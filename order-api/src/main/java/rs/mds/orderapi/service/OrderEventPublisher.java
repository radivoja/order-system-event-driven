package rs.mds.orderapi.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import rs.mds.orderapi.constants.MessagingConstants;
import rs.mds.orderapi.event.OrderCreatedEvent;

@Service
@RequiredArgsConstructor
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void publish(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent [eventId={}, orderId={}]",
                event.getEventId(), event.getOrderId());

        rabbitTemplate.convertAndSend(MessagingConstants.ORDER_EVENTS, MessagingConstants.ORDER_CREATED_KEY , event);
    }
}
