package com.landverification.controller;

import com.landverification.dto.ApiResponse;
import com.landverification.dto.AuthResponse;
import com.landverification.dto.CreateOfficerRequest;
import com.landverification.dto.PendingProfileChangeResponse;
import com.landverification.dto.RegisterRequest;
import com.landverification.repository.PendingProfileChangeRepository;
import com.landverification.model.User;
import com.landverification.repository.UserRepository;
import com.landverification.repository.LandParcelRepository;
import com.landverification.repository.TransferRequestRepository;
import com.landverification.repository.BlockchainAuditLogRepository;
import com.landverification.service.AuthService;
import com.landverification.service.EmailService;
import com.landverification.service.BlockchainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.web3j.protocol.Web3j;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final PendingProfileChangeRepository pendingProfileChangeRepository;
    private final LandParcelRepository landParcelRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final BlockchainAuditLogRepository blockchainAuditLogRepository;
    private final BlockchainService blockchainService;
    private final Web3j web3j;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            log.info("Fetched {} users from database", users.size());
            return ResponseEntity.ok(ApiResponse.ok("All users", users));
        } catch (Exception e) {
            log.error("Error fetching users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch users: " + e.getMessage()));
        }
    }

    @GetMapping("/system-statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> systemStatistics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", userRepository.count());
        data.put("activeUsers", userRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count());
        data.put("landOfficers", userRepository.findByRole(User.Role.LAND_OFFICER).size());
        data.put("seniorOfficers", userRepository.findByRole(User.Role.SENIOR_OFFICER).size());
        data.put("admins", userRepository.findByRole(User.Role.SYSTEM_ADMIN).size());
        data.put("landowners", userRepository.findByRole(User.Role.LAND_OWNER).size());
        data.put("totalParcels", landParcelRepository.count());
        data.put("totalTransfers", transferRequestRepository.count());
        data.put("pendingTransfers", transferRequestRepository.findAll().stream()
                .filter(t -> t.getStatus() == com.landverification.model.TransferRequest.TransferStatus.SUBMITTED
                        || t.getStatus() == com.landverification.model.TransferRequest.TransferStatus.UNDER_REVIEW
                        || t.getStatus() == com.landverification.model.TransferRequest.TransferStatus.APPROVED_L1)
                .count());
        data.put("blockchainRecords", blockchainAuditLogRepository.count());
        data.put("completedTransfers", transferRequestRepository.findByStatus(com.landverification.model.TransferRequest.TransferStatus.APPROVED).size());
        data.put("rejectedTransfers", transferRequestRepository.findByStatus(com.landverification.model.TransferRequest.TransferStatus.REJECTED).size());
        data.put("flaggedTransfers", transferRequestRepository.findAll().stream().filter(com.landverification.model.TransferRequest::isFlagged).count());
        data.put("registrationByMonth", landParcelRepository.findAll().stream()
            .filter(p -> p.getRegisteredAt() != null)
            .collect(Collectors.groupingBy(p -> p.getRegisteredAt().getMonthValue(), Collectors.counting())));
        data.put("parcelsByProvince", landParcelRepository.findAll().stream()
            .collect(Collectors.groupingBy(com.landverification.model.LandParcel::getProvince, Collectors.counting())));
        data.put("transfersByStatus", transferRequestRepository.findAll().stream()
            .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting())));
        Map<String, Map<String, Long>> provinceStats = new LinkedHashMap<>();
        landParcelRepository.findAll().forEach(parcel -> {
            String province = parcel.getProvince() != null ? parcel.getProvince() : "Unknown";
            provinceStats.computeIfAbsent(province, key -> new LinkedHashMap<>())
                    .merge("parcels", 1L, Long::sum);
        });
        transferRequestRepository.findAll().forEach(transfer -> {
            String province = transfer.getParcel() != null && transfer.getParcel().getProvince() != null
                    ? transfer.getParcel().getProvince() : "Unknown";
            Map<String, Long> stats = provinceStats.computeIfAbsent(province, key -> new LinkedHashMap<>());
            stats.merge("transfers", 1L, Long::sum);
            if (transfer.isFlagged()) stats.merge("flagged", 1L, Long::sum);
        });
        data.put("provinceStats", provinceStats);
        return ResponseEntity.ok(ApiResponse.ok("System statistics", data));
    }

    @GetMapping("/system-monitoring")
    public ResponseEntity<ApiResponse<Map<String, Object>>> systemMonitoring() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "OPERATIONAL");
        data.put("database", "CONNECTED");
        data.put("api", "AVAILABLE");
        try {
            web3j.ethChainId().send();
            data.put("blockchain", "CONNECTED");
        } catch (Exception e) {
            data.put("blockchain", "UNAVAILABLE");
        }
        data.put("activeUsers", userRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count());
        data.put("warnings", transferRequestRepository.findAll().stream().filter(com.landverification.model.TransferRequest::isFlagged).count());
        data.put("activitiesToday", blockchainAuditLogRepository.findAll().stream()
            .filter(log -> log.getRecordedAt() != null && log.getRecordedAt().toLocalDate().equals(java.time.LocalDate.now())).count());
        data.put("checkedAt", java.time.LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.ok("System monitoring", data));
    }

    @GetMapping("/blockchain-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> blockchainStatus() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contractAddress", blockchainService.getContractAddress());
        data.put("contractName", "LandRegistry");
        data.put("network", "Hardhat Local Network");
        try {
            data.put("chainId", web3j.ethChainId().send().getChainId());
            data.put("latestBlock", web3j.ethBlockNumber().send().getBlockNumber());
            data.put("status", "OPERATIONAL");
        } catch (Exception e) {
            data.put("status", "UNAVAILABLE");
            data.put("error", e.getMessage());
        }
            data.put("recordCount", blockchainAuditLogRepository.count());
            data.put("recentTransactions", blockchainAuditLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(com.landverification.model.BlockchainAuditLog::getRecordedAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(10).toList());
            data.put("transactionTypes", blockchainAuditLogRepository.findAll().stream()
                .collect(Collectors.groupingBy(log -> log.getEventType().name(), Collectors.counting())));
        return ResponseEntity.ok(ApiResponse.ok("Blockchain system status", data));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUserById(@PathVariable Integer id) {
        try {
            var optionalUser = userRepository.findById(id);
            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found"));
            }
            User user = optionalUser.get();
            // Do not expose password hash
            user.setPasswordHash(null);
            return ResponseEntity.ok(ApiResponse.ok("User details", user));
        } catch (Exception e) {
            log.error("Error fetching user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user: " + e.getMessage()));
        }
    }

    @PostMapping("/users/create")
    public ResponseEntity<ApiResponse<AuthResponse>> createUser(
            @Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("User created", authService.register(req)));
    }

    @PostMapping("/officers/create")
    public ResponseEntity<ApiResponse<User>> createOfficer(
            @Valid @RequestBody CreateOfficerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Officer account created and email sent",
                authService.createOfficer(req)));
    }

    @PostMapping("/officers/resend-invitation")
    public ResponseEntity<ApiResponse<Void>> resendOfficerInvitation(
            @RequestParam String email) {
        authService.resendOfficerInvitation(email);
        return ResponseEntity.ok(ApiResponse.ok("Invitation email resent successfully", null));
    }

    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse<Void>> testEmailConfiguration(
            @RequestParam String testEmail) {
        try {
            emailService.sendEmail(testEmail, "Land Verification System - Email Test",
                "This is a test email from the Land Verification System.\n\n" +
                "If you received this email, the email configuration is working correctly.\n\n" +
                "System timestamp: " + java.time.LocalDateTime.now() + "\n\n" +
                "Best regards,\nLand Verification System");
            return ResponseEntity.ok(ApiResponse.ok("Test email sent successfully to: " + testEmail, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email test failed: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/toggle-active")
    public ResponseEntity<ApiResponse<Void>> toggleActive(@PathVariable Integer id) {
        try {
            User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
            user.setIsActive(!user.getIsActive());
            userRepository.save(user);
            log.info("User {} status toggled to: {}", user.getEmail(), user.getIsActive());
            return ResponseEntity.ok(ApiResponse.ok("User status updated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<Void>> changeRole(
            @PathVariable Integer id,
            @RequestParam String role) {
        try {
            User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
            if (user.getRole() == User.Role.LAND_OWNER) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Cannot change role for landowner users; they represent external entities."));
            }
            user.setRole(User.Role.valueOf(role));
            userRepository.save(user);
            log.info("User {} role changed to: {}", user.getEmail(), role);
            return ResponseEntity.ok(ApiResponse.ok("Role updated", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid target role: " + role));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Integer id, Authentication auth) {
        try {
            log.info("Delete request for user ID: {} by admin: {}", id, auth.getName());
            
            var optionalUser = userRepository.findById(id);
            if (optionalUser.isEmpty()) {
                log.warn("User with ID {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found"));
            }
            
            User userToDelete = optionalUser.get();
            
            // Prevent deleting self
            if (userToDelete.getEmail().equals(auth.getName())) {
                log.warn("Admin {} attempted to delete own account", auth.getName());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Cannot delete your own account"));
            }
            
            // Check if user is an officer before deletion
            String role = userToDelete.getRole().toString();
            if (!role.equals("LAND_OFFICER") && !role.equals("SENIOR_OFFICER")) {
                log.warn("Attempted to delete non-officer user: {} with role: {}", userToDelete.getEmail(), role);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Can only delete Land Officer or Senior Officer accounts"));
            }
            
            // Delete the user
            userRepository.delete(userToDelete);
            log.info("User successfully deleted: {} (ID: {}) by admin: {}", 
                userToDelete.getEmail(), id, auth.getName());
            
            return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", null));
            
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete user: " + e.getMessage()));
        }
    }

    @GetMapping("/profile-changes/pending")
    public ResponseEntity<ApiResponse<java.util.List<PendingProfileChangeResponse>>> getPendingProfileChanges() {
        var changes = pendingProfileChangeRepository.findByStatus(
                com.landverification.model.PendingProfileChange.Status.PENDING);
        var responses = changes.stream()
                .map(PendingProfileChangeResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Pending profile changes", responses));
    }

    @PostMapping("/profile-changes/{changeId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProfileChange(
            @PathVariable Integer changeId,
            @RequestParam(required = false) String comments,
            org.springframework.security.core.Authentication auth) {
        String adminEmail = auth.getName();
        authService.approveProfileChange(changeId, adminEmail, comments);
        return ResponseEntity.ok(ApiResponse.ok("Profile change approved successfully", null));
    }

    @PostMapping("/profile-changes/{changeId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectProfileChange(
            @PathVariable Integer changeId,
            @RequestParam String comments,
            org.springframework.security.core.Authentication auth) {
        String adminEmail = auth.getName();
        authService.rejectProfileChange(changeId, adminEmail, comments);
        return ResponseEntity.ok(ApiResponse.ok("Profile change rejected", null));
    }
}