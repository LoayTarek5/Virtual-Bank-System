package com.vbank.transaction.client;

import com.vbank.transaction.exception.ApiException;
import com.vbank.transaction.dto.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(RestClient.Builder builder, @Value("${services.account.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Optional<AccountResponse> findById(UUID accountId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/accounts/{accountId}", accountId)
                    .retrieve()
                    .body(AccountResponse.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service is unavailable.");
        }
    }

    public Optional<String> transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        try {
            restClient.put()
                    .uri("/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TransferRequest(fromAccountId, toAccountId, amount))
                    .retrieve()
                    .toBodilessEntity();
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            ErrorResponse body = ex.getResponseBodyAs(ErrorResponse.class);
            return Optional.of(body != null ? body.message() : "Transfer rejected by account service.");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Account service is unavailable.");
        }
    }
}