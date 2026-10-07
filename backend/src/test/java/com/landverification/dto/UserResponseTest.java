package com.landverification.dto;

import com.landverification.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserResponseTest {

    @Test
    void fromEntityIncludesAccountMetadata() {
        User user = User.builder()
                .fullName("Jane Mwale")
                .email("jane@example.com")
                .passwordHash("hash")
                .role(User.Role.LAND_OFFICER)
                .createdAt(LocalDateTime.of(2024, 1, 10, 8, 0))
                .lastLoginAt(LocalDateTime.of(2024, 2, 5, 13, 45))
                .passwordChangedAt(LocalDateTime.of(2024, 2, 1, 10, 30))
                .build();

        UserResponse response = UserResponse.fromEntity(user);

        assertEquals(user.getCreatedAt(), response.getCreatedAt());
        assertEquals(user.getLastLoginAt(), response.getLastLogin());
        assertEquals(user.getPasswordChangedAt(), response.getPasswordChangedAt());
    }
}
