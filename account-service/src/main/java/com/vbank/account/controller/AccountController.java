package com.vbank.account.controller;

import com.vbank.account.dto.*;
import com.vbank.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return service.create(request);
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse getById(@PathVariable UUID accountId) {
        return service.getById(accountId);
    }

    @GetMapping("/users/{userId}/accounts")
    public List<AccountResponse> getByUserId(@PathVariable UUID userId) {
        return service.getByUserId(userId);
    }
}