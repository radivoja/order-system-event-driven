package rs.mds.inventory.model;

import java.util.concurrent.atomic.AtomicInteger;

public class InventoryItem {
    private final AtomicInteger availableQuantity;

    public InventoryItem(int initialQuantity) {
        this.availableQuantity = new AtomicInteger(initialQuantity);
    }

    public boolean tryReserve(int quantity) {
        while (true) {
            int current = availableQuantity.get();
            if (current < quantity) {
                return false;
            }
            if (availableQuantity.compareAndSet(current, current - quantity)) {
                return true;
            }
        }
    }

    public int getAvailableQuantity() {
        return availableQuantity.get();
    }
}