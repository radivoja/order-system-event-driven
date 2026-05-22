package rs.mds.inventory.constants;

public final class MessagingConstants {
    private MessagingConstants() {}

    public static final String ORDER_EVENTS = "order.events";
    public static final String ORDER_CREATED_KEY = "order.created";
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String ORDER_DLX = "order.dlx";
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";
    public static final String PARKING_LOT_QUEUE = "order.parking-lot.queue";
    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final int MAX_RETRIES = 3;
}
