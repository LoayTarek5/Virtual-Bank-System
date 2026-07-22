package com.vbank.user.service;

import com.vbank.user.dto.*;
import com.vbank.user.exception.*;
import com.vbank.user.model.User;
import com.vbank.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByUsernameOrEmail(request.username(), request.email())) {
            throw new ConflictException("Username or email already exists.");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password())); // Hashing the password
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        User savedUser = userRepository.save(user);

        return new RegisterResponse(
                savedUser.getUserId(),
                savedUser.getUsername(),
                "User registered successfully."
        );
    }

    public LoginResponse authenticateUser(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password.");
        }

        return new LoginResponse(user.getUserId(), user.getUsername());
    }

    public ProfileResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with ID " + userId + " not found."));

        return new ProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}