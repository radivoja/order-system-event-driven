package rs.mds.inventory.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import rs.mds.inventory.constants.MessagingConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MessagingConstants.ORDER_CREATED_DLQ)
    public void handleDeadLetter(Message failedMessage) {
        int retryCount = getRetryCount(failedMessage);

        log.warn("DLQ received message (retry #{}) [body={}]", retryCount, new String(failedMessage.getBody()));

        if (retryCount < MessagingConstants.MAX_RETRIES) {
            retryCount++;
            failedMessage.getMessageProperties().getHeaders().put(MessagingConstants.HEADER_RETRY_COUNT, retryCount);

            log.info("Retry #{} — sending back to main queue", retryCount);

            rabbitTemplate.send(MessagingConstants.ORDER_EVENTS, MessagingConstants.ORDER_CREATED_KEY, failedMessage
            );
        } else {
            log.error("Exhausted all {} retries — sending to parking lot", MessagingConstants.MAX_RETRIES);

            rabbitTemplate.send(MessagingConstants.PARKING_LOT_QUEUE, failedMessage);
        }
    }

    private int getRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get(MessagingConstants.HEADER_RETRY_COUNT);

        if (header instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
