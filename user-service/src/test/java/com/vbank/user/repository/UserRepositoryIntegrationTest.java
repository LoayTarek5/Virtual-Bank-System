package com.vbank.user.repository;

import com.vbank.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindUser_Success() {
        // Arrange
        User user = new User();
        user.setUsername("integration.test");
        user.setPassword("secret");
        user.setEmail("integration@test.com");
        user.setFirstName("Integration");
        user.setLastName("Test");

        // Act - Save into the real H2 in-memory database
        User savedUser = userRepository.save(user);
        
        // Act - Retrieve from the real database
        Optional<User> foundUser = userRepository.findByUsername("integration.test");

        // Assert - The real database works and assigned an ID!
        assertTrue(foundUser.isPresent());
        assertNotNull(savedUser.getUserId());
        assertEquals("integration.test", foundUser.get().getUsername());
    }

    @Test
    void existsByUsernameOrEmail_ReturnsTrue_WhenUserExists() {
        // Arrange
        User user = new User();
        user.setUsername("exist.test");
        user.setPassword("secret");
        user.setEmail("exist@test.com");
        user.setFirstName("Exist");
        user.setLastName("Test");
        
        userRepository.save(user);

        // Act & Assert
        assertTrue(userRepository.existsByUsernameOrEmail("exist.test", "other@test.com"));
        assertTrue(userRepository.existsByUsernameOrEmail("other", "exist@test.com"));
        assertFalse(userRepository.existsByUsernameOrEmail("nope", "nope@test.com"));
    }
}
