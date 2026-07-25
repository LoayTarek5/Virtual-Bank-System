package com.vbank.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpMethod;

@RestController
@RequestMapping("/bff")
public class BffController {

    private final RestClient restClient;
    private final String userServiceUrl;
    private final String accountServiceUrl;
    private final String transactionServiceUrl;

    public BffController(RestClient restClient,
                         @Value("${vbank.services.user}") String userServiceUrl,
                         @Value("${vbank.services.account}") String accountServiceUrl,
                         @Value("${vbank.services.transaction}") String transactionServiceUrl) {
        this.restClient = restClient;
        this.userServiceUrl = userServiceUrl;
        this.accountServiceUrl = accountServiceUrl;
        this.transactionServiceUrl = transactionServiceUrl;
    }

    // User Profile
    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<byte[]> getUserProfile(@PathVariable String userId,
                                                 @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(userServiceUrl + "/users/" + userId + "/profile", HttpMethod.GET, null, appName);
    }

    // User Accounts
    @GetMapping("/users/{userId}/accounts")
    public ResponseEntity<byte[]> getUserAccounts(@PathVariable String userId,
                                                  @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(accountServiceUrl + "/users/" + userId + "/accounts", HttpMethod.GET, null, appName);
    }

    // Get Account
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<byte[]> getAccount(@PathVariable String accountId,
                                             @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(accountServiceUrl + "/accounts/" + accountId, HttpMethod.GET, null, appName);
    }

    // Create Account
    @PostMapping("/accounts")
    public ResponseEntity<byte[]> createAccount(@RequestBody byte[] body,
                                                @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(accountServiceUrl + "/accounts", HttpMethod.POST, body, appName);
    }

    // Transfer
    @PutMapping("/accounts/transfer")
    public ResponseEntity<byte[]> transfer(@RequestBody byte[] body,
                                           @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(accountServiceUrl + "/accounts/transfer", HttpMethod.PUT, body, appName);
    }

    // Transactions History
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<byte[]> getTransactions(@PathVariable String accountId,
                                                  @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(transactionServiceUrl + "/accounts/" + accountId + "/transactions", HttpMethod.GET, null, appName);
    }

    // Transfer Initiation
    @PostMapping("/transactions/transfer/initiation")
    public ResponseEntity<byte[]> transferInitiation(@RequestBody byte[] body,
                                                     @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(transactionServiceUrl + "/transactions/transfer/initiation", HttpMethod.POST, body, appName);
    }

    // Transfer Execution
    @PostMapping("/transactions/transfer/execution")
    public ResponseEntity<byte[]> transferExecution(@RequestBody byte[] body,
                                                    @RequestHeader(value = "APP-NAME", required = false) String appName) {
        return forward(transactionServiceUrl + "/transactions/transfer/execution", HttpMethod.POST, body, appName);
    }

    private ResponseEntity<byte[]> forward(String url, HttpMethod method, byte[] body, String appName) {
        var request = restClient.method(method)
                .uri(url)
                .headers(headers -> {
                    if (appName != null) {
                        headers.set("APP-NAME", appName);
                    }
                    headers.set("Content-Type", "application/json");
                });

        if (body != null) {
            request.body(body);
        }

        return request.exchange((clientRequest, clientResponse) -> 
            ResponseEntity.status(clientResponse.getStatusCode())
                    .headers(clientResponse.getHeaders())
                    .body(clientResponse.getBody().readAllBytes())
        );
    }
}
