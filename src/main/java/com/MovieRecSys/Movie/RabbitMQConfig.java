package com.MovieRecSys.Movie;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the interaction event pipeline.
 *
 * Exchange: interaction.events
 * Queue:    interaction.events.profile-projector  (main consumer)
 * DLQ:      interaction.events.profile-projector.dlq  (dead letter)
 *
 * Messages that fail processing after retries are routed to the DLQ
 * for manual inspection and replay.
 */
@Configuration
@ConditionalOnProperty(name = "app.messaging.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMQConfig {
    public static final String EXCHANGE = "interaction.events";
    public static final String QUEUE = "interaction.events.profile-projector";
    public static final String DLQ = "interaction.events.profile-projector.dlq";
    public static final String ROUTING_KEY = "interaction.event";
    public static final String DLQ_ROUTING_KEY = "interaction.event.dead";

    @Bean
    public DirectExchange interactionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange interactionDlxExchange() {
        return new DirectExchange(EXCHANGE + ".dlx", true, false);
    }

    @Bean
    public Queue interactionQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE + ".dlx")
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue interactionDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding interactionBinding() {
        return BindingBuilder.bind(interactionQueue())
                .to(interactionExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(interactionDlq())
                .to(interactionDlxExchange())
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
