package com.bankeurob.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String TRANSFER_QUEUE    = "transfer.processing";
    public static final String TRANSFER_EXCHANGE = "bankeurob.transfers";
    public static final String TRANSFER_ROUTING_KEY = "transfer.new";

    @Bean
    public Queue transferQueue() {
        return QueueBuilder.durable(TRANSFER_QUEUE).build();
    }

    @Bean
    public DirectExchange transferExchange() {
        return new DirectExchange(TRANSFER_EXCHANGE);
    }

    @Bean
    public Binding transferBinding(Queue transferQueue, DirectExchange transferExchange) {
        return BindingBuilder.bind(transferQueue).to(transferExchange).with(TRANSFER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
