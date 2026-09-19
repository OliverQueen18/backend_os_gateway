package com.osgateway.gatewaydevice.config;

import com.osgateway.common.messaging.QueueConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange osgatewayExchange() {
        return new DirectExchange(QueueConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue ussdQueue() {
        return QueueBuilder.durable(QueueConstants.USSD_QUEUE).build();
    }

    @Bean
    public Binding ussdBinding(Queue ussdQueue, DirectExchange osgatewayExchange) {
        return BindingBuilder.bind(ussdQueue).to(osgatewayExchange).with(QueueConstants.USSD_ROUTING_KEY);
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(QueueConstants.SMS_QUEUE)
                .maxPriority(QueueConstants.MAX_PRIORITY)
                .build();
    }

    @Bean
    public Binding smsBinding(Queue smsQueue, DirectExchange osgatewayExchange) {
        return BindingBuilder.bind(smsQueue).to(osgatewayExchange).with(QueueConstants.SMS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
