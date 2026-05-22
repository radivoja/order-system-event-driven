package rs.mds.orderapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import rs.mds.orderapi.dto.CreateOrderRequest;
import rs.mds.orderapi.dto.CreateOrderResponse;
import rs.mds.orderapi.event.OrderCreatedEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    private OrderEventPublisher publisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        publisher = mock(OrderEventPublisher.class);
        orderService = new OrderService(publisher);
    }

    @Test
    @DisplayName("createOrder publishes event and returns ACCEPTED")
    void createOrder_publishesEventAndReturnsAccepted() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-1");
        request.setItemId("item-1");
        request.setQuantity(3);

        CreateOrderResponse response = orderService.createOrder(request);

        assertEquals("order-1", response.getOrderId());
        assertEquals("ACCEPTED", response.getStatus());
        assertNotNull(response.getTimestamp());
    }

    @Test
    @DisplayName("createOrder passes correct data to publisher")
    void createOrder_passesCorrectDataToPublisher() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-2");
        request.setItemId("item-5");
        request.setQuantity(10);

        orderService.createOrder(request);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher, times(1)).publish(captor.capture());

        OrderCreatedEvent event = captor.getValue();
        assertEquals("order-2", event.getOrderId());
        assertEquals("item-5", event.getItemId());
        assertEquals(10, event.getQuantity());
        assertNotNull(event.getEventId());
    }

    @Test
    @DisplayName("createOrder generates unique eventId")
    void createOrder_generatesUniqueEventId() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderId("order-3");
        request.setItemId("item-1");
        request.setQuantity(1);

        orderService.createOrder(request);
        orderService.createOrder(request);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher, times(2)).publish(captor.capture());

        String eventId1 = captor.getAllValues().get(0).getEventId();
        String eventId2 = captor.getAllValues().get(1).getEventId();
        assertNotEquals(eventId1, eventId2);
    }
}