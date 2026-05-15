package rs.mds.orderapi.event;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private String eventId = UUID.randomUUID().toString();
    private String orderId;
    private String itemId;
    private int quantity;
    private Instant timestamp = Instant.now();

    public OrderCreatedEvent(String orderId, String itemId, int quantity) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.quantity = quantity;
    }
}
