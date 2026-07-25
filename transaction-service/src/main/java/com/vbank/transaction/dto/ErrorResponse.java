package com.vbank.transaction.dto;

public record ErrorResponse(int status, String error, String message) {
}