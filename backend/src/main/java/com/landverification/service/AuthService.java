package com.landverification.service;

import com.landverification.dto.AuthResponse;
import com.landverification.dto.CompleteProfileRequest;
import com.landverification.dto.CreateOfficerRequest;
import com.landverification.dto.LoginRequest;
import com.landverification.dto.RegisterOfficerRequest;
import com.landverification.dto.RegisterRequest;
import com.landverification.dto.UpdateProfileRequest;
import com.landverification.dto.UpdateProfileDirectRequest;
import com.landverification.dto.ChangePasswordRequest;
import com.landverification.model.Notification;
import com.landverification.model.PendingProfileChange;
import com.landverification.model.User;
import com.landverification.repository.NotificationRepository;
import com.landverification.repository.PendingProfileChangeRepository;
import com.landverification.repository.UserRepository;
import com.landverification.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.UUID;
import com.landverification.model.PasswordResetToken;
import com.landverification.repository.PasswordResetTokenRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final FileService fileService;
    private final PendingProfileChangeRepository pendingProfileChangeRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // ─── Login ───────────────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail() == null ? null : req.getEmail().trim().toLowerCase();
        req.setEmail(email);
        // ──── Authenticate with credentials ────
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, req.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new RuntimeException("Account is deactivated. Contact the administrator.");
        }

        // ──── If a role is provided, validate it matches the user's assigned role.
        // Otherwise, let the backend resolve the role automatically from the account.
        String userRole = user.getRole().name();
        if (req.getRole() != null && !req.getRole().isBlank()) {
            String selectedRole = req.getRole().trim().toUpperCase();
            if (!userRole.equals(selectedRole)) {
                log.warn("Role mismatch for user {}: selected role '{}' does not match assigned role '{}'",
                        user.getEmail(), selectedRole, userRole);
                throw new RuntimeException("Invalid role selected for this account. Your account is assigned as: " + userRole);
            }
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getUserId(),
                user.getProfileComplete(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getPhotoPath(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getUsername(),
                user.getDistrict()
        );
    }

    // ─── Request password reset ───────────────────────────────────────────
    public void requestPasswordReset(String email) {
        if (email == null || email.trim().isEmpty()) return; // do not reveal existence
        String addr = email.trim().toLowerCase();
        userRepository.findByEmail(addr).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            PasswordResetToken prt = PasswordResetToken.builder()
                    .token(token)
                    .email(addr)
                    .expiresAt(LocalDateTime.now().plusHours(4))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(prt);

            String resetUrl = String.format("%s/reset.html?token=%s", "http://localhost:8080", token);
            String subject = "LandVerify ZM — Password reset request";
            String body = String.format("Dear %s,\n\nWe received a request to reset your password. Click the link below to reset your password (valid for 4 hours):\n\n%s\n\nIf you did not request this, ignore this email.\n\nRegards,\nLandVerify ZM", user.getFullName() != null ? user.getFullName() : "User", resetUrl);
            try {
                emailService.sendEmail(user.getEmail(), subject, body);
            } catch (Exception e) {
                log.warn("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
            }
        });
    }

    // ─── Complete password reset using token ──────────────────────────────
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.trim().isEmpty()) throw new RuntimeException("Invalid token");
        if (newPassword == null || newPassword.length() < 8) throw new RuntimeException("Password must be at least 8 characters");

        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token.trim())
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

        if (prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired token");
        }

        User user = userRepository.findByEmail(prt.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!isPasswordStrong(newPassword)) {
            throw new RuntimeException("New password must contain at least 8 characters, including upper case, lower case, and numbers");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);
    }

    // ─── Register ─────────────────────────────────────────────────────────────
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByNationalId(req.getNationalId())) {
            throw new RuntimeException("National ID already registered");
        }

        User user = User.builder()
                .fullName(req.getFullName())
                .nationalId(req.getNationalId())
                .email(req.getEmail())
                .phoneNumber(req.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole() != null ? req.getRole() : User.Role.LAND_OWNER)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getUserId(),
                user.getProfileComplete(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getPhotoPath(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getUsername(),
                user.getDistrict()
        );
    }

    // ─── Create Officer ───────────────────────────────────────────────────────
    public User createOfficer(CreateOfficerRequest req) {
        if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email address is required");
        }
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (req.getFullName() == null || req.getFullName().trim().isEmpty()) {
            throw new RuntimeException("Full name is required");
        }
        if (req.getDistrict() == null || req.getDistrict().trim().isEmpty()) {
            throw new RuntimeException("District is required");
        }
        if (req.getPhoneNumber() == null || req.getPhoneNumber().trim().isEmpty()) {
            throw new RuntimeException("Phone number is required");
        }
        if (req.getTemporaryPassword() == null || req.getTemporaryPassword().trim().isEmpty()) {
            throw new RuntimeException("Temporary password is required");
        }

        String email = req.getEmail().trim().toLowerCase();
        String username = req.getUsername().trim();

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new RuntimeException("Invalid email format. Please provide a valid email address");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("This email address is already registered in the system");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("This username is already taken");
        }

        log.info("Creating officer account for email: {} and username: {}", email, username);

        User user = User.builder()
                .fullName(req.getFullName().trim())
                .nationalId("OFFICER-" + username.toUpperCase())
                .email(email)
                .username(username)
                .district(req.getDistrict().trim())
                .province(req.getProvince() != null && !req.getProvince().trim().isBlank() ? req.getProvince().trim() : null)
                .phoneNumber(req.getPhoneNumber().trim())
                .passwordHash(passwordEncoder.encode(req.getTemporaryPassword()))
                .role(req.getRole() != null ? req.getRole() : User.Role.LAND_OFFICER)
                .isActive(true)
                .profileComplete(false)
                .codeUsed(false)
                .build();

        log.info("Preparing to send credentials email for: {}", email);
        try {
            emailService.sendOfficerCredentials(email, username, req.getTemporaryPassword(), req.getFullName());
            log.info("Credentials email sent successfully for: {}", email);
        } catch (Exception e) {
            log.error("Failed to send credentials email to {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to create officer because email delivery failed: " + e.getMessage());
        }

        user = userRepository.save(user);
        log.info("Officer account saved to database successfully for: {}", email);
        return user;
    }

    // ─── Resend Officer Invitation ────────────────────────────────────────────
    public void resendOfficerInvitation(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email address is required");
        }

        String emailAddress = email.trim().toLowerCase();
        log.info("Attempting to resend invitation for email: {}", emailAddress);

        User user = userRepository.findByEmail(emailAddress)
                .orElseThrow(() -> new RuntimeException("No officer found with email: " + emailAddress));

        if (user.getRole() != User.Role.LAND_OFFICER || user.getInvitationCode() == null) {
            throw new RuntimeException("Invalid officer account for invitation resend");
        }

        if (user.getCodeUsed()) {
            throw new RuntimeException("This invitation code has already been used. The officer has completed registration.");
        }

        log.info("Resending invitation to: {}", emailAddress);
        try {
            emailService.sendOfficerInvitation(user.getEmail(), user.getInvitationCode());
            log.info("Invitation email resent successfully to: {}", emailAddress);
        } catch (Exception e) {
            log.error("Failed to resend invitation email to {}: {}", emailAddress, e.getMessage());
            throw new RuntimeException("Failed to resend invitation email: " + e.getMessage() +
                    ". Please check email configuration.");
        }
    }

    // ─── Register Officer with Invitation Code ────────────────────────────────
    public AuthResponse registerOfficerWithCode(RegisterOfficerRequest req) {
        User user = userRepository.findByInvitationCode(req.getInvitationCode())
                .orElseThrow(() -> new RuntimeException("Invalid invitation code"));

        if (user.getCodeUsed()) {
            throw new RuntimeException("Invitation code has already been used");
        }

        if (userRepository.existsByNationalId(req.getNationalId())) {
            throw new RuntimeException("National ID already registered");
        }

        user.setFullName(req.getFullName());
        user.setNationalId(req.getNationalId());
        user.setPhoneNumber(req.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setCodeUsed(true);

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getUserId(),
                user.getProfileComplete(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getPhotoPath(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getUsername(),
                user.getDistrict()
        );
    }

    // ─── Complete Profile ─────────────────────────────────────────────────────
    public User completeProfile(String email, CompleteProfileRequest req) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!(user.getRole() == User.Role.LAND_OFFICER || user.getRole() == User.Role.SENIOR_OFFICER)) {
            throw new RuntimeException("Only officers can complete profile");
        }

        if (user.getProfileComplete()) {
            throw new RuntimeException("Profile already completed");
        }

        user.setAddress(req.getAddress());

        if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            String photoPath = fileService.saveFile(req.getPhoto());
            user.setPhotoPath(photoPath);
        }

        user.setProfileComplete(true);
        return userRepository.save(user);
    }

    // ─── Request Profile Update ───────────────────────────────────────────────
    public PendingProfileChange requestProfileUpdate(String email, UpdateProfileRequest req) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        validateProfileUpdateRequest(req, user);

        // Determine change type
        PendingProfileChange.ChangeType changeType;
        if (req.getNewPassword() != null && !req.getNewPassword().trim().isEmpty()) {
            changeType = PendingProfileChange.ChangeType.PASSWORD_CHANGE;
        } else if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            changeType = PendingProfileChange.ChangeType.PHOTO_UPDATE;
        } else {
            changeType = PendingProfileChange.ChangeType.PROFILE_UPDATE;
        }

        // Save photo if provided
        String photoPath = null;
        if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            photoPath = fileService.saveFile(req.getPhoto());
        }

        PendingProfileChange change = PendingProfileChange.builder()
                .user(user)
                .fullName(isValidValue(req.getFullName())       ? req.getFullName().trim()       : null)
                .phoneNumber(isValidValue(req.getPhoneNumber()) ? req.getPhoneNumber().trim()     : null)
                .address(isValidValue(req.getAddress())         ? req.getAddress().trim()         : null)
                .gender(isValidValue(req.getGender())
                        ? User.Gender.valueOf(req.getGender().trim().toUpperCase())
                        : null)
                .photoPath(photoPath)
                .passwordHash(isValidValue(req.getNewPassword())
                        ? passwordEncoder.encode(req.getNewPassword())
                        : null)
                .changeType(changeType)
                .status(PendingProfileChange.Status.PENDING)
                .build();

        boolean autoApprove = canAutoApprove(req);
        if (autoApprove) {
            change.setStatus(PendingProfileChange.Status.APPROVED);
            change.setApprovedAt(LocalDateTime.now());
            change.setAdminComments("Auto-approved by system constraints");
        }

        change = pendingProfileChangeRepository.save(change);

        if (autoApprove) {
            applyProfileChange(change, user);
            notifyUserOfProfileChangeApproval(change);
        } else {
            notifyAdminsOfProfileChange(change);
        }

        return change;
    }

    // ============================================
    // NEW METHODS FOR DASHBOARD (ADD THESE)
    // ============================================

    /**
     * Update profile directly without pending approval (for simple fields)
     * Used by the dashboard profile form
     */
    @Transactional
    public User updateProfileDirect(String email, UpdateProfileDirectRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isValidValue(req.getFullName())) {
            if (req.getFullName().trim().length() < 3 || req.getFullName().trim().length() > 150) {
                throw new RuntimeException("Full name must be between 3 and 150 characters");
            }
            user.setFullName(req.getFullName().trim());
        }

        if (isValidValue(req.getPhoneNumber())) {
            String digits = req.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() < 7 || digits.length() > 15) {
                throw new RuntimeException("Phone number must contain 7 to 15 digits");
            }
            user.setPhoneNumber(req.getPhoneNumber().trim());
        }

        if (isValidValue(req.getAddress())) {
            if (req.getAddress().trim().length() < 10 || req.getAddress().trim().length() > 250) {
                throw new RuntimeException("Address must be between 10 and 250 characters");
            }
            user.setAddress(req.getAddress().trim());
        }

        if (isValidValue(req.getNationalId())) {
            String nationalId = req.getNationalId().trim();
            if (nationalId.length() < 6 || nationalId.length() > 20) {
                throw new RuntimeException("National ID must be between 6 and 20 characters");
            }
            if (!nationalId.equals(user.getNationalId()) && userRepository.existsByNationalId(nationalId)) {
                throw new RuntimeException("National ID is already in use");
            }
            user.setNationalId(nationalId);
        }

        if (isValidValue(req.getDistrict())) {
            String district = req.getDistrict().trim();
            if (district.length() < 2 || district.length() > 100) {
                throw new RuntimeException("District must be between 2 and 100 characters");
            }
            user.setDistrict(district);
        }

        if (isValidValue(req.getProvince())) {
            String province = req.getProvince().trim();
            if (province.length() < 2 || province.length() > 100) {
                throw new RuntimeException("Province must be between 2 and 100 characters");
            }
            user.setProvince(province);
        }

        if (isValidValue(req.getGender())) {
            try {
                user.setGender(User.Gender.valueOf(req.getGender().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid gender value. Must be MALE, FEMALE, or OTHER");
            }
        }

        return userRepository.save(user);
    }

    @Transactional
    public User updateProfilePhoto(String email, org.springframework.web.multipart.MultipartFile photo) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (photo == null || photo.isEmpty()) {
            throw new RuntimeException("No photo file provided");
        }

        String contentType = photo.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"))) {
            throw new RuntimeException("Profile photo must be JPG, PNG, or WEBP");
        }
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("Profile photo must be smaller than 5 MB");
        }

        try {
            if (user.getPhotoPath() != null) {
                fileService.deleteFile(user.getPhotoPath());
            }
        } catch (IOException ignored) {
            log.warn("Unable to delete old photo for user {}: {}", user.getEmail(), ignored.getMessage());
        }

        String photoPath = fileService.saveFile(photo);
        user.setPhotoPath(photoPath);
        return userRepository.save(user);
    }

    /**
     * Change user password directly (used by dashboard)
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new RuntimeException("New password and confirmation do not match");
        }

        if (!isPasswordStrong(req.getNewPassword())) {
            throw new RuntimeException("New password must contain at least 8 characters, including upper case, lower case, and numbers");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", email);
    }

    /**
     * Get user by email (returns full user object for dashboard)
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    /**
     * Update user session data (for dashboard after profile update)
     */
    public AuthResponse refreshSession(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getUserId(),
                user.getProfileComplete(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getPhotoPath(),
                user.getGender() != null ? user.getGender().name() : null,
                user.getUsername(),
                user.getDistrict()
        );
    }

    // ============================================
    // EXISTING HELPER METHODS (Keep as they are)
    // ============================================

    private void validateProfileUpdateRequest(UpdateProfileRequest req, User user) {
        if (!isValidValue(req.getFullName())
                && !isValidValue(req.getPhoneNumber())
                && !isValidValue(req.getAddress())
                && !isValidValue(req.getGender())
                && (req.getPhoto() == null || req.getPhoto().isEmpty())
                && !isValidValue(req.getNewPassword())) {
            throw new RuntimeException("At least one profile field must be provided for update");
        }

        if (isValidValue(req.getFullName())) {
            String value = req.getFullName().trim();
            if (value.length() < 3 || value.length() > 150) {
                throw new RuntimeException("Full name must be between 3 and 150 characters");
            }
        }

        if (isValidValue(req.getPhoneNumber())) {
            String digits = req.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() < 7 || digits.length() > 15) {
                throw new RuntimeException("Phone number must contain 7 to 15 digits");
            }
        }

        if (isValidValue(req.getAddress())) {
            String value = req.getAddress().trim();
            if (value.length() < 10 || value.length() > 250) {
                throw new RuntimeException("Address must be between 10 and 250 characters");
            }
        }

        if (req.getPhoto() != null && !req.getPhoto().isEmpty()) {
            String contentType = req.getPhoto().getContentType();
            if (contentType == null || !(contentType.equals("image/jpeg")
                    || contentType.equals("image/png")
                    || contentType.equals("image/webp"))) {
                throw new RuntimeException("Profile photo must be JPG, PNG, or WEBP");
            }
            if (req.getPhoto().getSize() > 5 * 1024 * 1024) {
                throw new RuntimeException("Profile photo must be smaller than 5 MB");
            }
        }

        if (isValidValue(req.getNewPassword())) {
            if (!isValidValue(req.getCurrentPassword())) {
                throw new RuntimeException("Current password is required to change password");
            }
            if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
                throw new RuntimeException("Current password is incorrect");
            }
            if (!isPasswordStrong(req.getNewPassword())) {
                throw new RuntimeException("New password must contain at least 8 characters, including upper case, lower case, and numbers");
            }
        }
    }

    private boolean canAutoApprove(UpdateProfileRequest req) {
        return true;
    }

    private boolean isPasswordStrong(String password) {
        return password.matches("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}");
    }

    private void applyProfileChange(PendingProfileChange change, User user) {
        if (change.getFullName() != null)    user.setFullName(change.getFullName());
        if (change.getPhoneNumber() != null) user.setPhoneNumber(change.getPhoneNumber());
        if (change.getAddress() != null)     user.setAddress(change.getAddress());
        if (change.getGender() != null)      user.setGender(change.getGender());
        if (change.getPhotoPath() != null)   user.setPhotoPath(change.getPhotoPath());
        if (change.getPasswordHash() != null) {
            user.setPasswordHash(change.getPasswordHash());
            user.setPasswordChangedAt(LocalDateTime.now());
        }
        userRepository.save(user);
    }

    public void approveProfileChange(Integer changeId, String adminEmail, String comments) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRole() != User.Role.SYSTEM_ADMIN) {
            throw new RuntimeException("Only system administrators can approve profile changes");
        }

        PendingProfileChange change = pendingProfileChangeRepository.findById(changeId)
                .orElseThrow(() -> new RuntimeException("Profile change request not found"));

        if (change.getStatus() != PendingProfileChange.Status.PENDING) {
            throw new RuntimeException("Profile change request has already been processed");
        }

        User user = change.getUser();

        if (change.getFullName() != null)    user.setFullName(change.getFullName());
        if (change.getPhoneNumber() != null) user.setPhoneNumber(change.getPhoneNumber());
        if (change.getAddress() != null)     user.setAddress(change.getAddress());
        if (change.getGender() != null)      user.setGender(change.getGender());
        if (change.getPhotoPath() != null)   user.setPhotoPath(change.getPhotoPath());
        if (change.getPasswordHash() != null) {
            user.setPasswordHash(change.getPasswordHash());
            user.setPasswordChangedAt(LocalDateTime.now());
        }

        userRepository.save(user);

        change.setStatus(PendingProfileChange.Status.APPROVED);
        change.setApprovedBy(admin);
        change.setApprovedAt(LocalDateTime.now());
        change.setAdminComments(comments);
        pendingProfileChangeRepository.save(change);

        notifyUserOfProfileChangeApproval(change);
    }

    public void rejectProfileChange(Integer changeId, String adminEmail, String comments) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (admin.getRole() != User.Role.SYSTEM_ADMIN) {
            throw new RuntimeException("Only system administrators can reject profile changes");
        }

        PendingProfileChange change = pendingProfileChangeRepository.findById(changeId)
                .orElseThrow(() -> new RuntimeException("Profile change request not found"));

        if (change.getStatus() != PendingProfileChange.Status.PENDING) {
            throw new RuntimeException("Profile change request has already been processed");
        }

        change.setStatus(PendingProfileChange.Status.REJECTED);
        change.setApprovedBy(admin);
        change.setApprovedAt(LocalDateTime.now());
        change.setAdminComments(comments);
        pendingProfileChangeRepository.save(change);

        if (change.getPhotoPath() != null) {
            try {
                fileService.deleteFile(change.getPhotoPath());
            } catch (Exception e) {
                log.warn("Failed to delete rejected photo file: {}", change.getPhotoPath());
            }
        }

        notifyUserOfProfileChangeRejection(change);
    }

    private void notifyAdminsOfProfileChange(PendingProfileChange change) {
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.SYSTEM_ADMIN && u.getIsActive())
                .collect(Collectors.toList());

        String message = String.format(
                "Profile change request from %s (%s) requires approval. Change type: %s",
                change.getUser().getFullName(),
                change.getUser().getEmail(),
                change.getChangeType());

        for (User admin : admins) {
            Notification notification = Notification.builder()
                    .recipient(admin)
                    .message(message)
                    .notificationType(Notification.NotificationType.PROFILE_CHANGE_REQUESTED)
                    .relatedUserId(change.getUser().getUserId())
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    private void notifyUserOfProfileChangeApproval(PendingProfileChange change) {
        String message = String.format(
                "Your profile change request has been approved. Change type: %s",
                change.getChangeType());

        if (change.getAdminComments() != null && !change.getAdminComments().trim().isEmpty()) {
            message += ". Comments: " + change.getAdminComments();
        }

        Notification notification = Notification.builder()
                .recipient(change.getUser())
                .message(message)
                .notificationType(Notification.NotificationType.PROFILE_CHANGE_APPROVED)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    private void notifyUserOfProfileChangeRejection(PendingProfileChange change) {
        String message = String.format(
                "Your profile change request has been rejected. Change type: %s",
                change.getChangeType());

        if (change.getAdminComments() != null && !change.getAdminComments().trim().isEmpty()) {
            message += ". Reason: " + change.getAdminComments();
        }

        Notification notification = Notification.builder()
                .recipient(change.getUser())
                .message(message)
                .notificationType(Notification.NotificationType.PROFILE_CHANGE_REJECTED)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    public List<PendingProfileChange> getPendingChangesByUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return pendingProfileChangeRepository.findByUserAndStatus(user, PendingProfileChange.Status.PENDING);
    }

    public User getUserProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    private boolean isValidValue(String value) {
        return value != null
                && !value.trim().isEmpty()
                && !value.trim().equalsIgnoreCase("null");
    }
}