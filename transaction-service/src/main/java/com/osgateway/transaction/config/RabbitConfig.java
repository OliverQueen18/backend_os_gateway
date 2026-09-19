package com.osgateway.transaction.config;

import com.osgateway.common.messaging.QueueConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public MessageConverter jacksonMessageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    public DirectExchange exchange() { return new DirectExchange(QueueConstants.EXCHANGE, true, false); }

    @Bean
    public Queue transactionQueue() {
        return QueueBuilder.durable(QueueConstants.TRANSACTION_QUEUE)
                .maxPriority(QueueConstants.MAX_PRIORITY).build();
    }

    @Bean
    public Binding transactionBinding(Queue transactionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(transactionQueue).to(exchange).with(QueueConstants.TRANSACTION_ROUTING_KEY);
    }
}