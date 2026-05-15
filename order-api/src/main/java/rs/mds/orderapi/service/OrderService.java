package rs.mds.orderapi.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rs.mds.orderapi.constants.MessagingConstants;
import rs.mds.orderapi.dto.CreateOrderRequest;
import rs.mds.orderapi.dto.CreateOrderResponse;
import rs.mds.orderapi.event.OrderCreatedEvent;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderEventPublisher publisher;

    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        log.info("Processing order [orderId={}, itemId={}, qty={}]",
                request.getOrderId(), request.getItemId(), request.getQuantity());


        OrderCreatedEvent event = new OrderCreatedEvent(
                request.getOrderId(),
                request.getItemId(),
                request.getQuantity()
        );

        publisher.publish(event);

        return new CreateOrderResponse(request.getOrderId(), MessagingConstants.ACCEPTED, Instant.now());
    }
}
