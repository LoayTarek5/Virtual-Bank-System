package com.vbank.logging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vbank.logging.dto.LogMessage;
import com.vbank.logging.model.LogEntry;
import com.vbank.logging.repository.LogRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class LogConsumer {

    private final LogRepository logRepository;
    private final ObjectMapper objectMapper;

    public LogConsumer(LogRepository logRepository, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "logging", groupId = "logging-service-group")
    public void consumeLogMessage(String messagePayload) {
        try {
            LogMessage logMessage = objectMapper.readValue(messagePayload, LogMessage.class);

            LogEntry logEntry = new LogEntry();
            logEntry.setMessage(logMessage.message());
            logEntry.setMessageType(logMessage.messageType());
            logEntry.setDateTime(logMessage.dateTime());

            logRepository.save(logEntry);
        } catch (Exception e) {
            System.err.println("Failed to process log message: " + messagePayload);
            e.printStackTrace();
        }
    }
}
