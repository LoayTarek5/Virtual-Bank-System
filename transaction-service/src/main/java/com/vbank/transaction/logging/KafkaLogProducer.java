package com.vbank.transaction.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
public class KafkaLogProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogProducer.class);
    private static final String TOPIC = "logging";
    private static final String REQUEST = "Request";
    private static final String RESPONSE = "Response";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public KafkaLogProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                            @Value("${spring.application.name}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    public void sendRequest(String correlationId, String method, String uri, String body, Instant dateTime) {
        send(REQUEST, correlationId, new LogPayload(serviceName, correlationId, method, uri, null, body), dateTime);
    }

    public void sendResponse(String correlationId, String method, String uri, int status, String body, Instant dateTime) {
        send(RESPONSE, correlationId, new LogPayload(serviceName, correlationId, method, uri, status, body), dateTime);
    }

    private void send(String messageType, String correlationId, LogPayload payload, Instant dateTime) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            String value = objectMapper.writeValueAsString(new LogMessage(message, messageType, dateTime));
            kafkaTemplate.send(TOPIC, correlationId, value)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka delivery failed for {} log: {}", messageType, ex.toString());
                        }
                    });
        } catch (Exception e) {
            log.warn("Kafka send failed for {} log: {}", messageType, e.toString());
        }
    }
}