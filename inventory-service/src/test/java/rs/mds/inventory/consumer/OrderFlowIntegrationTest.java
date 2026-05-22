package rs.mds.inventory.consumer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import rs.mds.inventory.constants.MessagingConstants;
import rs.mds.inventory.event.OrderCreatedEvent;
import rs.mds.inventory.service.InventoryService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest
@Testcontainers
public class OrderFlowIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMQ =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        log.info("========================================");
        log.info("TEST: {}", testInfo.getDisplayName());
        log.info("========================================");
    }

    private OrderCreatedEvent createEvent(String orderId, String itemId, int quantity) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setOrderId(orderId);
        event.setItemId(itemId);
        event.setQuantity(quantity);
        event.setTimestamp(Instant.now());
        return event;
    }

    @Test
    @DisplayName("Order message reduces inventory stock via RabbitMQ")
    void orderReducesStock() {
        int before = inventoryService.getInventorySnapshot().get("item-1");

        log.info("Stock before: item-1={}", before);

        OrderCreatedEvent event = createEvent("flow-1", "item-1", 7);
        rabbitTemplate.convertAndSend(
                MessagingConstants.ORDER_EVENTS,
                MessagingConstants.ORDER_CREATED_KEY,
                event
        );

        log.info("Sent order for 7 units of item-1 — waiting for processing");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            int after = inventoryService.getInventorySnapshot().get("item-1");
            assertEquals(before - 7, after);
        });

        log.info("Stock after: item-1={}", inventoryService.getInventorySnapshot().get("item-1"));
    }

    @Test
    @DisplayName("Rejected order does not change stock via RabbitMQ")
    void rejectedOrderDoesNotChangeStock() {
        int before = inventoryService.getInventorySnapshot().get("item-3");

        log.info("Stock before: item-3={} — sending order for 999 units", before);

        OrderCreatedEvent event = createEvent("flow-2", "item-3", 999);
        rabbitTemplate.convertAndSend(
                MessagingConstants.ORDER_EVENTS,
                MessagingConstants.ORDER_CREATED_KEY,
                event
        );

        await().during(Duration.ofSeconds(3)).untilAsserted(() -> {
            int after = inventoryService.getInventorySnapshot().get("item-3");
            assertEquals(before, after, "Stock should not change for rejected order");
        });

        log.info("Stock unchanged: item-3={}", inventoryService.getInventorySnapshot().get("item-3"));
    }
}