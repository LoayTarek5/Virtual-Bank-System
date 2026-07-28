package com.vbank.user.logging;

import java.time.Instant;

public record LogMessage(String message, String messageType, Instant dateTime) {
}
