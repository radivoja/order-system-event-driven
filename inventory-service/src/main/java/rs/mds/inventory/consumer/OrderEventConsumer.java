package rs.mds.inventory.consumer;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import rs.mds.inventory.constants.MessagingConstants;
import rs.mds.inventory.event.OrderCreatedEvent;
import rs.mds.inventory.service.InventoryService;

@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final InventoryService inventoryService;

    @RabbitListener(queues = MessagingConstants.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent [eventId={}, orderId={}]", event.getEventId(), event.getOrderId());

        String result = inventoryService.processOrder(event);

        if (result != null) {
            log.info("Order processed [orderId={}, result={}]", event.getOrderId(), result);
        }
    }
}