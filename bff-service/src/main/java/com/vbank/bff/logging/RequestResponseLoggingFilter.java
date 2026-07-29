package com.vbank.bff.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_BYTES = 8192;

    private final KafkaLogProducer producer;

    public RequestResponseLoggingFilter(KafkaLogProducer producer) {
        this.producer = producer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        String correlationId = UUID.randomUUID().toString();
        Instant receivedAt = Instant.now();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            try {
                sendLogs(cachedRequest, cachedResponse, correlationId, receivedAt);
            } finally {
                cachedResponse.copyBodyToResponse();
            }
        }
    }

    private void sendLogs(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response,
                          String correlationId, Instant receivedAt) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        producer.sendRequest(correlationId, method, uri, bodyOf(request.getContentAsByteArray()), receivedAt);
        producer.sendResponse(correlationId, method, uri, response.getStatus(),
                bodyOf(response.getContentAsByteArray()), Instant.now());
    }

    private static String bodyOf(byte[] content) {
        if (content.length == 0 || content.length >= MAX_BODY_BYTES) {
            return null;
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}
