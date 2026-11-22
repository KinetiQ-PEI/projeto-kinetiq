package pt.ua.kinetiq.fleet.lifecycle.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia a mudança de estado para o Kafka só depois de a transação da base de dados ser confirmada.
 * Assim nunca se anuncia uma mudança que acabou por falhar (rollback).
 *
 * <p>Limitação conhecida: se o Kafka estiver em baixo nesse momento, o estado fica gravado mas o
 * evento não sai. Para garantir entrega, o passo seguinte seria o padrão "transactional outbox".
 */
@Component
public class BikeStateEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BikeStateEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public BikeStateEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                   @Value("${kinetiq.topics.fleet-status}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStateChanged(BikeStateChangedEvent event) {
        // Chave = bikeId: as mudanças da mesma bicicleta vão para a mesma partição e chegam por ordem.
        kafkaTemplate.send(topic, event.bikeId(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Could not publish state change {} for bike {}", event.eventId(), event.bikeId(), error);
            }
        });
    }
}
