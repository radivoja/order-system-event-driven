package rs.mds.inventory.consumer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import rs.mds.inventory.constants.MessagingConstants;
import rs.mds.inventory.event.OrderCreatedEvent;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Slf4j
@Testcontainers
class DlqConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void purgeQueues(TestInfo testInfo) {
        rabbitAdmin.purgeQueue(MessagingConstants.PARKING_LOT_QUEUE);
        rabbitAdmin.purgeQueue(MessagingConstants.ORDER_CREATED_DLQ);
        rabbitAdmin.purgeQueue(MessagingConstants.ORDER_CREATED_QUEUE);

        log.info("========================================");
        log.info("TEST: {}", testInfo.getDisplayName());
        log.info("========================================");
    }

    @Test
    @DisplayName("Message goes through 3 retries then lands in parking lot")
    void messageIsRetriedAndEventuallyParked() {
        Message poison = MessageBuilder
                .withBody("{\"invalid\": true}".getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();

        log.info("Sending poison message to DLQ — expecting 3 retries then parking lot");
        rabbitTemplate.send(MessagingConstants.ORDER_CREATED_DLQ, poison);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            QueueInformation info = rabbitAdmin.getQueueInfo(MessagingConstants.PARKING_LOT_QUEUE);
            assertNotNull(info);
            assertEquals(1, info.getMessageCount());
        });

        log.info("Parking lot has 1 message — retries exhausted as expected");
    }

    @Test
    @DisplayName("Message with retry count already at max goes straight to parking lot")
    void messageWithExhaustedRetriesGoesDirectlyToParkingLot() {
        Message exhausted = MessageBuilder
                .withBody("{\"already\": \"exhausted\"}".getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(MessagingConstants.HEADER_RETRY_COUNT, 3)
                .build();

        log.info("Sending message with retry-count=3 to DLQ — expecting immediate parking lot");
        rabbitTemplate.send(MessagingConstants.ORDER_CREATED_DLQ, exhausted);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            QueueInformation info = rabbitAdmin.getQueueInfo(MessagingConstants.PARKING_LOT_QUEUE);
            assertNotNull(info);
            assertEquals(1, info.getMessageCount());
        });

        log.info("Message went straight to parking lot — no unnecessary retries");
    }

    @Test
    @DisplayName("Business rejection (unknown item) does not reach DLQ")
    void businessRejectionDoesNotReachDlq() {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId("evt-no-dlq");
        event.setOrderId("ord-no-dlq");
        event.setItemId("item-999");
        event.setQuantity(1);

        log.info("Sending order for non-existent item-999 — expecting REJECTED, not DLQ");
        rabbitTemplate.convertAndSend(
                MessagingConstants.ORDER_EVENTS,
                MessagingConstants.ORDER_CREATED_KEY,
                event
        );

        await().during(Duration.ofSeconds(5)).untilAsserted(() -> {
            QueueInformation info = rabbitAdmin.getQueueInfo(MessagingConstants.ORDER_CREATED_DLQ);
            int count = (info != null) ? info.getMessageCount() : 0;
            assertEquals(0, count, "Business rejection should not reach DLQ");
        });

        log.info("DLQ is empty — business logic handled rejection without error");
    }
}