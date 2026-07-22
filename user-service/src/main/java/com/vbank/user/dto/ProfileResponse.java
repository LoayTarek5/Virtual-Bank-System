package com.vbank.user.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName
) {}