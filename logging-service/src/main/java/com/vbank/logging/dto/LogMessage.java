package com.vbank.logging.dto;

import java.time.Instant;

public record LogMessage(
    String message,
    String messageType,
    Instant dateTime
) {
}
