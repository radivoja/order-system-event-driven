package rs.mds.orderapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static rs.mds.orderapi.constants.MessagingConstants.ORDER_EVENTS;

@Slf4j
@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EVENTS, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(ORDER_EVENTS);

        template.setObservationEnabled(true);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Broker confirmed message [{}]", correlationData);
            } else {
                log.error("Broker NACK — message NOT confirmed [cause={}]", cause);
            }
        });

        template.setReturnsCallback(returned -> {
            log.error("Message returned — no matching queue [exchange={}, routingKey={}, replyCode={}, replyText={}]",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText());
        });

        return template;
    }
}
