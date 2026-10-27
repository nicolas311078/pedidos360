package com.pedidos360.users.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Declara la topologia de RabbitMQ del microservicio de usuarios:
 * exchanges direct/topic/dead-letter y las colas que consume
 * (notificaciones y auditoria), cada una con su DLQ.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
@RequiredArgsConstructor
public class MessagingTopologyConfig {

    private final MessagingProperties properties;

    @Bean
    public Declarables messagingTopology() {
        MessagingProperties.Exchanges exchanges = properties.getExchanges();

        DirectExchange direct = new DirectExchange(exchanges.getDirect(), true, false);
        TopicExchange topic = new TopicExchange(exchanges.getTopic(), true, false);
        DirectExchange deadLetter = new DirectExchange(exchanges.getDeadLetter(), true, false);

        List<Declarable> declarables = new ArrayList<>();
        declarables.add(direct);
        declarables.add(topic);
        declarables.add(deadLetter);

        for (MessagingProperties.QueueDefinition definition : properties.getQueues().values()) {
            Queue queue = new Queue(
                    definition.getName(),
                    true,
                    false,
                    false,
                    queueArguments(definition.getDeadLetterQueue()));

            Queue deadLetterQueue = new Queue(
                    definition.getDeadLetterQueue(),
                    true,
                    false,
                    false,
                    Map.of("x-queue-type", properties.getQueueType()));

            Exchange exchange = "topic".equalsIgnoreCase(definition.getExchange()) ? topic : direct;

            Binding binding = new Binding(queue.getName(), Binding.DestinationType.QUEUE,
                    exchange.getName(), definition.getRoutingKey(), Map.of());
            Binding deadLetterBinding = new Binding(deadLetterQueue.getName(), Binding.DestinationType.QUEUE,
                    deadLetter.getName(), definition.getDeadLetterQueue(), Map.of());

            declarables.add(queue);
            declarables.add(deadLetterQueue);
            declarables.add(binding);
            declarables.add(deadLetterBinding);
        }

        return new Declarables(declarables);
    }

    private Map<String, Object> queueArguments(String deadLetterQueue) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-queue-type", properties.getQueueType());
        args.put("x-dead-letter-exchange", properties.getExchanges().getDeadLetter());
        args.put("x-dead-letter-routing-key", deadLetterQueue);
        return args;
    }
}
