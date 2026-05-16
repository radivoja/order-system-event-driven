package rs.mds.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rs.mds.inventory.event.OrderCreatedEvent;
import rs.mds.inventory.model.InventoryItem;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final Map<String, InventoryItem> inventory = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public InventoryService() {
        inventory.put("item-1", new InventoryItem(100));
        inventory.put("item-2", new InventoryItem(50));
        inventory.put("item-3", new InventoryItem(5));
        log.info("Inventory initialized: item-1=100, item-2=50, item-3=5");
    }

    public String processOrder(OrderCreatedEvent event) {
        if (!processedEventIds.add(event.getEventId())) {
            log.warn("Duplicate event, skipping [eventId={}]", event.getEventId());
            return null;
        }

        String itemId = event.getItemId();
        int quantity = event.getQuantity();

        InventoryItem item = inventory.get(itemId);
        if (item == null) {
            log.warn("Unknown item [itemId={}, orderId={}]", itemId, event.getOrderId());
            return "REJECTED: Item not found";
        }

        if (item.tryReserve(quantity)) {
            log.info("Reserved [orderId={}, itemId={}, qty={}, remaining={}]",
                    event.getOrderId(), itemId, quantity, item.getAvailableQuantity());
            return "RESERVED";
        } else {
            log.warn("Insufficient stock [orderId={}, itemId={}, requested={}, available={}]",
                    event.getOrderId(), itemId, quantity, item.getAvailableQuantity());
            return "REJECTED: Insufficient stock";
        }
    }

    public Map<String, Integer> getInventorySnapshot() {
        Map<String, Integer> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, InventoryItem> entry : inventory.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getAvailableQuantity());
        }
        return snapshot;
    }
}