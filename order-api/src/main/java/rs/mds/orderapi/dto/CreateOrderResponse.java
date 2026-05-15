package rs.mds.orderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CreateOrderResponse {
    private String orderId;
    private String status;
    private Instant timestamp;
}
