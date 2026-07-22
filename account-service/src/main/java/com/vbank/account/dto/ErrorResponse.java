package com.vbank.account.dto;

public record ErrorResponse(int status, String error, String message) {
}