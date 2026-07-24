package com.vbank.transaction.model;

public enum TransactionStatus {
    INITIATED, SUCCESS, FAILED;

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}