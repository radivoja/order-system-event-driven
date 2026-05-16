package rs.mds.inventory.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class OrderCreatedEvent {
    private String eventId;
    private String orderId;
    private String itemId;
    private int quantity;
    private Instant timestamp;
}