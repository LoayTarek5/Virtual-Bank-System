package com.vbank.transaction.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogPayload(
        String service,
        String correlationId,
        String method,
        String uri,
        Integer status,
        @JsonRawValue String body
) {
}