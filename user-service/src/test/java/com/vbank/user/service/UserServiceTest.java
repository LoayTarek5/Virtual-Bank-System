package com.vbank.user.service;

import com.vbank.user.dto.LoginRequest;
import com.vbank.user.dto.LoginResponse;
import com.vbank.user.dto.RegisterRequest;
import com.vbank.user.dto.RegisterResponse;
import com.vbank.user.exception.ApiException;
import com.vbank.user.model.User;
import com.vbank.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest("ahmed.moselhi", "password123", "ahmed@test.com", "Ahmed", "Moselhi");
        when(userRepository.existsByUsernameOrEmail("ahmed.moselhi", "ahmed@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        
        User savedUser = new User();
        savedUser.setUserId(UUID.randomUUID());
        savedUser.setUsername("ahmed.moselhi");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        RegisterResponse response = userService.registerUser(request);

        // Assert
        assertNotNull(response);
        assertEquals("ahmed.moselhi", response.username());
        assertEquals("User registered successfully.", response.message());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_ThrowsConflict_WhenUserExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest("ahmed.moselhi", "password123", "ahmed@test.com", "Ahmed", "Moselhi");
        when(userRepository.existsByUsernameOrEmail("ahmed.moselhi", "ahmed@test.com")).thenReturn(true);

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> userService.registerUser(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateUser_Success() {
        // Arrange
        LoginRequest request = new LoginRequest("ahmed.moselhi", "password123");
        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setUsername("ahmed.moselhi");
        mockUser.setPassword("hashed_password");
        
        when(userRepository.findByUsername("ahmed.moselhi")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);

        // Act
        LoginResponse response = userService.authenticateUser(request);

        // Assert
        assertNotNull(response);
        assertEquals("ahmed.moselhi", response.username());
    }

    @Test
    void authenticateUser_ThrowsUnauthorized_WhenPasswordWrong() {
        // Arrange
        LoginRequest request = new LoginRequest("ahmed.moselhi", "wrong_password");
        User mockUser = new User();
        mockUser.setUsername("ahmed.moselhi");
        mockUser.setPassword("hashed_password");
        
        when(userRepository.findByUsername("ahmed.moselhi")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> userService.authenticateUser(request));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
