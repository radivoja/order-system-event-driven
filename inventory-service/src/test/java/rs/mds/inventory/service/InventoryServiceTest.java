package rs.mds.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rs.mds.inventory.event.OrderCreatedEvent;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryServiceTest {

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService();
    }

    private OrderCreatedEvent createEvent(String orderId, String itemId, int quantity) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setOrderId(orderId);
        event.setItemId(itemId);
        event.setQuantity(quantity);
        return event;
    }

    @Test
    @DisplayName("Successful reservation returns RESERVED")
    void successfulReservation() {
        OrderCreatedEvent event = createEvent("o-1", "item-1", 5);
        assertEquals("RESERVED", service.processOrder(event));
    }

    @Test
    @DisplayName("Successful reservation decreases stock correctly")
    void reservationDecreasesStock() {
        OrderCreatedEvent event = createEvent("o-2", "item-1", 3);
        service.processOrder(event);

        Map<String, Integer> snapshot = service.getInventorySnapshot();
        assertEquals(97, snapshot.get("item-1"));
    }

    @Test
    @DisplayName("Multiple reservations decrease stock cumulatively")
    void multipleReservationsDecreaseCumulatively() {
        service.processOrder(createEvent("o-3", "item-2", 10));
        service.processOrder(createEvent("o-4", "item-2", 15));

        Map<String, Integer> snapshot = service.getInventorySnapshot();
        assertEquals(25, snapshot.get("item-2"));  // 50 - 10 - 15
    }

    @Test
    @DisplayName("Unknown item returns REJECTED: Item not found")
    void unknownItemRejected() {
        OrderCreatedEvent event = createEvent("o-5", "item-999", 1);
        assertEquals("REJECTED: Item not found", service.processOrder(event));
    }

    @Test
    @DisplayName("Insufficient stock returns REJECTED: Insufficient stock")
    void insufficientStockRejected() {
        OrderCreatedEvent event = createEvent("o-6", "item-3", 99);
        assertEquals("REJECTED: Insufficient stock", service.processOrder(event));
    }

    @Test
    @DisplayName("Insufficient stock does not change inventory")
    void insufficientStockDoesNotChangeInventory() {
        OrderCreatedEvent event = createEvent("o-7", "item-3", 99);
        service.processOrder(event);

        Map<String, Integer> snapshot = service.getInventorySnapshot();
        assertEquals(5, snapshot.get("item-3"));  // unchanged
    }

    @Test
    @DisplayName("Duplicate eventId is skipped and returns null")
    void duplicateEventSkipped() {
        OrderCreatedEvent event = createEvent("o-8", "item-1", 2);
        assertEquals("RESERVED", service.processOrder(event));
        assertNull(service.processOrder(event));
    }

    @Test
    @DisplayName("Duplicate event does not decrease stock twice")
    void duplicateDoesNotDecreaseTwice() {
        OrderCreatedEvent event = createEvent("o-9", "item-1", 10);
        service.processOrder(event);
        service.processOrder(event);

        Map<String, Integer> snapshot = service.getInventorySnapshot();
        assertEquals(90, snapshot.get("item-1"));  // 100 - 10, not 100 - 20
    }

    @Test
    @DisplayName("Exact stock can be reserved")
    void exactStockCanBeReserved() {
        OrderCreatedEvent event = createEvent("o-10", "item-3", 5);
        assertEquals("RESERVED", service.processOrder(event));

        Map<String, Integer> snapshot = service.getInventorySnapshot();
        assertEquals(0, snapshot.get("item-3"));
    }

    @Test
    @DisplayName("Zero stock after full reservation rejects next order")
    void zeroStockRejectsNextOrder() {
        service.processOrder(createEvent("o-11", "item-3", 5));  // drain stock
        OrderCreatedEvent next = createEvent("o-12", "item-3", 1);
        assertEquals("REJECTED: Insufficient stock", service.processOrder(next));
    }
}