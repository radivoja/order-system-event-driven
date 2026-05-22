import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import rs.mds.orderapi.OrderApiApplication;
import rs.mds.orderapi.dto.CreateOrderRequest;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@AutoConfigureMockMvc
@Testcontainers
@SpringBootTest(classes = OrderApiApplication.class)
public class OrderApiIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        rabbitAdmin.declareQueue(new Queue("order.created.queue", true));
        rabbitAdmin.declareBinding(
                BindingBuilder.bind(new Queue("order.created.queue", true))
                        .to(new DirectExchange("order.events"))
                        .with("order.created")
        );

        log.info("========================================");
        log.info("TEST: {}", testInfo.getDisplayName());
        log.info("========================================");
    }

    @Test
    @DisplayName("Valid order returns 202 ACCEPTED with orderId and status")
    void validOrder_returns202() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-100");
        request.setItemId("item-1");
        request.setQuantity(2);

        log.info("Sending valid order [orderId=order-100]");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value("order-100"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        log.info("Received 202 ACCEPTED as expected");
    }

    @Test
    @DisplayName("Missing orderId returns 400 with validation error")
    void missingOrderId_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItemId("item-1");
        request.setQuantity(2);

        log.info("Sending order with missing orderId — expecting 400");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Received 400 Bad Request as expected");
    }

    @Test
    @DisplayName("Zero quantity returns 400")
    void zeroQuantity_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-101");
        request.setItemId("item-1");
        request.setQuantity(0);

        log.info("Sending order with quantity=0 — expecting 400");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Received 400 Bad Request as expected");
    }

    @Test
    @DisplayName("Valid order actually publishes message to RabbitMQ")
    void validOrder_publishesMessageToRabbitMQ() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-mq-check");
        request.setItemId("item-1");
        request.setQuantity(1);

        log.info("Sending order and verifying message arrives in RabbitMQ");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Провери да је порука заиста стигла у queue
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Object message = rabbitTemplate.receiveAndConvert("order.created.queue", 1000);
            assertNotNull(message, "Message should be present in queue");
            log.info("Message found in queue: {}", message);
        });

        log.info("Message successfully published to RabbitMQ");
    }

    @Test
    @DisplayName("Empty body returns 400")
    void emptyBody_returns400() throws Exception {
        log.info("Sending empty body — expecting 400");

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        log.info("Received 400 Bad Request as expected");
    }
}