package com.vbank.transaction.controller;

import com.vbank.transaction.dto.ExecutionRequest;
import com.vbank.transaction.dto.InitiationRequest;
import com.vbank.transaction.dto.TransactionHistoryResponse;
import com.vbank.transaction.dto.TransactionResponse;
import com.vbank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/transactions/transfer/initiation")
    public TransactionResponse initiate(@Valid @RequestBody InitiationRequest request) {
        return service.initiate(request);
    }

    @PostMapping("/transactions/transfer/execution")
    public TransactionResponse execute(@Valid @RequestBody ExecutionRequest request) {
        return service.execute(request);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<TransactionHistoryResponse> getHistory(@PathVariable UUID accountId) {
        return service.getHistory(accountId);
    }
}