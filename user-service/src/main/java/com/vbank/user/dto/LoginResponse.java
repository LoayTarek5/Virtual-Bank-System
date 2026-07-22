package com.vbank.user.dto;

import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String username
) {}