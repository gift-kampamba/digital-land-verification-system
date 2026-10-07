package com.landverification.controller;

import com.landverification.dto.ApiResponse;
import com.landverification.dto.AuthResponse;
import com.landverification.dto.LoginRequest;
import com.landverification.dto.RegisterOfficerRequest;
import com.landverification.dto.RegisterRequest;
import com.landverification.dto.UpdateProfileRequest;
import com.landverification.model.PendingProfileChange;
import com.landverification.model.User;
import com.landverification.service.AuthService;
import com.landverification.config.AppProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppProperties appProperties;

    // ─── Login ───────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(req)));
    }

    // ─── Register ─────────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", authService.register(req)));
    }

    // ─── Register Officer with Invitation Code ────────────────────────────────
    @PostMapping("/register-officer")
    public ResponseEntity<ApiResponse<AuthResponse>> registerOfficer(
            @Valid @RequestBody RegisterOfficerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Officer registration successful", authService.registerOfficerWithCode(req)));
    }

    // ─── Get Current User Profile (safe — no passwordHash) ───────────────────
    @GetMapping("/profile/me")
    public ResponseEntity<ApiResponse<?>> getCurrentUserProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        User user = authService.getUserProfile(email);

        // Build a safe response map — never expose passwordHash
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId",          user.getUserId());
        profile.put("fullName",        user.getFullName());
        profile.put("email",           user.getEmail());
        profile.put("phoneNumber",     user.getPhoneNumber());
        profile.put("gender",          user.getGender() != null ? user.getGender().name() : null);
        profile.put("address",         user.getAddress());
        profile.put("role",            user.getRole().name());
        profile.put("photoPath",       existingPhotoPath(user.getPhotoPath()));
        profile.put("username",        user.getUsername());
        profile.put("district",        user.getDistrict());
        profile.put("profileComplete", user.getProfileComplete());
        profile.put("isActive",        user.getIsActive());
        profile.put("nationalId",      user.getNationalId());
        profile.put("createdAt",       user.getCreatedAt());
        profile.put("memberSince",     user.getCreatedAt());
        profile.put("lastLogin",       user.getLastLoginAt());
        profile.put("passwordChangedAt", user.getPasswordChangedAt());
        profile.put("passwordLastChanged", user.getPasswordChangedAt());

        return ResponseEntity.ok(ApiResponse.ok("Current user profile", profile));
    }

    private String existingPhotoPath(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) return null;
        try {
            Path path = Path.of(appProperties.getUploadDir()).resolve(photoPath).normalize();
            return path.startsWith(Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize())
                    && Files.isRegularFile(path) ? photoPath : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // ─── Request Profile Update ───────────────────────────────────────────────
    @PostMapping("/profile/request-update")
    public ResponseEntity<?> requestProfileUpdate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword) throws IOException {

        String email = userDetails.getUsername();

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(fullName);
        request.setPhoneNumber(phoneNumber);
        request.setAddress(address);
        request.setGender(gender);
        request.setPhoto(photo);
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);

        PendingProfileChange change = authService.requestProfileUpdate(email, request);
        return ResponseEntity.ok(ApiResponse.ok("Profile update request submitted", change));
    }

    // ─── Get My Pending Changes ───────────────────────────────────────────────
    @GetMapping("/profile/my-pending-changes")
    public ResponseEntity<?> getMyPendingChanges(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        List<PendingProfileChange> changes = authService.getPendingChangesByUser(email);
        return ResponseEntity.ok(ApiResponse.ok("Pending profile changes", changes));
    }

    // ─── Forgot password (request reset link) ──────────────────────────────
    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        authService.requestPasswordReset(email);
        return ResponseEntity.ok(ApiResponse.ok("If the email is registered, a reset link has been sent", null));
    }

    // ─── Reset password (complete) ────────────────────────────────────────
    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.getOrDefault("token", "").trim();
        String newPassword = body.getOrDefault("newPassword", "").trim();
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully", null));
    }
}