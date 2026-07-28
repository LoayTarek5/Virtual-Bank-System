package com.vbank.bff.logging;

import java.time.Instant;

public record LogMessage(String message, String messageType, Instant dateTime) {
}
