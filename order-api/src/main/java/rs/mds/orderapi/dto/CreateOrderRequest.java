package rs.mds.orderapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "itemId is required")
    private String itemId;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;
}
