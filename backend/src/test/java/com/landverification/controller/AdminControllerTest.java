package com.landverification.controller;

import com.landverification.model.User;
import com.landverification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private com.landverification.service.AuthService authService;

    @MockitoBean
    private com.landverification.service.EmailService emailService;

    @MockitoBean
    private com.landverification.repository.PendingProfileChangeRepository pendingProfileChangeRepository;

    @MockitoBean
    private com.landverification.security.JwtFilter jwtFilter;

    private User sampleUser;

    @BeforeEach
    void setup() {
        sampleUser = User.builder()
                .userId(100)
                .fullName("Test User")
                .email("testuser@example.com")
                .username("testuser")
                .district("Lusaka")
                .address("123 Main Street")
                .phoneNumber("+260123456789")
                .nationalId("NID123456")
                .role(User.Role.LAND_OFFICER)
                .isActive(true)
                .profileComplete(true)
                .invitationCode("INV123")
                .createdAt(java.time.LocalDateTime.of(2026, 7, 3, 10, 0))
                .updatedAt(java.time.LocalDateTime.of(2026, 7, 3, 11, 0))
                .build();
    }

    @Test
    void shouldReturnFullUserDetails() throws Exception {
        when(userRepository.findById(anyInt())).thenReturn(Optional.of(sampleUser));

        mockMvc.perform(get("/api/admin/users/100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.userId").value(100))
                .andExpect(jsonPath("$.data.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.passwordHash").isEmpty());
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(userRepository.findById(anyInt())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/users/999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
