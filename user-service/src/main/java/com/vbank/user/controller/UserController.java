package com.vbank.user.controller;

import com.vbank.user.dto.*;
import com.vbank.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.registerUser(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.authenticateUser(request);
    }

    @GetMapping("/{userId}/profile")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        return userService.getUserProfile(userId);
    }
}