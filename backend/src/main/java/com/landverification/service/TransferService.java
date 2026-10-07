package com.landverification.service;

import com.landverification.dto.ApproveRequest;
import com.landverification.dto.ApprovalWorkflowResponse;
import com.landverification.dto.BuyerSearchRequest;
import com.landverification.dto.BuyerSearchResponse;
import com.landverification.dto.DashboardStats;
import com.landverification.dto.FlagRequest;
import com.landverification.dto.InitiateTransferRequest;
import com.landverification.dto.ParcelRegistrationRequest;
import com.landverification.dto.RejectionRequest;
import com.landverification.dto.TransferResponse;
import com.landverification.dto.TransferStatusResponse;
import com.landverification.dto.DocumentResponse;
import com.landverification.model.*;
import com.landverification.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.landverification.config.AppProperties;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRequestRepository transferRepository;
    private final ApprovalWorkflowRepository approvalRepository;
    private final DocumentRepository documentRepository;
    private final LandParcelRepository parcelRepository;
    private final LandOwnershipRepository ownershipRepository;
    private final UserRepository userRepository;
    private final BlockchainService blockchainService;
    private final EmailService emailService;
    private final TitleDeedService titleDeedService;
    private final AppProperties appProperties;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;
    private final JurisdictionAccessService jurisdictionAccessService;
    private final Executor submissionTaskExecutor;

    // ============================================
    // EXISTING METHODS (Keep as they are)
    // ============================================

    @Transactional
    public TransferResponse initiateTransfer(InitiateTransferRequest req, Integer sellerUserId, List<MultipartFile> supportingFiles) {
        return initiateTransfer(req, sellerUserId, supportingFiles, null, null);
    }

    @Transactional
    public TransferResponse initiateTransfer(InitiateTransferRequest req, Integer sellerUserId,
                                             List<MultipartFile> supportingFiles,
                                             MultipartFile sellerPhoto, MultipartFile buyerPhoto) {
        LandParcel parcel = parcelRepository.findById(req.getParcelId())
                .orElseThrow(() -> new RuntimeException("Parcel not found"));

        if (!jurisdictionAccessService.isDemoParcel(parcel)) {
            throw new RuntimeException("Transfers are currently limited to Lusaka for this demonstration environment");
        }

        if (parcel.getStatus() == LandParcel.ParcelStatus.PENDING_TRANSFER) {
            throw new RuntimeException("A transfer is already pending for this parcel");
        }

        User seller = userRepository.findById(sellerUserId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        // ===== NEW ACCOUNT VERIFICATION LOGIC =====
        User buyer = null;
        boolean buyerIsNew = false;
        
        if (req.getBuyerHasExistingAccount() != null && req.getBuyerHasExistingAccount()) {
            // Buyer has an existing account - use the provided user ID or search
            if (req.getExistingBuyerUserId() != null) {
                buyer = userRepository.findById(req.getExistingBuyerUserId())
                        .orElseThrow(() -> new RuntimeException("Selected buyer account not found"));
                log.info("Using existing buyer account: {}", buyer.getUserId());
            } else {
                buyer = findExistingBuyerAccount(req);
            }
        } else if (req.getBuyerHasExistingAccount() != null && !req.getBuyerHasExistingAccount()) {
            // Buyer does NOT have an existing account - create new inactive account
            buyer = createInactiveNewBuyerAccount(req);
            buyerIsNew = true;
            log.info("Created new inactive buyer account: {}", buyer.getUserId());
        } else {
            // Legacy behavior: try to find existing, otherwise attempt to create
            buyer = findExistingBuyerAccount(req);
            if (buyer == null) {
                buyer = createInactiveNewBuyerAccount(req);
                buyerIsNew = true;
            }
        }

        LandOwnership current = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId())
                .orElseThrow(() -> new RuntimeException("No current ownership record found"));

        if (!current.getOwner().getUserId().equals(sellerUserId)) {
            throw new RuntimeException("You are not the current owner of this parcel");
        }

        String sellerPhotoPath = saveTransferPhoto(sellerPhoto, "seller");
        String buyerPhotoPath = saveTransferPhoto(buyerPhoto, "buyer");

        TransferRequest transfer = TransferRequest.builder()
                .parcel(parcel).seller(seller).buyer(buyer)
                .buyerFullName(req.getBuyerName())
                .buyerNationalId(req.getBuyerNid())
                .buyerEmail(req.getBuyerEmail())
                .buyerPhoneNumber(req.getBuyerPhone())
                .buyerAddress(req.getBuyerAddress())
                .buyerNationality(req.getBuyerNationality())
                .buyerIdType(req.getBuyerIdType())
                .transferDate(req.getTransferDate())
                .paymentTerms(req.getPaymentTerms())
                .transferReason(req.getTransferReason())
                .agreedPriceZmw(req.getAgreedPriceZmw())
                .status(TransferRequest.TransferStatus.SUBMITTED)
                .supportingDocs(req.getSupportingDocs())
                .ownerSignature(req.getOwnerSignature())
                .buyerSignature(req.getBuyerSignature())
                .sellerPhotoPath(sellerPhotoPath)
                .buyerPhotoPath(buyerPhotoPath)
                .build();
        transfer = transferRepository.save(transfer);
        persistSupportingDocuments(transfer, seller, supportingFiles, req.getSupportingDocs());

        parcel.setStatus(LandParcel.ParcelStatus.PENDING_TRANSFER);
        parcelRepository.save(parcel);

        final TransferRequest submittedTransfer = transfer;
        final User submittedBuyer = buyer;
        final boolean submittedBuyerIsNew = buyerIsNew;
        runAfterCommit(() -> processTransferSideEffects(submittedTransfer, parcel, req, sellerUserId,
                submittedBuyer, submittedBuyerIsNew));

        return toResponse(transfer, null);
    }

    private void processTransferSideEffects(TransferRequest transfer, LandParcel parcel, InitiateTransferRequest req,
                                            Integer sellerUserId, User buyer, boolean buyerIsNew) {
        try {
            try {
                String txHash = blockchainService.initiateTransfer(parcel.getParcelNumber(),
                        "0x0000000000000000000000000000000000000000", req.getTransferReason(),
                        req.getOwnerSignature(), parcel.getParcelId(), transfer.getRequestId(), sellerUserId);
                transfer.setBlockchainTxHash(txHash);
                transferRepository.save(transfer);
            } catch (Exception e) {
                log.warn("Blockchain initiateTransfer failed (DB saved): {}", e.getMessage());
            }

            notify(transfer.getSeller(), "Your parcel " + parcel.getParcelNumber()
                    + " has been sent for verification. You will receive status updates soon.",
                    Notification.NotificationType.TRANSFER_INITIATED, parcel.getParcelId(), transfer.getRequestId());

            String emailStatusMessage = null;
            // Send appropriate email to buyer based on account status.
            if (buyerIsNew) {
            // NEW BUYER: Send "waiting for approval" email
            try {
                String subject = "Land Verification System — Land Parcel Transfer Awaiting Approval";
                String body = String.format(
                        "Dear %s,\n\n" +
                        "A land parcel has been transferred to you and is currently awaiting approval by the Ministry of Lands.\n" +
                        "Parcel Number: %s\n\n" +
                        "Your account will be created automatically once the transfer has been fully approved.\n" +
                        "You will be notified with your login credentials once the process is complete.\n\n" +
                        "Thank you,\nLand Verification System Administration\n",
                        req.getBuyerName() != null && !req.getBuyerName().isBlank() ? req.getBuyerName() : "New Landowner",
                        parcel.getParcelNumber());
                emailService.sendEmailAsync(req.getBuyerEmail(), subject, body);
                emailStatusMessage = "Email queued";
                log.info("Queued 'waiting for approval' email to new buyer: {}", req.getBuyerEmail());
            } catch (Exception e) {
                log.warn("Failed to email new buyer at initiation {}: {}", req.getBuyerEmail(), e.getMessage());
            }
            } else {
            // EXISTING BUYER: Send dashboard notification and "waiting for approval" email
            if (buyer != null) {
                notify(buyer,
                        "A land transfer for parcel " + parcel.getParcelNumber() + " has been initiated in your favour and is now under verification. You will be notified once the approval process is completed.",
                        Notification.NotificationType.TRANSFER_INITIATED,
                        parcel.getParcelId(), transfer.getRequestId());
                try {
                    String subject = "Land Verification System — Land Parcel Transfer Awaiting Approval";
                    String body = String.format(
                            "Dear %s,\n\n" +
                            "A land parcel has been transferred to your account and is currently awaiting approval by the Ministry of Lands.\n" +
                            "Parcel Number: %s\n" +
                            "You will be notified once the approval process has been completed.\n\n" +
                            "Thank you,\nLand Verification System Administration\n",
                            buyer.getFullName() != null && !buyer.getFullName().isBlank() ? buyer.getFullName() : "Landowner",
                            parcel.getParcelNumber());
                    emailService.sendEmailAsync(buyer.getEmail(), subject, body);
                    emailStatusMessage = "Email queued";
                    log.info("Queued 'waiting for approval' email to existing buyer: {}", buyer.getEmail());
                } catch (Exception e) {
                    log.warn("Failed to email existing buyer at initiation {}: {}", buyer.getEmail(), e.getMessage());
                }
            }
            }

        // Notify all Land Officers about the new transfer pending their review
        try {
            List<User> landOfficers = userRepository.findByRole(User.Role.LAND_OFFICER);
            String officerNotificationMessage = String.format(
                    "A new land transfer for parcel %s — Request ID: %d has been submitted and is awaiting your review.",
                    parcel.getParcelNumber(), transfer.getRequestId());

            int notifiedCount = 0;
            for (User officer : landOfficers) {
                try {
                    // Only notify officers who have jurisdictional access to this parcel
                    if (officer != null && jurisdictionAccessService.canAccess(officer, parcel)) {
                        notify(officer, officerNotificationMessage,
                                Notification.NotificationType.TRANSFER_INITIATED,
                                parcel.getParcelId(), transfer.getRequestId());
                        notifiedCount++;

                        // Also send email notification to the officer
                        String subject = "Land Verification System — New Transfer Pending Review";
                        String body = String.format(
                                "Dear %s,\n\n" +
                                "A new land transfer for parcel %s has been submitted and is awaiting your review.\n" +
                                "Request ID: %d\n" +
                                "Parcel Number: %s\n\n" +
                                "Please log in to the system to review and process this transfer.\n\n" +
                                "Thank you,\nLand Verification System Administration\n",
                                officer.getFullName() != null && !officer.getFullName().isBlank() ? officer.getFullName() : "Officer",
                                parcel.getParcelNumber(), transfer.getRequestId(), parcel.getParcelNumber());

                        if (officer.getEmail() != null && !officer.getEmail().isBlank()) {
                            try {
                                emailService.sendEmailAsync(officer.getEmail(), subject, body);
                            } catch (Exception emailEx) {
                                log.warn("Failed to email officer {} about transfer {}: {}",
                                        officer.getUserId(), transfer.getRequestId(), emailEx.getMessage());
                            }
                        }
                    }
                } catch (Exception officerEx) {
                    log.warn("Failed to create notification for officer {} about transfer {}: {}",
                            officer != null ? officer.getUserId() : null, transfer.getRequestId(), officerEx.getMessage());
                }
            }
            log.info("Notified {} land officers about new transfer {}", notifiedCount, transfer.getRequestId());
            // If no land officers were found with jurisdiction access, escalate to senior officers in the parcel province
            if (notifiedCount == 0) {
                try {
                    List<User> seniorOfficers = userRepository.findByRole(User.Role.SENIOR_OFFICER).stream()
                            .filter(s -> s != null && jurisdictionAccessService.canAccess(s, parcel))
                            .toList();
                    int seniorNotified = 0;
                    String seniorMsg = String.format("A new land transfer for parcel %s — Request ID: %d has been submitted and requires senior officer review (no local land officers found).",
                            parcel.getParcelNumber(), transfer.getRequestId());
                    for (User senior : seniorOfficers) {
                        try {
                            notify(senior, seniorMsg, Notification.NotificationType.TRANSFER_INITIATED,
                                    parcel.getParcelId(), transfer.getRequestId());
                            seniorNotified++;
                        } catch (Exception ex) {
                            log.warn("Failed to notify senior officer {} about escalated transfer {}: {}",
                                    senior != null ? senior.getUserId() : null, transfer.getRequestId(), ex.getMessage());
                        }
                    }
                    log.info("Escalated and notified {} senior officers about transfer {}", seniorNotified, transfer.getRequestId());
                } catch (Exception e) {
                    log.warn("Failed to escalate notification to senior officers for transfer {}: {}", transfer.getRequestId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to notify land officers about new transfer {}: {}", transfer.getRequestId(), e.getMessage());
        }

        } catch (Exception e) {
            log.warn("Post-transfer processing failed for request {}: {}", transfer.getRequestId(), e.getMessage());
        }
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submissionTaskExecutor.execute(task); }
            });
        } else {
            submissionTaskExecutor.execute(task);
        }
    }

    @Transactional
    public TransferResponse landOfficerApprove(ApproveRequest req, Integer officerUserId) {
        TransferRequest transfer = transferRepository.findById(req.getRequestId().intValue())
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        User officer = userRepository.findById(officerUserId)
            .orElseThrow(() -> new RuntimeException("Officer not found"));
        enforceJurisdictionAccess(officer, transfer.getParcel());

        if (transfer.getStatus() != TransferRequest.TransferStatus.SUBMITTED
                && transfer.getStatus() != TransferRequest.TransferStatus.UNDER_REVIEW
                && transfer.getStatus() != TransferRequest.TransferStatus.FLAGGED) {
            throw new RuntimeException("Transfer is not awaiting Land Officer approval");
        }

        ensureNoMismatchBeforeApproval(transfer);

        approvalRepository.save(ApprovalWorkflow.builder()
                .request(transfer).officer(officer).approvalLevel(1)
                .action(ApprovalWorkflow.ApprovalAction.APPROVED)
                .comments(req.getComments())
                .digitalSignature(req.getDigitalSignature())
                .build());

        if (transfer.isFlagged()) {
            transfer.setFlagged(false);
            transfer.setFlagReason(null);
            transfer.setFlaggedAt(null);
            transfer.setFlaggedBy(null);
        }

        // ─── Mark as APPROVED_L1 (Land Officer Level 1) ───
        transfer.setStatus(TransferRequest.TransferStatus.APPROVED_L1);
        transferRepository.save(transfer);

        if (hasBlockchainSignature(req)) {
            try {
                blockchainService.seniorOfficerApprove(
                        BigInteger.valueOf(transfer.getRequestId()),
                        req.getSignerAddress(), req.getDigitalSignature(),
                        transfer.getParcel().getParcelId(), transfer.getRequestId(), officerUserId);
            } catch (Exception e) {
                log.warn("Blockchain L1 approve failed: {}", e.getMessage());
            }
        }

        notify(transfer.getSeller(),
                "Transfer request for parcel " + transfer.getParcel().getParcelNumber()
                        + " has been approved by the Land Officer. It is now pending Senior Officer approval.",
                Notification.NotificationType.TRANSFER_APPROVED_L1,
                transfer.getParcel().getParcelId(), transfer.getRequestId());

        // Also notify the approving officer
        notify(officer,
                "You have successfully approved the transfer request for parcel " + transfer.getParcel().getParcelNumber() + ".",
                Notification.NotificationType.TRANSFER_APPROVED_L1,
                transfer.getParcel().getParcelId(), transfer.getRequestId());

        notifySeniorOfficersOfLevelOneApproval(transfer);
        return toResponse(transfer);
    }

    @Transactional
    public TransferResponse seniorOfficerApprove(ApproveRequest req, Integer officerUserId) {
        TransferRequest transfer = transferRepository.findById(req.getRequestId().intValue())
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        User officer = userRepository.findById(officerUserId)
            .orElseThrow(() -> new RuntimeException("Officer not found"));
        enforceJurisdictionAccess(officer, transfer.getParcel());

        if (transfer.getStatus() != TransferRequest.TransferStatus.APPROVED_L1) {
            throw new RuntimeException("Transfer can only be reviewed by the Senior Officer after the Land Officer has approved it");
        }

        ensureNoMismatchBeforeApproval(transfer);

        ApprovalWorkflow savedApproval = approvalRepository.save(ApprovalWorkflow.builder()
            .request(transfer).officer(officer).approvalLevel(2)
            .action(ApprovalWorkflow.ApprovalAction.APPROVED)
            .comments(req.getComments())
            .digitalSignature(req.getDigitalSignature())
            .build());

        // Immediately notify the responsible Level-1 Land Officer (if any)
        try {
            approvalRepository.findByRequest_RequestId(transfer.getRequestId())
                .stream()
                .filter(a -> a.getApprovalLevel() != null && a.getApprovalLevel() == 1)
                .findFirst()
                .ifPresent(l1 -> {
                User l1Officer = l1.getOfficer();
                if (l1Officer != null) {
                    String decision = savedApproval.getAction() != null
                        ? savedApproval.getAction().name() : "APPROVED";
                    String comments = savedApproval.getComments() != null ? savedApproval.getComments() : "";
                    String when = savedApproval.getActionedAt() != null ? savedApproval.getActionedAt().toString() : java.time.LocalDateTime.now().toString();
                    String msg = String.format("Senior Officer %s %s transfer request %d for parcel %s at %s. Comments: %s",
                        officer.getFullName() != null ? officer.getFullName() : officer.getEmail(),
                        decision, transfer.getRequestId(),
                        transfer.getParcel() != null ? transfer.getParcel().getParcelNumber() : "N/A",
                        when, comments);
                    notify(l1Officer, msg,
                        Notification.NotificationType.TRANSFER_APPROVED_L2,
                        transfer.getParcel() != null ? transfer.getParcel().getParcelId() : null,
                        transfer.getRequestId());
                }
                });
        } catch (Exception e) {
            log.warn("Failed to notify Level-1 officer about senior approval: {}", e.getMessage());
        }

        if (transfer.isFlagged()) {
            transfer.setFlagged(false);
            transfer.setFlagReason(null);
            transfer.setFlaggedAt(null);
            transfer.setFlaggedBy(null);
        }

        BuyerResolution buyerResolution = resolveOrCreateBuyerAccount(transfer);
        User buyer = buyerResolution.buyer();

        LandOwnership currentOwnership = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(transfer.getParcel().getParcelId())
                .orElseThrow(() -> new RuntimeException("Current ownership not found"));
        currentOwnership.setIsCurrent(false);
        currentOwnership.setEndDate(LocalDate.now());
        ownershipRepository.save(currentOwnership);

        ownershipRepository.save(LandOwnership.builder()
                .parcel(transfer.getParcel()).owner(buyer)
                .ownershipType(LandOwnership.OwnershipType.SOLE)
                .acquisitionMethod(LandOwnership.AcquisitionMethod.PURCHASE)
                .startDate(LocalDate.now()).isCurrent(true).build());
        transfer.getParcel().setOwnerPhotoPath(transfer.getBuyerPhotoPath());
        blockchainService.recordOwnershipChange(transfer.getParcel().getParcelNumber(),
                transfer.getParcel().getParcelId(), transfer.getRequestId(), officerUserId);

        LandParcel parcel = transfer.getParcel();
        parcel.setStatus(LandParcel.ParcelStatus.ACTIVE);
        parcelRepository.save(parcel);

        transfer.setBuyer(buyer);
        transfer.setStatus(TransferRequest.TransferStatus.APPROVED);
        transferRepository.save(transfer);

        if (hasBlockchainSignature(req)) {
            try {
                blockchainService.levelTwoApprove(
                        BigInteger.valueOf(transfer.getRequestId()),
                        req.getSignerAddress(), req.getDigitalSignature(),
                        parcel.getParcelId(), transfer.getRequestId(), officerUserId);
            } catch (Exception e) {
                log.warn("Blockchain final approve failed but database updated: {}", e.getMessage());
            }
        }

        try {
            syncBuyerDetailsFromTransfer(transfer, buyer);
            regenerateTitleDeedForTransfer(parcel, buyer);
        } catch (Exception e) {
            log.warn("Failed to regenerate title deed for parcel {} after transfer {}: {}",
                    parcel.getParcelNumber(), transfer.getRequestId(), e.getMessage());
        }

        if (buyerResolution.created()) {
            sendBuyerNewAccountCredentialsEmail(buyer, transfer, buyerResolution.tempPassword());
        } else {
            sendBuyerExistingAccountNotificationEmail(buyer, transfer);
        }

        if (buyer != null) {
            notify(buyer,
                    "Ownership of parcel " + parcel.getParcelNumber() + " has been transferred to you.",
                    Notification.NotificationType.TRANSFER_APPROVED_FINAL,
                    parcel.getParcelId(), transfer.getRequestId());
        }
        notify(transfer.getSeller(),
                "Ownership transfer of parcel " + parcel.getParcelNumber() + " has been completed.",
                Notification.NotificationType.TRANSFER_APPROVED_FINAL,
                parcel.getParcelId(), transfer.getRequestId());

        return toResponse(transfer);
    }

    @Transactional
    public TransferResponse rejectTransfer(RejectionRequest req, Integer officerUserId) {
        TransferRequest transfer = transferRepository.findById(req.getRequestId())
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        User officer = userRepository.findById(officerUserId)
            .orElseThrow(() -> new RuntimeException("Officer not found"));
        enforceJurisdictionAccess(officer, transfer.getParcel());
        if (officer.getRole() == User.Role.SENIOR_OFFICER && !isSeniorEligible(transfer)) {
            throw new RuntimeException("Senior Officer actions require Land Officer approval first");
        }

        if (transfer.getStatus() == TransferRequest.TransferStatus.APPROVED
                || transfer.getStatus() == TransferRequest.TransferStatus.REJECTED
                || transfer.getStatus() == TransferRequest.TransferStatus.CANCELLED) {
            throw new RuntimeException("Transfer cannot be rejected in its current state");
        }

        ApprovalWorkflow savedRejection = approvalRepository.save(ApprovalWorkflow.builder()
                .request(transfer).officer(officer)
                .approvalLevel(transfer.getStatus() == TransferRequest.TransferStatus.APPROVED_L1 ? 2 : 1)
                .action(ApprovalWorkflow.ApprovalAction.REJECTED)
                .comments(req.getReason())
                .digitalSignature(req.getDigitalSignature())
                .build());

        // If rejection was performed by a Senior Officer (level 2), notify the Level-1 officer
        try {
            if (savedRejection.getApprovalLevel() != null && savedRejection.getApprovalLevel() == 2) {
                approvalRepository.findByRequest_RequestId(transfer.getRequestId())
                        .stream()
                        .filter(a -> a.getApprovalLevel() != null && a.getApprovalLevel() == 1)
                        .findFirst()
                        .ifPresent(l1 -> {
                            User l1Officer = l1.getOfficer();
                            if (l1Officer != null) {
                                String when = savedRejection.getActionedAt() != null ? savedRejection.getActionedAt().toString() : java.time.LocalDateTime.now().toString();
                                String comments = savedRejection.getComments() != null ? savedRejection.getComments() : "";
                                String msg = String.format("Senior Officer %s REJECTED transfer request %d for parcel %s at %s. Reason: %s",
                                        officer.getFullName() != null ? officer.getFullName() : officer.getEmail(),
                                        transfer.getRequestId(),
                                        transfer.getParcel() != null ? transfer.getParcel().getParcelNumber() : "N/A",
                                        when, comments);
                                notify(l1Officer, msg,
                                        Notification.NotificationType.TRANSFER_REJECTED,
                                        transfer.getParcel() != null ? transfer.getParcel().getParcelId() : null,
                                        transfer.getRequestId());
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to notify Level-1 officer about senior rejection: {}", e.getMessage());
        }

        if (transfer.isFlagged()) {
            transfer.setFlagged(false);
            transfer.setFlagReason(null);
            transfer.setFlaggedAt(null);
        }

        transfer.setStatus(TransferRequest.TransferStatus.REJECTED);
        transferRepository.save(transfer);

        LandParcel parcel = transfer.getParcel();
        parcel.setStatus(LandParcel.ParcelStatus.ACTIVE);
        parcelRepository.save(parcel);

        try {
            blockchainService.rejectTransfer(
                    BigInteger.valueOf(transfer.getRequestId()), req.getReason(),
                    parcel.getParcelId(), transfer.getRequestId(), officerUserId);
        } catch (Exception e) {
            log.warn("Blockchain reject failed: {}", e.getMessage());
        }

        notify(transfer.getSeller(),
                "Your transfer request for parcel " + parcel.getParcelNumber()
                        + " has been rejected. Reason: " + req.getReason(),
                Notification.NotificationType.TRANSFER_REJECTED,
                parcel.getParcelId(), transfer.getRequestId());

        String rejectionMessage = "Transfer request for parcel " + parcel.getParcelNumber()
            + " was rejected by a Senior Officer. Reason: " + req.getReason();
        for (User landOfficer : userRepository.findByRole(User.Role.LAND_OFFICER)) {
            if (landOfficer != null && !landOfficer.equals(officer)
                && jurisdictionAccessService.canAccess(landOfficer, parcel)) {
            notify(landOfficer, rejectionMessage,
                Notification.NotificationType.TRANSFER_REJECTED,
                parcel.getParcelId(), transfer.getRequestId());
            }
        }

        if (transfer.getSeller() != null && transfer.getSeller().getEmail() != null
            && !transfer.getSeller().getEmail().isBlank()) {
            try {
            emailService.sendTransferRejectedNotification(
                transfer.getSeller().getEmail(), transfer.getSeller().getFullName(),
                parcel.getParcelNumber(), req.getReason(), parcel.getLocationAddress());
            } catch (Exception e) {
            log.warn("Failed to email landowner about rejected parcel {}: {}",
                parcel.getParcelNumber(), e.getMessage());
            }
        } else {
            log.warn("Cannot email landowner about rejected parcel {} because the seller email is missing",
                parcel.getParcelNumber());
        }

        return toResponse(transfer);
    }

    @Transactional
    public TransferResponse flagTransfer(FlagRequest req, Integer officerUserId) {
        TransferRequest transfer = transferRepository.findById(req.getRequestId())
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        User flaggingOfficer = userRepository.findById(officerUserId)
            .orElseThrow(() -> new RuntimeException("Officer not found"));
        enforceJurisdictionAccess(flaggingOfficer, transfer.getParcel());
        if (flaggingOfficer.getRole() == User.Role.SENIOR_OFFICER && !isSeniorEligible(transfer)) {
            throw new RuntimeException("Senior Officer actions require Land Officer approval first");
        }

        if (transfer.getStatus() == TransferRequest.TransferStatus.APPROVED
                || transfer.getStatus() == TransferRequest.TransferStatus.REJECTED
                || transfer.getStatus() == TransferRequest.TransferStatus.CANCELLED) {
            throw new RuntimeException("Transfer cannot be flagged in its current state");
        }

        transfer.setStatus(TransferRequest.TransferStatus.FLAGGED);
        transfer.setFlagged(true);
        transfer.setFlagReason(req.getReason());
        transfer.setFlaggedAt(java.time.LocalDateTime.now());
        transfer.setFlaggedBy(flaggingOfficer);
        transferRepository.save(transfer);

        LandParcel parcel = transfer.getParcel();
        parcel.setStatus(LandParcel.ParcelStatus.DISPUTED);
        parcelRepository.save(parcel);

        notify(flaggingOfficer,
            "Parcel " + parcel.getParcelNumber() + " has been flagged for review. Reason: " + req.getReason(),
            Notification.NotificationType.TRANSFER_FLAGGED,
            parcel.getParcelId(), transfer.getRequestId());

        List<User> landOfficers = userRepository.findByRole(User.Role.LAND_OFFICER);
        for (User landOfficer : landOfficers) {
            if (landOfficer != null && !landOfficer.equals(flaggingOfficer)
                && jurisdictionAccessService.canAccess(landOfficer, parcel)) {
            notify(landOfficer,
                "Parcel " + parcel.getParcelNumber() + " has been flagged for review by a Senior Officer. Reason: " + req.getReason(),
                Notification.NotificationType.TRANSFER_FLAGGED,
                parcel.getParcelId(), transfer.getRequestId());
            }
        }

        notify(transfer.getSeller(),
                "Parcel " + parcel.getParcelNumber() + " has been flagged for review. Please address the issue and follow the required next steps.",
                Notification.NotificationType.TRANSFER_FLAGGED,
                parcel.getParcelId(), transfer.getRequestId());

        if (transfer.getBuyer() != null) {
            notify(transfer.getBuyer(),
                    "Parcel " + parcel.getParcelNumber() + " has been flagged for review. Please review the issue and follow the required next steps.",
                    Notification.NotificationType.TRANSFER_FLAGGED,
                    parcel.getParcelId(), transfer.getRequestId());
        }

        String buyerEmail = getBuyerEmail(transfer);
        if (buyerEmail != null && !buyerEmail.isBlank()) {
            try {
                emailService.sendFlaggedParcelNotification(
                        buyerEmail,
                        transfer.getBuyer() != null ? transfer.getBuyer().getFullName() : transfer.getBuyerFullName(),
                        parcel.getParcelNumber(),
                        req.getReason(),
                        parcel.getLocationAddress(),
                        "Resolve the issue, provide any missing documentation, and wait for the reviewing authority to verify the parcel before approval can continue."
                );
            } catch (Exception e) {
                log.warn("Failed to email buyer about flagged parcel {}: {}", parcel.getParcelNumber(), e.getMessage());
            }
        }

        if (transfer.getSeller() != null && transfer.getSeller().getEmail() != null && !transfer.getSeller().getEmail().isBlank()) {
            try {
                emailService.sendFlaggedParcelNotification(
                        transfer.getSeller().getEmail(),
                        transfer.getSeller().getFullName(),
                        parcel.getParcelNumber(),
                        req.getReason(),
                        parcel.getLocationAddress(),
                        "Resolve the issue, provide any missing documentation, and wait for the reviewing authority to verify the parcel before approval can continue."
                );
            } catch (Exception e) {
                log.warn("Failed to email landowner about flagged parcel {}: {}", parcel.getParcelNumber(), e.getMessage());
            }
        } else {
            log.warn("Cannot email landowner about flagged parcel {} because the seller email is missing", parcel.getParcelNumber());
        }

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getPendingTransfers() {
        return transferRepository.findByStatus(TransferRequest.TransferStatus.SUBMITTED)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getSeniorOfficerTransfers(User officer) {
        // Include recently submitted and under-review transfers so senior officers can act on new requests
        List<TransferRequest.TransferStatus> statuses = List.of(
            TransferRequest.TransferStatus.APPROVED_L1,
            TransferRequest.TransferStatus.APPROVED_L2,
            TransferRequest.TransferStatus.APPROVED,
            TransferRequest.TransferStatus.FLAGGED,
            TransferRequest.TransferStatus.REJECTED,
            TransferRequest.TransferStatus.CANCELLED
        );
        List<TransferRequest> seniorQueue = new ArrayList<>(transferRepository.findByStatusIn(statuses));
        transferRepository.findByFlaggedTrue().stream()
            .filter(transfer -> seniorQueue.stream().noneMatch(existing ->
                Objects.equals(existing.getRequestId(), transfer.getRequestId())))
            .forEach(seniorQueue::add);

        return seniorQueue
                .stream()
                .filter(transfer -> transfer.getParcel() != null)
                .filter(this::isSeniorEligible)
                .filter(transfer -> {
                    if (officer == null) return true;
                    boolean access = jurisdictionAccessService.canAccess(officer, transfer.getParcel());
                    if (!access) {
                        try {
                            String oEmail = officer.getEmail();
                            String oProv = officer.getProvince();
                            String oDist = officer.getDistrict();
                            var p = transfer.getParcel();
                            String pProv = p != null ? p.getProvince() : null;
                            String pDist = p != null ? p.getDistrict() : null;
                            log.debug("SeniorOfficer {} excluded from transfer {} due to jurisdiction mismatch: officer(province={},district={}) vs parcel(province={},district={})",
                                    oEmail, transfer.getRequestId(), oProv, oDist, pProv, pDist);
                        } catch (Exception e) {
                            // swallow logging exception
                        }
                    }
                    return access;
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfersByStatus(TransferRequest.TransferStatus status, User officer) {
        if (status == null) {
            return transferRepository.findAll().stream()
                    .filter(transfer -> transfer.getParcel() != null)
                    .filter(transfer -> officer == null || jurisdictionAccessService.canAccess(officer, transfer.getParcel()))
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
        return transferRepository.findByStatus(status)
                .stream()
                .filter(transfer -> transfer.getParcel() != null)
            .filter(transfer -> officer == null || officer.getRole() != User.Role.SENIOR_OFFICER
                || isSeniorEligible(transfer))
                .filter(transfer -> officer == null || jurisdictionAccessService.canAccess(officer, transfer.getParcel()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getFlaggedTransfers(User officer) {
        return transferRepository.findByFlaggedTrueOrStatusIn(
                        List.of(TransferRequest.TransferStatus.FLAGGED))
                .stream()
                .filter(transfer -> transfer.getParcel() != null)
            .filter(this::isSeniorEligible)
                .filter(transfer -> officer == null || jurisdictionAccessService.canAccess(officer, transfer.getParcel()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getTransfersByUser(Integer userId) {
        List<TransferRequest> all = transferRepository.findBySeller_UserId(userId);
        all.addAll(transferRepository.findByBuyer_UserId(userId));
        return all.stream().map(this::toResponse).collect(Collectors.toList());
    }
    public TransferStatusResponse getTransferStatus(Integer requestId) {
        TransferRequest transfer = transferRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));

        LandOwnership currentOwnership = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(transfer.getParcel().getParcelId()).orElse(null);

        List<String> mismatchReasons = computeTransferMismatchReasons(transfer, currentOwnership);
        if (!mismatchReasons.isEmpty()) {
            createMismatchNotificationsIfNeeded(transfer, mismatchReasons);
        }

        String currentOwnerName = currentOwnership != null && currentOwnership.getOwner() != null
                ? currentOwnership.getOwner().getFullName() : "Unknown";
        String currentOwnerMaskedId = currentOwnership != null && currentOwnership.getOwner() != null
                ? maskNationalId(currentOwnership.getOwner().getNationalId()) : "Unknown";

        return TransferStatusResponse.builder()
                .requestId(transfer.getRequestId())
                .parcelNumber(transfer.getParcel().getParcelNumber())
                .seller(transfer.getSeller().getFullName())
                .sellerName(transfer.getSeller().getFullName())
                .buyer(getBuyerDisplayName(transfer))
                .buyerName(getBuyerDisplayName(transfer))
                .agreedPriceZmw(transfer.getAgreedPriceZmw() != null ? transfer.getAgreedPriceZmw().doubleValue() : null)
                .status(transfer.getStatus().toString())
                .transferReason(transfer.getTransferReason())
                .supportingDocs(transfer.getSupportingDocs())
                .supportingDocuments(getSupportingDocuments(transfer))
                .submittedAt(transfer.getSubmittedAt() != null ? transfer.getSubmittedAt().toString() : "")
                .createdAt(transfer.getSubmittedAt() != null ? transfer.getSubmittedAt().toString() : "")
                .price(transfer.getAgreedPriceZmw() != null ? transfer.getAgreedPriceZmw().doubleValue() : null)
                .reason(transfer.getTransferReason())
                .documents(transfer.getSupportingDocs())
                .parcelTitleDeedNumber(transfer.getParcel().getTitleDeedNumber())
                .parcelProvince(transfer.getParcel().getProvince())
                .parcelLocationAddress(transfer.getParcel().getLocationAddress())
                .parcelLandUse(transfer.getParcel().getLandUse() != null ? transfer.getParcel().getLandUse().name() : null)
                .parcelAreaSqm(transfer.getParcel().getAreaSqm() != null ? transfer.getParcel().getAreaSqm().doubleValue() : null)
                .parcelRegisteredAt(transfer.getParcel().getRegisteredAt() != null ? transfer.getParcel().getRegisteredAt().toString() : "")
                .parcelStatus(transfer.getParcel().getStatus() != null ? transfer.getParcel().getStatus().name() : null)
                .documentHash(transfer.getParcel().getDocumentHash())
                .ownerAuthenticated(transfer.getOwnerSignature() != null && !transfer.getOwnerSignature().isBlank())
                .buyerAuthenticated(transfer.getBuyerSignature() != null && !transfer.getBuyerSignature().isBlank())
                .currentOwnerName(currentOwnerName)
                .currentOwnerMaskedId(currentOwnerMaskedId)
                .mismatchDetected(!mismatchReasons.isEmpty())
                .mismatchReasons(mismatchReasons)
                .build();
    }

    private List<String> computeTransferMismatchReasons(TransferRequest transfer, LandOwnership currentOwnership) {
        List<String> reasons = new java.util.ArrayList<>();
        if (currentOwnership == null || currentOwnership.getOwner() == null) {
            reasons.add("The registered parcel does not have a current owner record.");
            return reasons;
        }

        if (!currentOwnership.getOwner().getUserId().equals(transfer.getSeller().getUserId())) {
            reasons.add("The transfer seller does not match the current registered owner.");
        }

        if (transfer.getBuyer() != null && currentOwnership.getOwner().getUserId().equals(transfer.getBuyer().getUserId())) {
            reasons.add("The buyer is already listed as the current parcel owner.");
        }

        if (transfer.getParcel().getParcelId() == null || transfer.getParcel().getParcelNumber() == null) {
            reasons.add("The transfer request references an invalid parcel record.");
        }

        if (transfer.getParcel().getStatus() == null) {
            reasons.add("The registered parcel has no status set.");
        }

        return reasons;
    }

    private void createMismatchNotificationsIfNeeded(TransferRequest transfer, List<String> mismatchReasons) {
        if (notificationRepository.existsByRelatedRequestIdAndNotificationType(
                transfer.getRequestId(), Notification.NotificationType.TRANSFER_MISMATCH_DETECTED)) {
            return;
        }

        String message = String.format(
                "Transfer request %d for parcel %s has inconsistencies with the registered land record: %s",
                transfer.getRequestId(), transfer.getParcel().getParcelNumber(), String.join(" ", mismatchReasons));

        List<User> recipients = new java.util.ArrayList<>();
        recipients.addAll(userRepository.findByRole(User.Role.LAND_OFFICER));
        recipients.addAll(userRepository.findByRole(User.Role.SENIOR_OFFICER));

        for (User recipient : recipients) {
            try {
                notificationRepository.save(Notification.builder()
                        .recipient(recipient)
                        .message(message)
                        .notificationType(Notification.NotificationType.TRANSFER_MISMATCH_DETECTED)
                        .relatedParcelId(transfer.getParcel().getParcelId())
                        .relatedRequestId(transfer.getRequestId())
                        .relatedUserId(recipient.getUserId())
                        .isRead(false)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to create mismatch notification for user {}: {}",
                        recipient != null ? recipient.getUserId() : null, e.getMessage());
            }
        }
    }

    private void ensureNoMismatchBeforeApproval(TransferRequest transfer) {
        LandOwnership currentOwnership = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(transfer.getParcel().getParcelId()).orElse(null);
        List<String> mismatchReasons = computeTransferMismatchReasons(transfer, currentOwnership);
        if (!mismatchReasons.isEmpty()) {
            throw new RuntimeException("Cannot approve transfer request until data mismatches are resolved: "
                    + String.join("; ", mismatchReasons));
        }
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return "Unknown";
        }
        String cleaned = nationalId.trim();
        if (cleaned.length() <= 4) {
            return "****";
        }
        return cleaned.substring(0, 2) + "****" + cleaned.substring(cleaned.length() - 2);
    }
    // ============================================
    // NEW METHODS FOR HTML DASHBOARD
    // ============================================

    /**
     * Get pending transfers for Level 1 officer dashboard
     * Returns transfers with status SUBMITTED or UNDER_REVIEW
     */
    @Transactional(readOnly = true)
    public List<TransferResponse> getPendingTransfersForOfficer(Integer officerUserId) {
        User officer = userRepository.findById(officerUserId).orElse(null);
        List<TransferRequest> transfers = transferRepository.findByStatusIn(
            List.of(
                TransferRequest.TransferStatus.SUBMITTED,
                TransferRequest.TransferStatus.UNDER_REVIEW
            )
        );
        return transfers.stream()
                .filter(transfer -> transfer.getParcel() != null)
                .filter(transfer -> officer == null || jurisdictionAccessService.canAccess(officer, transfer.getParcel()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Approve transfer at Level 1 (for Land Officer)
     * This is what the dashboard's "Approve" button calls
     */
    @Transactional
    public TransferResponse approveTransferLevel1(Integer requestId, Integer officerUserId, String notes) {
        TransferRequest transfer = transferRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        User officer = userRepository.findById(officerUserId)
            .orElseThrow(() -> new RuntimeException("Officer not found"));
        enforceJurisdictionAccess(officer, transfer.getParcel());
        
        // Check if transfer can be approved
        if (transfer.getStatus() != TransferRequest.TransferStatus.SUBMITTED 
                && transfer.getStatus() != TransferRequest.TransferStatus.UNDER_REVIEW) {
            throw new RuntimeException("Transfer cannot be approved in current status: " + transfer.getStatus());
        }
        
        // Save approval record
        ApprovalWorkflow approval = ApprovalWorkflow.builder()
                .request(transfer)
                .officer(officer)
                .approvalLevel(1)
                .action(ApprovalWorkflow.ApprovalAction.APPROVED)
                .comments(notes != null ? notes : "Approved by Level 1 Officer")
                .build();
        approvalRepository.save(approval);
        
        // Update transfer status to APPROVED_L1
        transfer.setStatus(TransferRequest.TransferStatus.APPROVED_L1);
        transferRepository.save(transfer);
        
        // Notify seller and buyer
        String message = String.format("Your transfer request for parcel %s has been approved at Level 1. Awaiting Level 2 approval.",
            transfer.getParcel().getParcelNumber());
        
        notify(transfer.getSeller(), message,
            Notification.NotificationType.TRANSFER_APPROVED_L1,
            transfer.getParcel().getParcelId(), transfer.getRequestId());
        
        notify(transfer.getBuyer(), message,
            Notification.NotificationType.TRANSFER_APPROVED_L1,
            transfer.getParcel().getParcelId(), transfer.getRequestId());

        notifySeniorOfficersOfLevelOneApproval(transfer);
        
        return toResponse(transfer);
    }

    /**
     * Get dashboard statistics for the officer dashboard cards
     */
    public DashboardStats getDashboardStats(Integer officerUserId) {
        // Scope counts to transfers the requesting officer can access so the
        // dashboard numbers match the transfers shown in the officer UI.
        User officer = userRepository.findById(officerUserId).orElse(null);

        List<TransferRequest> pendingList = transferRepository.findByStatusIn(
            List.of(
                TransferRequest.TransferStatus.SUBMITTED,
                TransferRequest.TransferStatus.UNDER_REVIEW
            )
        );
        long pendingReviews = pendingList.stream()
                .filter(t -> t.getParcel() != null)
                .filter(t -> officer == null || jurisdictionAccessService.canAccess(officer, t.getParcel()))
                .count();

        long approvedL1 = transferRepository.findByStatus(TransferRequest.TransferStatus.APPROVED_L1)
                .stream()
                .filter(t -> t.getParcel() != null)
                .filter(t -> officer == null || jurisdictionAccessService.canAccess(officer, t.getParcel()))
                .count();
        
        // Count approvals BY THIS OFFICER this month using ApprovalWorkflow
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        List<ApprovalWorkflow> thisMonthApprovals = approvalRepository.findByOfficer_UserId(officerUserId);
        long approvedThisMonth = thisMonthApprovals.stream()
            .filter(a -> a.getActionedAt() != null && a.getActionedAt().isAfter(startOfMonth.atStartOfDay()))
            .filter(a -> a.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED)
            .count();
        
        // Count rejections BY THIS OFFICER this month
        long rejectedThisMonth = thisMonthApprovals.stream()
            .filter(a -> a.getActionedAt() != null && a.getActionedAt().isAfter(startOfMonth.atStartOfDay()))
            .filter(a -> a.getAction() == ApprovalWorkflow.ApprovalAction.REJECTED)
            .count();
        
        // Count total parcels
        long totalParcels = parcelRepository.count();
        
        return DashboardStats.builder()
                .pendingReviews(pendingReviews)
                .approvedL1(approvedL1)
                .approvedThisMonth(approvedThisMonth)
                .rejectedThisMonth(rejectedThisMonth)
                .totalParcels(totalParcels)
                .build();
    }

    /**
     * Get a single transfer by ID with full details
     */
    public TransferResponse getTransferById(Integer requestId) {
        TransferRequest transfer = transferRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        return toResponse(transfer);
    }

    /**
     * Get transfers with filtering options (for the transfers table with filters)
     */
    public List<TransferResponse> getFilteredTransfers(String status, String search, LocalDate fromDate, LocalDate toDate) {
        List<TransferRequest> transfers;
        
        if (status != null && !status.isEmpty()) {
            TransferRequest.TransferStatus filterStatus = TransferRequest.TransferStatus.valueOf(status);
            transfers = transferRepository.findByStatus(filterStatus);
        } else {
            transfers = transferRepository.findAll();
        }
        
        // Apply search filter (parcel number, seller name, buyer name)
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            transfers = transfers.stream()
                    .filter(t -> t.getParcel().getParcelNumber().toLowerCase().contains(searchLower) ||
                                t.getSeller().getFullName().toLowerCase().contains(searchLower) ||
                                t.getBuyer().getFullName().toLowerCase().contains(searchLower))
                    .collect(Collectors.toList());
        }
        
        // Apply date filters
        if (fromDate != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getSubmittedAt().toLocalDate().isAfter(fromDate.minusDays(1)))
                    .collect(Collectors.toList());
        }
        
        if (toDate != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getSubmittedAt().toLocalDate().isBefore(toDate.plusDays(1)))
                    .collect(Collectors.toList());
        }
        
        return transfers.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private boolean hasBlockchainSignature(ApproveRequest request) {
        return request != null
                && request.getSignerAddress() != null && !request.getSignerAddress().isBlank()
                && request.getDigitalSignature() != null && !request.getDigitalSignature().isBlank();
    }

    private void notifySeniorOfficersOfLevelOneApproval(TransferRequest transfer) {
        try {
            List<User> seniorOfficers = userRepository.findByRole(User.Role.SENIOR_OFFICER).stream()
                    .filter(senior -> senior != null && jurisdictionAccessService.canAccess(senior, transfer.getParcel()))
                    .toList();
            for (User senior : seniorOfficers) {
                notify(senior,
                        "Transfer request " + transfer.getRequestId() + " for parcel "
                                + transfer.getParcel().getParcelNumber() + " is awaiting your final approval.",
                        Notification.NotificationType.TRANSFER_APPROVED_L1,
                        transfer.getParcel().getParcelId(), transfer.getRequestId());
            }
        } catch (Exception e) {
            log.warn("Failed to notify senior officers after L1 approval for transfer {}: {}",
                    transfer.getRequestId(), e.getMessage());
        }
    }

    private void enforceJurisdictionAccess(User officer, LandParcel parcel) {
        if (officer == null || !jurisdictionAccessService.canAccess(officer, parcel)) {
            throw new RuntimeException("You are not authorized to manage records outside your assigned province");
        }
    }

    private boolean isSeniorEligible(TransferRequest transfer) {
        if (transfer == null || transfer.getParcel() == null) {
            return false;
        }
        if (transfer.getStatus() == TransferRequest.TransferStatus.APPROVED_L1
                || transfer.getStatus() == TransferRequest.TransferStatus.APPROVED_L2
                || transfer.getStatus() == TransferRequest.TransferStatus.APPROVED) {
            return true;
        }
        return approvalRepository.findByRequest_RequestId(transfer.getRequestId()).stream()
                .anyMatch(approval -> approval.getApprovalLevel() != null
                        && approval.getApprovalLevel() == 1
                        && approval.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED);
    }

    private void notify(User recipient, String message,
                        Notification.NotificationType type,
                        Integer parcelId, Integer requestId) {
        if (recipient == null) {
            log.warn("Notification skipped because recipient is null for request {}", requestId);
            return;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .recipient(recipient).message(message)
                    .notificationType(type)
                    .relatedParcelId(parcelId).relatedRequestId(requestId)
                    .isRead(false).build());
        } catch (Exception e) {
            log.warn("Failed to send notification: {}", e.getMessage());
        }
    }

    private User findExistingBuyerAccount(InitiateTransferRequest req) {
        if (req.getBuyerNid() != null && !req.getBuyerNid().isBlank()) {
            return userRepository.findByNationalId(req.getBuyerNid()).orElse(null);
        }
        if (req.getBuyerEmail() != null && !req.getBuyerEmail().isBlank()) {
            return userRepository.findByEmail(req.getBuyerEmail().trim().toLowerCase()).orElse(null);
        }
        return null;
    }

    private BuyerResolution resolveOrCreateBuyerAccount(TransferRequest transfer) {
        if (transfer.getBuyer() != null) {
            User buyer = transfer.getBuyer();
            if (!buyer.getIsActive()) {
                String tempPassword = generateTemporaryPassword();
                buyer.setPasswordHash(passwordEncoder.encode(tempPassword));
                buyer.setIsActive(true);
                buyer = userRepository.save(buyer);
                log.info("Activated inactive buyer account {} and generated temporary credentials", buyer.getUserId());
                return new BuyerResolution(buyer, true, tempPassword);
            }
            return new BuyerResolution(buyer, false, null);
        }

        if (transfer.getBuyerEmail() == null || transfer.getBuyerEmail().isBlank()) {
            throw new RuntimeException("Cannot create buyer account without a valid email");
        }

        String buyerEmail = transfer.getBuyerEmail().trim().toLowerCase();
        User buyer = userRepository.findByEmail(buyerEmail).orElse(null);
        if (buyer == null && transfer.getBuyerNationalId() != null && !transfer.getBuyerNationalId().isBlank()) {
            buyer = userRepository.findByNationalId(transfer.getBuyerNationalId()).orElse(null);
        }
        if (buyer != null) {
            if (!buyer.getIsActive()) {
                String tempPassword = generateTemporaryPassword();
                buyer.setPasswordHash(passwordEncoder.encode(tempPassword));
                buyer.setIsActive(true);
                buyer = userRepository.save(buyer);
                log.info("Activated existing inactive buyer account {} and generated temporary credentials", buyer.getUserId());
                return new BuyerResolution(buyer, true, tempPassword);
            }
            return new BuyerResolution(buyer, false, null);
        }

        String tempPassword = generateTemporaryPassword();
        String username = generateBuyerUsername(transfer);
        String passwordHash = passwordEncoder.encode(tempPassword);
        User newBuyer = User.builder()
                .fullName(transfer.getBuyerFullName())
                .nationalId(transfer.getBuyerNationalId())
                .phoneNumber(transfer.getBuyerPhoneNumber())
                .email(buyerEmail)
                .username(username)
                .address(transfer.getBuyerAddress())
                .role(User.Role.LAND_OWNER)
                .isActive(true)
                .profileComplete(false)
                .passwordHash(passwordHash)
                .build();
        newBuyer = userRepository.save(newBuyer);
        return new BuyerResolution(newBuyer, true, tempPassword);
    }

    private record BuyerResolution(User buyer, boolean created, String tempPassword) { }


    private void sendBuyerNewAccountCredentialsEmail(User buyer, TransferRequest transfer, String tempPassword) {
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            return;
        }
        try {
            String parcelDetails = transfer.getParcel() != null ? transfer.getParcel().getParcelNumber() : "N/A";
            emailService.sendLandownerCredentials(
                    buyer.getEmail(),
                    buyer.getUsername(),
                    tempPassword,
                    buyer.getFullName(),
                    parcelDetails,
                    transfer.getParcel() != null ? transfer.getParcel().getLocationAddress() : null,
                    transfer.getParcel() != null ? transfer.getParcel().getAreaSqm() : null,
                    null,
                    transfer.getParcel() != null ? transfer.getParcel().getTitleDeedFile() : null);
        } catch (Exception e) {
            log.warn("Failed to send new buyer credentials email to {}: {}", buyer.getEmail(), e.getMessage());
        }
    }

    private void sendBuyerExistingAccountNotificationEmail(User buyer, TransferRequest transfer) {
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            return;
        }

        try {
            String subject = "Land Verification System — Transfer Completed for Parcel " + transfer.getParcel().getParcelNumber();
            String body = String.format(
                    "Dear %s,\n\n" +
                    "Ownership of parcel %s has been transferred to you in the Land Verification System.\n\n" +
                    "Please log in to review your parcel details and continue with any next steps.\n\n" +
                    "Login URL: http://localhost:8080/login.html\n\n" +
                    "If you have any questions, contact the System Administrator.\n\n" +
                    "Best regards,\n" +
                    "Land Verification System Administration\n" +
                    "Republic of Zambia\n",
                    buyer.getFullName() != null && !buyer.getFullName().isBlank() ? buyer.getFullName() : "Landowner",
                    transfer.getParcel().getParcelNumber());
            emailService.sendEmail(buyer.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("Failed to email existing buyer about completed transfer {}: {}",
                    transfer.getParcel() != null ? transfer.getParcel().getParcelNumber() : "N/A", e.getMessage());
        }
    }

    private void syncBuyerDetailsFromTransfer(TransferRequest transfer, User buyer) {
        boolean updated = false;
        if (transfer.getBuyerFullName() != null && !transfer.getBuyerFullName().isBlank()
                && !transfer.getBuyerFullName().equals(buyer.getFullName())) {
            buyer.setFullName(transfer.getBuyerFullName());
            updated = true;
        }
        if (transfer.getBuyerNationalId() != null && !transfer.getBuyerNationalId().isBlank()
                && !transfer.getBuyerNationalId().equals(buyer.getNationalId())) {
            buyer.setNationalId(transfer.getBuyerNationalId());
            updated = true;
        }
        if (transfer.getBuyerPhoneNumber() != null && !transfer.getBuyerPhoneNumber().isBlank()
                && !transfer.getBuyerPhoneNumber().equals(buyer.getPhoneNumber())) {
            buyer.setPhoneNumber(transfer.getBuyerPhoneNumber());
            updated = true;
        }
        if (transfer.getBuyerAddress() != null && !transfer.getBuyerAddress().isBlank()
                && !transfer.getBuyerAddress().equals(buyer.getAddress())) {
            buyer.setAddress(transfer.getBuyerAddress());
            updated = true;
        }
        if (transfer.getBuyerPhotoPath() != null && !transfer.getBuyerPhotoPath().isBlank()
                && !transfer.getBuyerPhotoPath().equals(buyer.getPhotoPath())) {
            buyer.setPhotoPath(transfer.getBuyerPhotoPath());
            updated = true;
        }
        if (updated) {
            userRepository.save(buyer);
            log.info("Synchronized buyer account {} with transfer request details", buyer.getUserId());
        }
    }

    private String saveTransferPhoto(MultipartFile photo, String subject) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("A live " + subject + " photo is required for transfer evidence");
        }
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("The " + subject + " photo must be an image");
        }
        if (photo.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("The " + subject + " photo must be 10 MB or smaller");
        }
        try {
            return fileService.saveFile(photo);
        } catch (IOException e) {
            throw new RuntimeException("Unable to securely store the " + subject + " transfer photo", e);
        }
    }

    private void regenerateTitleDeedForTransfer(LandParcel parcel, User newOwner) throws Exception {
        String oldDeedFile = parcel.getTitleDeedFile();
        ParcelRegistrationRequest req = new ParcelRegistrationRequest();
        req.setProvince(parcel.getProvince());
        req.setDistrict(parcel.getDistrict());
        req.setLocationAddress(parcel.getLocationAddress());
        req.setGpsLat(null);
        req.setGpsLng(null);
        req.setEncumbrances(null);
        String titleDeedFile = titleDeedService.generateCertificate(parcel, newOwner, req);
        parcel.setTitleDeedFile(titleDeedFile);
        parcelRepository.save(parcel);
        log.info("Regenerated title deed for parcel {} with new owner: {}", parcel.getParcelNumber(), newOwner.getUserId());
        if (oldDeedFile != null && !oldDeedFile.isBlank() && !oldDeedFile.equals(titleDeedFile)) {
            deleteOldTitleDeedFile(oldDeedFile);
        }
    }

    private void deleteOldTitleDeedFile(String oldDeedFile) {
        try {
            Path oldPath = Path.of(appProperties.getUploadDir()).resolve(oldDeedFile);
            if (Files.exists(oldPath)) {
                Files.delete(oldPath);
                log.info("Deleted old title deed file {}", oldPath);
            }
        } catch (Exception e) {
            log.warn("Unable to delete old title deed file {}: {}", oldDeedFile, e.getMessage());
        }
    }

    private String generateBuyerUsername(TransferRequest transfer) {
        if (transfer.getBuyerEmail() != null && !transfer.getBuyerEmail().isBlank()) {
            String base = transfer.getBuyerEmail().split("@")[0].replaceAll("[^A-Za-z0-9._-]", "");
            return ensureUniqueUsername(base);
        }
        if (transfer.getBuyerNationalId() != null && !transfer.getBuyerNationalId().isBlank()) {
            String base = "buyer" + transfer.getBuyerNationalId().replaceAll("[^A-Za-z0-9]", "");
            return ensureUniqueUsername(base);
        }
        return ensureUniqueUsername("buyer" + UUID.randomUUID().toString().substring(0, 8));
    }

    private String ensureUniqueUsername(String base) {
        String username = base;
        int suffix = 1;
        while (username == null || username.isBlank() || userRepository.findByUsername(username).isPresent()) {
            username = base + suffix++;
        }
        return username;
    }

    private String getBuyerDisplayName(TransferRequest transfer) {
        if (transfer.getBuyer() != null) {
            return transfer.getBuyer().getFullName();
        }
        if (transfer.getBuyerFullName() != null && !transfer.getBuyerFullName().isBlank()) {
            return transfer.getBuyerFullName();
        }
        if (transfer.getBuyerEmail() != null && !transfer.getBuyerEmail().isBlank()) {
            return transfer.getBuyerEmail();
        }
        return "Unknown Buyer";
    }

    private String getBuyerNationalId(TransferRequest transfer) {
        if (transfer.getBuyer() != null) {
            return transfer.getBuyer().getNationalId();
        }
        return transfer.getBuyerNationalId();
    }

    private String getBuyerEmail(TransferRequest transfer) {
        if (transfer.getBuyer() != null) {
            return transfer.getBuyer().getEmail();
        }
        return transfer.getBuyerEmail();
    }

    private String getBuyerPhone(TransferRequest transfer) {
        if (transfer.getBuyer() != null) {
            return transfer.getBuyer().getPhoneNumber();
        }
        return transfer.getBuyerPhoneNumber();
    }

    private void persistSupportingDocuments(TransferRequest transfer, User uploadedBy, List<MultipartFile> supportingFiles, String supportingDocsText) {
        if (transfer == null || transfer.getRequestId() == null || uploadedBy == null || supportingFiles == null || supportingFiles.isEmpty()) {
            return;
        }

        for (MultipartFile file : supportingFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                String savedName = fileService.saveFile(file);
                Document document = Document.builder()
                        .relatedType(Document.RelatedType.TRANSFER_REQUEST)
                        .relatedId(transfer.getRequestId())
                        .documentType(Document.DocumentType.OTHER)
                        .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : savedName)
                        .filePath("/uploads/" + savedName)
                        .uploadedBy(uploadedBy)
                        .build();
                documentRepository.save(document);
            } catch (Exception e) {
                log.warn("Failed to save supporting document for transfer {}: {}", transfer.getRequestId(), e.getMessage());
            }
        }

        if (supportingDocsText != null && !supportingDocsText.isBlank()) {
            transfer.setSupportingDocs(supportingDocsText);
            transferRepository.save(transfer);
        }
    }

    private List<DocumentResponse> getSupportingDocuments(TransferRequest transfer) {
        if (transfer == null || transfer.getRequestId() == null) {
            return java.util.List.of();
        }

        return documentRepository.findByRelatedTypeAndRelatedId(Document.RelatedType.TRANSFER_REQUEST, transfer.getRequestId())
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    private DocumentResponse toDocumentResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getDocumentId() != null ? document.getDocumentId().longValue() : null);
        response.setFileName(document.getFileName());
        response.setFilePath(document.getFilePath());
        response.setDocumentType(document.getDocumentType() != null ? document.getDocumentType().name() : null);
        response.setCategory(document.getDocumentType() != null ? document.getDocumentType().name() : null);
        response.setUploadedAt(document.getUploadedAt());
        return response;
    }

    private TransferResponse toResponse(TransferRequest t) {
        return toResponse(t, null);
    }

    private TransferResponse toResponse(TransferRequest t, String emailStatusMessage) {
        TransferResponse r = new TransferResponse();
        r.setRequestId(t.getRequestId());
        r.setParcelNumber(t.getParcel().getParcelNumber());
        r.setSellerName(t.getSeller().getFullName());
        r.setSellerNid(t.getSeller().getNationalId());
        r.setBuyerName(getBuyerDisplayName(t));
        r.setBuyerNid(getBuyerNationalId(t));
        r.setBuyerEmail(getBuyerEmail(t));
        r.setBuyerPhone(getBuyerPhone(t));
        r.setStatus(t.getStatus().name());
        r.setTransferReason(t.getTransferReason());
        r.setAgreedPriceZmw(t.getAgreedPriceZmw());
        r.setBlockchainTxHash(t.getBlockchainTxHash());
        r.setSubmittedAt(t.getSubmittedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        r.setFlagged(t.isFlagged());
        r.setFlagReason(t.getFlagReason());
        r.setFlaggedAt(t.getFlaggedAt());
        r.setEmailStatusMessage(emailStatusMessage);
        
        // Add parcel details
        if (t.getParcel() != null) {
            r.setDistrict(t.getParcel().getDistrict());
            r.setLandUse(t.getParcel().getLandUse() != null ? t.getParcel().getLandUse().name() : null);
            r.setAreaSqm(t.getParcel().getAreaSqm());
        }

        r.setSupportingDocs(t.getSupportingDocs());
        r.setSupportingDocuments(getSupportingDocuments(t));
        r.setHasOwnerSignature(t.getOwnerSignature() != null && !t.getOwnerSignature().isBlank());
        r.setHasBuyerSignature(t.getBuyerSignature() != null && !t.getBuyerSignature().isBlank());
        r.setSellerPhotoPath(t.getSellerPhotoPath());
        r.setBuyerPhotoPath(t.getBuyerPhotoPath());
        r.setApprovalHistory(approvalRepository.findByRequest_RequestId(t.getRequestId())
                .stream().map(this::toApprovalResponse).collect(Collectors.toList()));
        
        return r;
    }

    private ApprovalWorkflowResponse toApprovalResponse(ApprovalWorkflow approval) {
        ApprovalWorkflowResponse response = new ApprovalWorkflowResponse();
        response.setApprovalId(approval.getApprovalId());
        response.setRequestId(approval.getRequest().getRequestId());
        response.setOfficerName(approval.getOfficer() != null ? approval.getOfficer().getFullName() : "Unknown");
        response.setApprovalLevel(approval.getApprovalLevel());
        response.setAction(approval.getAction() != null ? approval.getAction().name() : null);
        response.setComments(approval.getComments());
        response.setDigitalSignature(approval.getDigitalSignature());
        response.setActionedAt(approval.getActionedAt());
        response.setStaffId(approval.getOfficer() != null ? approval.getOfficer().getUserId() : null);
        return response;
    }

    // ============================================
    // BUYER SEARCH AND ACCOUNT MANAGEMENT
    // ============================================

    /**
     * Search for existing buyer account(s) by NRC, email, or account number
     * Returns active landowner accounts that match the search criteria
     */
    public List<BuyerSearchResponse> searchBuyerAccount(BuyerSearchRequest req) {
        List<User> results = new java.util.ArrayList<>();
        
        if (req.getNrc() != null && !req.getNrc().isBlank()) {
            User user = userRepository.findByNationalId(req.getNrc()).orElse(null);
            if (user != null && user.getRole() == User.Role.LAND_OWNER && user.getIsActive()) {
                results.add(user);
            }
        }
        
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            User user = userRepository.findByEmail(req.getEmail().trim().toLowerCase()).orElse(null);
            if (user != null && user.getRole() == User.Role.LAND_OWNER && user.getIsActive() && !results.contains(user)) {
                results.add(user);
            }
        }
        
        return results.stream()
                .map(u -> BuyerSearchResponse.builder()
                        .userId(u.getUserId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .phoneNumber(u.getPhoneNumber())
                        .nationalId(u.getNationalId())
                        .address(u.getAddress())
                        .district(u.getDistrict())
                        .role(u.getRole().name())
                        .isActive(u.getIsActive())
                        .createdAt(u.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Create an inactive user account for a new buyer
     * Account will be activated after final approval of the transfer
     */
    private User createInactiveNewBuyerAccount(InitiateTransferRequest req) {
        String buyerEmail = req.getBuyerEmail().trim().toLowerCase();
        
        // Check if account already exists
        User existingUser = userRepository.findByEmail(buyerEmail).orElse(null);
        if (existingUser != null) {
            log.info("Account already exists for email {}", buyerEmail);
            return existingUser;
        }
        
        String username = generateBuyerUsername(req.getBuyerName());
        String tempPassword = generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);
        
        User newBuyer = User.builder()
                .fullName(req.getBuyerName())
                .nationalId(req.getBuyerNid())
                .phoneNumber(req.getBuyerPhone())
                .email(buyerEmail)
                .username(username)
                .address(req.getBuyerAddress())
                .district(null) // Will be set after activation
                .role(User.Role.LAND_OWNER)
                .isActive(false) // IMPORTANT: Create as inactive, activate after final approval
                .profileComplete(false)
                .passwordHash(passwordHash)
                .build();
        
        newBuyer = userRepository.save(newBuyer);
        log.info("Created new inactive buyer account: user={}, email={}", newBuyer.getUserId(), buyerEmail);
        return newBuyer;
    }

    /**
     * Generate a username for new buyer accounts
     */
    private String generateBuyerUsername(String fullName) {
        String baseUsername = fullName
                .toLowerCase()
                .replaceAll("\\s+", ".")
                .replaceAll("[^a-z0-9.]", "")
                .substring(0, Math.min(20, fullName.length()));
        
        String username = baseUsername;
        int counter = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + counter;
            counter++;
        }
        return username;
    }

    /**
     * Generate temporary password for new buyer accounts
     */
    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return password.toString();
    }
}