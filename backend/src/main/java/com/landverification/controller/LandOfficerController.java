package com.landverification.controller;

import com.landverification.dto.*;
import com.landverification.model.*;
import com.landverification.repository.BlockchainAuditLogRepository;
import com.landverification.repository.LandParcelRepository;
import com.landverification.repository.NotificationRepository;
import com.landverification.repository.PendingProfileChangeRepository;
import com.landverification.repository.UserRepository;
import com.landverification.service.AuthService;
import com.landverification.service.LandParcelService;
import com.landverification.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/officer")
@RequiredArgsConstructor
public class LandOfficerController {

    private final LandParcelService parcelService;
    private final TransferService transferService;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final PendingProfileChangeRepository pendingProfileChangeRepository;
    private final NotificationRepository notificationRepository;
    private final BlockchainAuditLogRepository blockchainAuditLogRepository;
    private final LandParcelRepository landParcelRepository;
    private final com.landverification.service.JurisdictionAccessService jurisdictionAccessService;

    // ============================================
    // EXISTING ENDPOINTS (Keep these as they are)
    // ============================================
    
    @PostMapping(value = "/parcel/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<ParcelResponse>> registerParcel(
            @Valid @RequestBody ParcelRegistrationRequest req, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Parcel registered",
                parcelService.registerParcel(req, officer.getUserId())));
    }

    @PostMapping(value = "/parcel/register", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ParcelResponse>> registerParcelWithPhoto(
            @Valid @RequestPart("payload") ParcelRegistrationRequest req,
            @RequestPart(value = "ownerPhoto", required = false) MultipartFile ownerPhoto,
            Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Parcel registered",
                parcelService.registerParcel(req, officer.getUserId(), ownerPhoto)));
    }

    @GetMapping("/owner/search")
    public ResponseEntity<ApiResponse<List<com.landverification.dto.LandownerWithParcelsResponse>>> searchLandowners(
            @RequestParam("q") String query) {
        return ResponseEntity.ok(ApiResponse.ok("Landowner search results",
                parcelService.searchLandowners(query)));
    }

    @GetMapping("/parcel/{parcelNumber}")
    public ResponseEntity<ApiResponse<ParcelResponse>> getParcel(
            @PathVariable String parcelNumber, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Parcel details",
                parcelService.getParcelByNumber(parcelNumber, officer)));
    }

    @GetMapping("/parcels")
    public ResponseEntity<ApiResponse<List<ParcelResponse>>> searchParcels(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Parcel search results",
                parcelService.searchParcels(query, fromDate, toDate, officer)));
    }

    @GetMapping("/transfers/pending")
    public ResponseEntity<ApiResponse<List<TransferResponse>>> pendingTransfers(Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Pending transfers",
                transferService.getPendingTransfersForOfficer(officer.getUserId())));
    }

    /**
     * General transfers endpoint for officer UI — supports optional status filter.
     * Example: /api/officer/transfers?status=SUBMITTED or /api/officer/transfers?status=APPROVED
     */
    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<TransferResponse>>> getTransfers(
            @RequestParam(value = "status", required = false) String status, Authentication auth) {
        User officer = getUser(auth);
        if (status != null && !status.isBlank()) {
            try {
                TransferRequest.TransferStatus s = TransferRequest.TransferStatus.valueOf(status);
                return ResponseEntity.ok(ApiResponse.ok("Transfers by status",
                        transferService.getTransfersByStatus(s, officer)));
            } catch (IllegalArgumentException iae) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status value"));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Pending transfers",
                transferService.getPendingTransfersForOfficer(officer.getUserId())));
    }

    @GetMapping("/parcels/rejected")
    public ResponseEntity<ApiResponse<List<ParcelResponse>>> getRejectedParcels(Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Rejected parcels",
                parcelService.getRejectedParcels(officer)));
    }

    @GetMapping("/parcels/approved")
    public ResponseEntity<ApiResponse<List<ParcelResponse>>> getApprovedParcels(
            @RequestParam(value = "includePartial", required = false, defaultValue = "false") boolean includePartial,
            Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Approved parcels",
                parcelService.getApprovedParcels(includePartial, officer)));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> notifications(Authentication auth) {
        User officer = getUser(auth);
        List<NotificationResponse> responses = notificationRepository
                .findByRecipient_UserIdOrderByCreatedAtDesc(officer.getUserId())
                .stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Notifications", responses));
    }

    @GetMapping("/notifications/count")
    public ResponseEntity<ApiResponse<Long>> unreadNotificationCount(Authentication auth) {
        User officer = getUser(auth);
        long count = notificationRepository.countByRecipient_UserIdAndIsReadFalse(officer.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Unread notification count", count));
    }

    @GetMapping("/blockchain/parcel/{parcelId}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getParcelBlockchainRecord(
            @PathVariable Integer parcelId, Authentication auth) {
        User officer = getUser(auth);
        LandParcel parcel = landParcelRepository.findById(parcelId)
            .orElseThrow(() -> new RuntimeException("Parcel not found"));
        if (!jurisdictionAccessService.canAccess(officer, parcel)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to view this blockchain record"));
        }

        return blockchainAuditLogRepository.findByRelatedParcelId(parcelId).stream()
                .filter(log -> log.getEventType() == BlockchainAuditLog.EventType.PARCEL_REGISTERED)
                .findFirst()
                .map(log -> {
                    java.util.Map<String, Object> record = new java.util.LinkedHashMap<>();
                    record.put("logId", log.getLogId());
                    record.put("transactionHash", log.getTransactionHash());
                    record.put("blockNumber", log.getBlockNumber());
                    record.put("contractAddress", log.getContractAddress());
                    record.put("eventType", log.getEventType().name());
                    record.put("payloadHash", log.getPayloadHash());
                    record.put("recordedAt", log.getRecordedAt());
                    return ResponseEntity.ok(ApiResponse.ok("Blockchain record", record));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Blockchain record not found for this parcel")));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Integer id, Authentication auth) {
        User officer = getUser(auth);
        var notificationOpt = notificationRepository.findById(id);
        if (notificationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Notification not found"));
        }

        var notification = notificationOpt.get();
        if (notification.getRecipient() == null || !notification.getRecipient().getUserId().equals(officer.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to modify this notification"));
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    @PostMapping("/transfer/reject")
    public ResponseEntity<ApiResponse<TransferResponse>> rejectTransfer(
            @Valid @RequestBody RejectionRequest req, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Transfer rejected",
                transferService.rejectTransfer(req, officer.getUserId())));
    }

    @PostMapping("/profile/complete")
    public ResponseEntity<ApiResponse<User>> completeProfile(
            @RequestParam("address") String address,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            Authentication auth) throws IOException {
        User officer = getUser(auth);
        CompleteProfileRequest req = new CompleteProfileRequest();
        req.setAddress(address);
        req.setPhoto(photo);
        User updatedUser = authService.completeProfile(officer.getEmail(), req);
        return ResponseEntity.ok(ApiResponse.ok("Profile completed successfully", updatedUser));
    }

    @PostMapping("/profile/update")
    public ResponseEntity<ApiResponse<PendingProfileChangeResponse>> updateProfile(
            @ModelAttribute UpdateProfileRequest req, Authentication auth) throws IOException {
        User officer = getUser(auth);
        var change = authService.requestProfileUpdate(officer.getEmail(), req);
        return ResponseEntity.ok(ApiResponse.ok("Profile update request submitted for approval",
                PendingProfileChangeResponse.fromEntity(change)));
    }

    @PostMapping("/profile/photo")
    public ResponseEntity<ApiResponse<User>> updateProfilePhoto(
            @RequestParam("photo") MultipartFile photo, Authentication auth) throws IOException {
        User officer = getUser(auth);
        User updatedUser = authService.updateProfilePhoto(officer.getEmail(), photo);
        return ResponseEntity.ok(ApiResponse.ok("Profile photo updated successfully", updatedUser));
    }

    @GetMapping("/profile/pending-changes")
    public ResponseEntity<ApiResponse<List<PendingProfileChangeResponse>>> getPendingChanges(Authentication auth) {
        User officer = getUser(auth);
        var changes = pendingProfileChangeRepository.findByUserAndStatus(officer,
                com.landverification.model.PendingProfileChange.Status.PENDING);
        var responses = changes.stream()
                .map(PendingProfileChangeResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Pending profile changes", responses));
    }

    // ============================================
    // NEW ENDPOINTS FOR THE DASHBOARD
    // ============================================

    /**
     * Approve transfer at Level 1 (matches dashboard button)
     */
    @PostMapping("/transfer/approve")
    public ResponseEntity<ApiResponse<TransferResponse>> approveTransfer(
            @Valid @RequestBody ApproveRequest req, Authentication auth) {
        User officer = getUser(auth);
        TransferResponse response = transferService.landOfficerApprove(req, officer.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Transfer approved and sent to Senior Officer", response));
    }

    /**
     * Get dashboard statistics (for the stat cards)
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboardStats(Authentication auth) {
        User officer = getUser(auth);
        DashboardStats stats = transferService.getDashboardStats(officer.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Dashboard statistics", stats));
    }

    /**
     * Attach documents to a parcel (for documents section)
     */
    @PostMapping("/documents/attach")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> attachDocuments(
            @RequestParam("parcelNumber") String parcelNumber,
            @RequestParam("category") String category,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam("files") List<MultipartFile> files,
            Authentication auth) throws IOException {
        User officer = getUser(auth);
        List<DocumentResponse> documents = parcelService.attachDocuments(parcelNumber, category, notes, files, officer);
        return ResponseEntity.ok(ApiResponse.ok("Documents attached successfully", documents));
    }

    /**
     * Get recent documents (for documents section)
     */
    @GetMapping("/documents/recent")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getRecentDocuments(
            Authentication auth) {
        User officer = getUser(auth);
        List<DocumentResponse> documents = parcelService.getRecentDocuments(officer.getUserId(), 10);
        return ResponseEntity.ok(ApiResponse.ok("Recent documents", documents));
    }

    /**
     * Get all documents with filters (for documents section)
     */
    @GetMapping("/documents/list")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocuments(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            Authentication auth) {
        User officer = getUser(auth);
        List<DocumentResponse> documents = parcelService.getFilteredDocuments(officer.getUserId(), type, search);
        return ResponseEntity.ok(ApiResponse.ok("Documents list", documents));
    }

    /**
     * Get current user profile (for profile section)
     */
    @GetMapping("/profile/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Profile data", UserResponse.fromEntity(officer)));
    }

    /**
     * Update profile directly (without pending approval for simple fields)
     */
    @PostMapping("/profile/update-direct")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfileDirect(
            @RequestBody UpdateProfileDirectRequest req, Authentication auth) {
        User officer = getUser(auth);
        User updated = authService.updateProfileDirect(officer.getEmail(), req);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", UserResponse.fromEntity(updated)));
    }

    /**
     * Change password
     */
    @PostMapping("/profile/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        User officer = getUser(auth);
        authService.changePassword(officer.getEmail(), req);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }

    /**
     * Generate next sequential parcel number
     * Auto-generates the next parcel number to avoid duplicates
     */
    @GetMapping("/parcel/generate-number")
    public ResponseEntity<ApiResponse<ParcelNumberResponse>> generateParcelNumber(
            @RequestParam("province") String province) {
        String nextParcelNumber = parcelService.generateNextParcelNumber(province);
        return ResponseEntity.ok(ApiResponse.ok("Next parcel number generated",
                new ParcelNumberResponse(nextParcelNumber)));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}