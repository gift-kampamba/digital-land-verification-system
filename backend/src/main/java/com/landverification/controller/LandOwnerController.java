package com.landverification.controller;

import com.landverification.dto.*;
import com.landverification.dto.BuyerSearchResponse;
import com.landverification.dto.BuyerSearchRequest;
import com.landverification.model.User;
import com.landverification.repository.BlockchainAuditLogRepository;
import com.landverification.repository.NotificationRepository;
import com.landverification.repository.UserRepository;
import com.landverification.dto.NotificationResponse;
import com.landverification.model.LandParcel;
import com.landverification.repository.LandParcelRepository;
import com.landverification.config.AppProperties;
import com.landverification.service.AuthService;
import com.landverification.service.LandParcelService;
import com.landverification.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/landowner")
@RequiredArgsConstructor
public class LandOwnerController {

    private final LandParcelService parcelService;
    private final TransferService transferService;
    private final BlockchainAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final AuthService authService;
    private final LandParcelRepository landParcelRepository;
    private final AppProperties appProperties;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LandOwnerController.class);

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<LandownerDashboardResponse>> dashboard(Authentication auth) {
        log.info("========== Landowner Dashboard Request ==========");
        log.info("Auth Name (email): {}", auth.getName());
        User user = getUser(auth);
        log.info("User found: ID={}, Name={}, Email={}", user.getUserId(), user.getFullName(), user.getEmail());
        java.util.List<com.landverification.dto.ParcelResponse> parcels = parcelService.getParcelsByOwner(user.getUserId());
        log.info("Parcels found for user {}: {} parcels", user.getUserId(), parcels.size());
        parcels.forEach(p -> log.info("  - Parcel: {} (Owner: {})", p.getParcelNumber(), p.getOwnerFullName()));
        return ResponseEntity.ok(ApiResponse.ok("Landowner dashboard",
                LandownerDashboardResponse.fromUser(user, parcels)));
    }

    @GetMapping("/my-parcels")
    public ResponseEntity<ApiResponse<List<ParcelResponse>>> myParcels(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Your parcels",
                parcelService.getParcelsByOwner(user.getUserId())));
    }

    @GetMapping("/parcels/{id}/title-deed")
    public ResponseEntity<byte[]> getTitleDeedById(
            @PathVariable Integer id, Authentication auth) {
        try {
            LandParcel parcel = landParcelRepository.findById(id).orElse(null);
            return getTitleDeedResponse(parcel);
        } catch (Exception ex) {
            log.warn("Failed to read title deed for parcel {}: {}", id, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/title-deed/{parcelNumber}")
    public ResponseEntity<byte[]> getTitleDeedByNumber(
            @PathVariable String parcelNumber, Authentication auth) {
        try {
            LandParcel parcel = landParcelRepository.findByParcelNumber(parcelNumber).orElse(null);
            return getTitleDeedResponse(parcel);
        } catch (Exception ex) {
            log.warn("Failed to read title deed for parcel number {}: {}", parcelNumber, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<byte[]> getTitleDeedResponse(LandParcel parcel) throws IOException {
            if (parcel == null) return ResponseEntity.notFound().build();

            Path deedPath = null;
            for (Path uploadRoot : uploadRoots()) {
                deedPath = resolveTitleDeedPath(parcel, uploadRoot);
                if (deedPath != null) break;
            }
            if (deedPath == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaTypeFactory.getMediaType(deedPath.getFileName().toString())
                            .orElse(org.springframework.http.MediaType.APPLICATION_PDF))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"title-deed.pdf\"")
                    .body(Files.readAllBytes(deedPath));
    }

    private Path resolveTitleDeedPath(LandParcel parcel, Path uploadRoot) throws IOException {
        if (parcel.getTitleDeedFile() != null && !parcel.getTitleDeedFile().isBlank()) {
            Path storedPath = uploadRoot.resolve(parcel.getTitleDeedFile()).normalize();
            if (storedPath.startsWith(uploadRoot) && Files.exists(storedPath)) return storedPath;
        }
        Path deedFolder = uploadRoot.resolve("title-deeds").normalize();
        if (!Files.isDirectory(deedFolder) || parcel.getParcelNumber() == null) return null;
        String prefix = ("title-deed-" + parcel.getParcelNumber() + "-").toLowerCase();
        try (java.util.stream.Stream<Path> files = Files.list(deedFolder)) {
            return files.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().toLowerCase().startsWith(prefix))
                    .findFirst().orElse(null);
        }
    }

    private List<Path> uploadRoots() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize());
        roots.add(cwd.resolve("uploads").normalize());
        if (cwd.getParent() != null) roots.add(cwd.getParent().resolve("uploads").normalize());
        if (cwd.getParent() != null && cwd.getParent().getParent() != null) {
            roots.add(cwd.getParent().getParent().resolve("uploads").normalize());
        }
        return roots.stream().distinct().toList();
    }

    @GetMapping("/my-transfers")
    public ResponseEntity<ApiResponse<List<TransferResponse>>> myTransfers(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Your transfers",
                transferService.getTransfersByUser(user.getUserId())));
    }

    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<List<com.landverification.model.BlockchainAuditLog>>> auditLog(
            @RequestParam(required = false) Integer parcelId) {
        if (parcelId != null) {
            return ResponseEntity.ok(ApiResponse.ok("Parcel audit log",
                    auditLogRepository.findByRelatedParcelId(parcelId)));
        }
        return ResponseEntity.ok(ApiResponse.ok("Blockchain audit log",
                auditLogRepository.findAll()));
    }

    /**
     * Search for an existing buyer account by NRC, email, or account number
     * Used during transfer initiation when buyer has an existing account
     */
    @PostMapping("/transfer/search-buyer")
    public ResponseEntity<ApiResponse<List<BuyerSearchResponse>>> searchBuyer(
            @Valid @RequestBody BuyerSearchRequest req) {
        List<BuyerSearchResponse> results = transferService.searchBuyerAccount(req);
        return ResponseEntity.ok(ApiResponse.ok("Buyer search results", results));
    }

    @PostMapping(value = "/transfer/initiate", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(
            @Valid @RequestPart("payload") InitiateTransferRequest req,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "sellerPhoto", required = false) MultipartFile sellerPhoto,
            @RequestPart(value = "buyerPhoto", required = false) MultipartFile buyerPhoto,
            Authentication auth) {
        User user = getUser(auth);
        TransferResponse transferResponse = transferService.initiateTransfer(req, user.getUserId(), files, sellerPhoto, buyerPhoto);
        String message = "Transfer initiated";
        if (transferResponse.getEmailStatusMessage() != null) {
            message = "Transfer initiated and email sent successfully. " + transferResponse.getEmailStatusMessage();
        }
        return ResponseEntity.ok(ApiResponse.ok(message, transferResponse));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> notifications(Authentication auth) {
        User user = getUser(auth);
        List<NotificationResponse> responses = notificationRepository
                .findByRecipient_UserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Notifications", responses));
    }

    @GetMapping("/notifications/count")
    public ResponseEntity<ApiResponse<Long>> unreadNotificationCount(Authentication auth) {
        User user = getUser(auth);
        long count = notificationRepository.countByRecipient_UserIdAndIsReadFalse(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Unread notification count", count));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Integer id, Authentication auth) {
        User user = getUser(auth);
        var notificationOpt = notificationRepository.findById(id);
        if (notificationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Notification not found"));
        }

        var notification = notificationOpt.get();
        if (notification.getRecipient() == null || !notification.getRecipient().getUserId().equals(user.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You are not authorized to modify this notification"));
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(ApiResponse.ok("Marked as read", null));
    }

    @GetMapping("/profile/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Authentication auth) {
        User user = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Profile data", UserResponse.fromEntity(user)));
    }

    @PostMapping("/profile/update-direct")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfileDirect(
            @RequestBody UpdateProfileDirectRequest req, Authentication auth) {
        User user = getUser(auth);
        User updated = authService.updateProfileDirect(user.getEmail(), req);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", UserResponse.fromEntity(updated)));
    }

    @PostMapping("/profile/photo")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfilePhoto(
            @RequestParam("photo") MultipartFile photo, Authentication auth) throws IOException {
        User user = getUser(auth);
        User updatedUser = authService.updateProfilePhoto(user.getEmail(), photo);
        return ResponseEntity.ok(ApiResponse.ok("Profile photo updated successfully", UserResponse.fromEntity(updatedUser)));
    }

    @GetMapping("/profile/pending-changes")
    public ResponseEntity<ApiResponse<List<PendingProfileChangeResponse>>> getPendingChanges(Authentication auth) {
        User user = getUser(auth);
        var changes = authService.getPendingChangesByUser(user.getEmail());
        var responses = changes.stream()
                .map(PendingProfileChangeResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok("Pending profile changes", responses));
    }

    private User getUser(Authentication auth) {
        String email = auth.getName();
        log.info("Looking up user by email: {}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new RuntimeException("User not found: " + email);
                });
    }
}
