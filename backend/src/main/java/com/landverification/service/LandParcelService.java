package com.landverification.service;

import com.landverification.dto.DocumentResponse;
import com.landverification.dto.LandownerWithParcelsResponse;
import com.landverification.dto.ParcelRegistrationRequest;
import com.landverification.dto.ParcelResponse;
import com.landverification.dto.VerifyResponse;
import com.landverification.model.*;
import com.landverification.model.ParcelSequence;
import com.landverification.model.ParcelSequenceId;
import com.landverification.repository.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.util.regex.Pattern;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class LandParcelService {

    private final LandParcelRepository parcelRepository;
    private final LandOwnershipRepository ownershipRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final BlockchainService blockchainService;
    private final TitleDeedService titleDeedService;
    private final ParcelSequenceRepository parcelSequenceRepository;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransferRequestRepository transferRequestRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final JurisdictionAccessService jurisdictionAccessService;
    private final FileService fileService;
    private final Executor submissionTaskExecutor;

    public LandParcelService(LandParcelRepository parcelRepository,
                             LandOwnershipRepository ownershipRepository,
                             UserRepository userRepository,
                             DocumentRepository documentRepository,
                             BlockchainService blockchainService,
                             TitleDeedService titleDeedService,
                             ParcelSequenceRepository parcelSequenceRepository,
                             EmailService emailService,
                             NotificationRepository notificationRepository,
                             PasswordEncoder passwordEncoder,
                             TransferRequestRepository transferRequestRepository,
                             ApprovalWorkflowRepository approvalWorkflowRepository,
                             JurisdictionAccessService jurisdictionAccessService,
                             FileService fileService,
                             @org.springframework.beans.factory.annotation.Qualifier("submissionTaskExecutor") Executor submissionTaskExecutor) {
        this.parcelRepository = parcelRepository;
        this.ownershipRepository = ownershipRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.blockchainService = blockchainService;
        this.titleDeedService = titleDeedService;
        this.parcelSequenceRepository = parcelSequenceRepository;
        this.emailService = emailService;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.transferRequestRepository = transferRequestRepository;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
        this.jurisdictionAccessService = jurisdictionAccessService;
        this.fileService = fileService;
        this.submissionTaskExecutor = submissionTaskExecutor;
    }

    private static final Map<String, String> PROVINCE_CODES = Map.of(
            "Lusaka", "LUS",
            "Copperbelt", "CBT",
            "Central", "CEN",
            "Southern", "SOU",
            "Eastern", "EST",
            "Western", "WST",
            "Northern", "NRT",
            "Luapula", "LPL",
            "Muchinga", "MCH",
            "North-Western", "NWT"
    );

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Transactional
    public ParcelResponse registerParcel(ParcelRegistrationRequest req, Integer officerUserId) {
        return registerParcel(req, officerUserId, null);
    }

    @Transactional
    public ParcelResponse registerParcel(ParcelRegistrationRequest req, Integer officerUserId,
                                         MultipartFile ownerPhoto) {
        if (req.getParcelNumber() == null || req.getParcelNumber().isBlank()) {
            req.setParcelNumber(generateNextParcelNumber(req.getProvince()));
        }
        if (parcelRepository.existsByParcelNumber(req.getParcelNumber())) {
            throw new RuntimeException("Parcel number already exists: " + req.getParcelNumber());
        }

        validateNoDuplicateParcel(req);

        if (req.getOwnerSignature() == null || req.getOwnerSignature().isBlank()) {
            throw new IllegalArgumentException("Owner signature is required for parcel registration");
        }

        if (req.getOwnerUserId() == null) {
            if (req.getOwnerName() == null || req.getOwnerName().isBlank()) {
                throw new RuntimeException("Owner name is required when registering a new owner.");
            }
            if (req.getOwnerNid() == null || req.getOwnerNid().isBlank()) {
                throw new RuntimeException("Owner national ID is required when registering a new owner.");
            }
            if (req.getOwnerPhone() == null || req.getOwnerPhone().isBlank()) {
                throw new RuntimeException("Owner phone number is required when registering a new owner.");
            }
            if (req.getOwnerGender() == null || req.getOwnerGender().isBlank()) {
                throw new RuntimeException("Owner gender is required when registering a new owner.");
            }
            if (req.getOwnerAddress() == null || req.getOwnerAddress().isBlank()) {
                throw new RuntimeException("Owner address is required when registering a new owner.");
            }
        }

        OwnerCreation ownerCreation = findOrCreateOwner(req);
        User owner = ownerCreation.user();

        if (ownerPhoto != null && !ownerPhoto.isEmpty()) {
            validateOwnerPhoto(ownerPhoto);
            try {
                owner.setPhotoPath(fileService.saveFile(ownerPhoto));
                owner = userRepository.save(owner);
            } catch (IOException e) {
                throw new RuntimeException("Unable to securely store the owner photo", e);
            }
        }

        LandParcel parcel = LandParcel.builder()
                .parcelNumber(req.getParcelNumber())
                .plotNumber(req.getPlotNumber())
                .titleDeedNumber(generateTitleDeedNumber(req.getParcelNumber(), req.getProvince(), req.getDistrict()))
                .blockSection(req.getBlockSection())
                .province(req.getProvince())
                .district(req.getDistrict())
                .locationAddress(req.getLocationAddress())
                .gpsLat(req.getGpsLat())
                .gpsLng(req.getGpsLng())
                .areaSqm(req.getPlotSize() != null ? java.math.BigDecimal.valueOf(req.getPlotSize()) : null)
                .landUse(parseLandUse(req.getLandUseType()))
                .landCategory(req.getLandCategory() != null ? parseLandCategory(req.getLandCategory()) : null)
                .status(LandParcel.ParcelStatus.ACTIVE)
                .ownerPhotoPath(owner.getPhotoPath())
                .registeredByOfficerId(officerUserId)
                .build();
        parcel = parcelRepository.save(parcel);

        LandOwnership ownership = LandOwnership.builder()
                .parcel(parcel)
                .owner(owner)
                .ownershipType(parseOwnershipType(req.getOwnershipType()))
                .acquisitionMethod(parseAcquisitionMethod(req.getTenureType()))
                .startDate(LocalDate.now())
                .isCurrent(true)
                .ownerSignature(req.getOwnerSignature().trim())
                .build();
        ownershipRepository.save(ownership);
        String documentHash = req.getDocumentHash() != null && !req.getDocumentHash().isBlank()
                ? req.getDocumentHash().trim() : createDocumentHash(req);
        parcel.setDocumentHash(documentHash);
        parcelRepository.save(parcel);
        final LandParcel persistedParcel = parcel;
        final User ownerForProcessing = owner;
        final boolean ownerAccountCreated = ownerCreation.newOwnerCreated();
        final String ownerTempPassword = ownerCreation.tempPassword();
        runAfterCommit(() -> processRegistrationSideEffects(
                persistedParcel, ownerForProcessing, ownership, req, officerUserId, ownerAccountCreated, ownerTempPassword));

        LandOwnership currentOwnership = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId()).orElse(null);
        return toResponse(parcel, currentOwnership, ownerAccountCreated, false, officerUserId);
    }

    private void processRegistrationSideEffects(LandParcel parcel, User owner, LandOwnership ownership,
                                                ParcelRegistrationRequest req, Integer officerUserId,
                                                boolean ownerAccountCreated, String ownerTempPassword) {
        try {
            blockchainService.recordOwnershipChange(parcel.getParcelNumber(), parcel.getParcelId(), null, officerUserId);

            try {
                String titleDeedFile = titleDeedService.generateCertificate(parcel, owner, req);
                parcel.setTitleDeedFile(titleDeedFile);
                parcelRepository.save(parcel);
            } catch (Exception e) {
                log.warn("Title deed generation failed for parcel {}: {}", parcel.getParcelNumber(), e.getMessage());
            }

            String wallet = req.getOwnerWalletAddress();
                String recordHash = parcel.getDocumentHash();
            if (wallet == null || !wallet.matches("^0x[0-9a-fA-F]{40}$")) {
                wallet = blockchainService.getDefaultOwnerAddress();
            }
            try {
                String txHash = blockchainService.registerParcel(parcel.getParcelNumber(), parcel.getProvince(),
                        parcel.getDistrict(), parcel.getLocationAddress(), parcel.getAreaSqm().toBigInteger(),
                        parcel.getLandUse().name(), wallet, recordHash, parcel.getParcelId(), officerUserId);
                parcel.setBlockchainHash(txHash);
                parcelRepository.save(parcel);
            } catch (Exception e) {
                log.warn("Blockchain registration failed for parcel {}: {}", parcel.getParcelNumber(), e.getMessage());
            }

            if (ownerCreationHasRealEmail(owner)) {
                String deedUrl = parcel.getTitleDeedFile() != null ? "/uploads/" + parcel.getTitleDeedFile() : null;
                emailService.sendLandownerCredentialsAsync(owner.getEmail(), owner.getUsername(),
                    ownerAccountCreated ? ownerTempPassword : null, owner.getFullName(), parcel.getParcelNumber(),
                        parcel.getLocationAddress(), parcel.getAreaSqm(),
                        ownership.getOwnershipType() != null ? ownership.getOwnershipType().name() : null, deedUrl);
            }
            sendNotification(owner, "Your land parcel " + parcel.getParcelNumber() + " has been registered successfully.",
                    Notification.NotificationType.REGISTRATION_COMPLETE, parcel.getParcelId(), null);
        } catch (Exception e) {
            log.warn("Post-registration processing failed for parcel {}: {}", parcel.getParcelNumber(), e.getMessage());
        }
    }

    private boolean ownerCreationHasRealEmail(User owner) {
        return owner.getEmail() != null && !owner.getEmail().isBlank() && isValidEmail(owner.getEmail());
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

    private OwnerCreation findOrCreateOwner(ParcelRegistrationRequest req) {
        if (req.getOwnerUserId() != null) {
            var ownerOpt = userRepository.findById(req.getOwnerUserId());
            if (ownerOpt.isPresent()) {
                User owner = ownerOpt.get();
                if (owner.getRole() != User.Role.LAND_OWNER) {
                    throw new RuntimeException("Specified user is not a landowner account");
                }
                if (ownerDetailsConflict(owner, req)) {
                    log.info("Owner details conflict with selected ownerUserId {}; ignoring ownerUserId and creating a new owner", req.getOwnerUserId());
                    req.setOwnerUserId(null);
                } else {
                    if ((req.getOwnerEmail() == null || req.getOwnerEmail().isBlank()) && owner.getEmail() != null) {
                        req.setOwnerEmail(owner.getEmail());
                    }
                    if ((req.getOwnerNid() == null || req.getOwnerNid().isBlank()) && owner.getNationalId() != null) {
                        req.setOwnerNid(owner.getNationalId());
                    }
                    if ((req.getOwnerName() == null || req.getOwnerName().isBlank()) && owner.getFullName() != null) {
                        req.setOwnerName(owner.getFullName());
                    }
                    if ((req.getOwnerPhone() == null || req.getOwnerPhone().isBlank()) && owner.getPhoneNumber() != null) {
                        req.setOwnerPhone(owner.getPhoneNumber());
                    }
                    if ((req.getOwnerAddress() == null || req.getOwnerAddress().isBlank()) && owner.getAddress() != null) {
                        req.setOwnerAddress(owner.getAddress());
                    }
                    if ((req.getOwnerGender() == null || req.getOwnerGender().isBlank()) && owner.getGender() != null) {
                        req.setOwnerGender(owner.getGender().name());
                    }
                    return new OwnerCreation(owner, null, false, isValidEmail(owner.getEmail()));
                }
            }

            // Owner id supplied but not found — fall back to creating a new owner from supplied details
            log.info("Owner id {} not found; proceeding to create new owner from provided registration details", req.getOwnerUserId());
        }

        String ownerEmail = normalizeEmail(req.getOwnerEmail());
        String ownerNid = normalizeNationalId(req.getOwnerNid());

        Optional<User> existing = findExistingLandowner(ownerEmail, ownerNid);
        if (existing.isPresent()) {
            User owner = existing.get();
            boolean updated = false;

            if (isValidEmail(ownerEmail) && !ownerEmail.equals(owner.getEmail())) {
                if (owner.getEmail() == null || owner.getEmail().isBlank() || owner.getEmail().contains("@landverify.local")) {
                    owner.setEmail(ownerEmail);
                    updated = true;
                }
            }
            if (ownerNid != null && !ownerNid.isBlank() && !ownerNid.equals(owner.getNationalId())) {
                owner.setNationalId(ownerNid);
                updated = true;
            }
            if (req.getOwnerName() != null && !req.getOwnerName().isBlank() && !req.getOwnerName().equals(owner.getFullName())) {
                owner.setFullName(req.getOwnerName());
                updated = true;
            }
            if (req.getOwnerPhone() != null && !req.getOwnerPhone().isBlank() && !req.getOwnerPhone().equals(owner.getPhoneNumber())) {
                owner.setPhoneNumber(req.getOwnerPhone());
                updated = true;
            }
            if (req.getOwnerAddress() != null && !req.getOwnerAddress().isBlank() && !req.getOwnerAddress().equals(owner.getAddress())) {
                owner.setAddress(req.getOwnerAddress());
                updated = true;
            }
            if (req.getOwnerGender() != null && !req.getOwnerGender().isBlank()) {
                User.Gender newGender = parseGender(req.getOwnerGender());
                if (newGender != null && owner.getGender() != newGender) {
                    owner.setGender(newGender);
                    updated = true;
                }
            }
            if (req.getDistrict() != null && !req.getDistrict().isBlank() && !req.getDistrict().equals(owner.getDistrict())) {
                owner.setDistrict(req.getDistrict());
                updated = true;
            }
            if (updated) {
                owner = userRepository.save(owner);
            }
            // ensure request contains ownerUserId for downstream use
            if (owner.getUserId() != null) {
                req.setOwnerUserId(owner.getUserId());
            }
            return new OwnerCreation(owner, null, false, isValidEmail(owner.getEmail()));
        }

        String generatedEmail = generateOwnerEmail(ownerEmail, ownerNid);
        if (userRepository.existsByEmail(generatedEmail)) {
            generatedEmail = generatedEmail.replace("@", "." + UUID.randomUUID().toString() + "@");
        }

        String password = generateTemporaryPassword();
        User owner = User.builder()
                .fullName(req.getOwnerName())
                .nationalId(ownerNid)
                .phoneNumber(req.getOwnerPhone())
                .email(generatedEmail)
                .username(generateOwnerUsername(req))
                .district(req.getDistrict())
                .address(req.getOwnerAddress())
                .gender(parseGender(req.getOwnerGender()))
                .role(User.Role.LAND_OWNER)
                .profileComplete(false)
                .passwordHash(passwordEncoder.encode(password))
                .isActive(true)
                .build();
        owner = userRepository.save(owner);

        // ensure request gets the generated DB id so callers and responses can reference it
        if (owner.getUserId() != null) {
            req.setOwnerUserId(owner.getUserId());
        }

        return new OwnerCreation(owner, password, true, isValidEmail(ownerEmail));
    }

    private void validateNoDuplicateParcel(ParcelRegistrationRequest req) {
        List<LandParcel> existingParcels = parcelRepository.findAll();
        String normalizedParcelNumber = normalizeParcelNumber(req.getParcelNumber());
        String normalizedLocation = normalizeText(req.getLocationAddress());
        String normalizedProvince = normalizeText(req.getProvince());
        String normalizedDistrict = normalizeText(req.getDistrict());
        String normalizedOwnerName = normalizeText(req.getOwnerName());
        String normalizedOwnerNid = normalizeNationalId(req.getOwnerNid());
        String normalizedOwnerEmail = normalizeEmail(req.getOwnerEmail());

        for (LandParcel existing : existingParcels) {
            boolean sameParcelNumber = normalizedParcelNumber != null
                    && normalizedParcelNumber.equals(normalizeParcelNumber(existing.getParcelNumber()));
            boolean sameAddress = normalizedLocation != null
                    && normalizedLocation.equals(normalizeText(existing.getLocationAddress()));
            boolean sameProvinceDistrict = normalizedProvince != null && normalizedDistrict != null
                    && normalizedProvince.equals(normalizeText(existing.getProvince()))
                    && normalizedDistrict.equals(normalizeText(existing.getDistrict()));

            boolean sameOwnerName = false;
            boolean sameOwnerId = false;
            boolean sameOwnerEmail = false;
            if (existing.getParcelId() != null) {
                Optional<LandOwnership> currentOwnership = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(existing.getParcelId());
                if (currentOwnership.isPresent()) {
                    User existingOwner = currentOwnership.get().getOwner();
                    if (existingOwner != null) {
                        sameOwnerName = normalizedOwnerName != null
                                && normalizedOwnerName.equals(normalizeText(existingOwner.getFullName()));
                        sameOwnerId = normalizedOwnerNid != null && normalizedOwnerNid.equals(normalizeNationalId(existingOwner.getNationalId()));
                        sameOwnerEmail = normalizedOwnerEmail != null && normalizedOwnerEmail.equals(normalizeEmail(existingOwner.getEmail()));
                    }
                }
            }

            if ((sameParcelNumber || (sameAddress && sameProvinceDistrict && (sameOwnerName || sameOwnerId || sameOwnerEmail)))) {
                throw new IllegalArgumentException("A similar parcel registration already exists for this owner and location.");
            }
        }
    }

    private String normalizeParcelNumber(String parcelNumber) {
        if (parcelNumber == null || parcelNumber.isBlank()) {
            return null;
        }
        return parcelNumber.trim().toUpperCase().replaceAll("\\s+", "");
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private boolean ownerDetailsConflict(User owner, ParcelRegistrationRequest req) {
        if (req.getOwnerName() != null && !req.getOwnerName().isBlank() && !req.getOwnerName().equalsIgnoreCase(owner.getFullName())) {
            return true;
        }
        if (req.getOwnerEmail() != null && !req.getOwnerEmail().isBlank() && !req.getOwnerEmail().equalsIgnoreCase(owner.getEmail())) {
            return true;
        }
        if (req.getOwnerNid() != null && !req.getOwnerNid().isBlank() && !req.getOwnerNid().equalsIgnoreCase(owner.getNationalId())) {
            return true;
        }
        if (req.getOwnerPhone() != null && !req.getOwnerPhone().isBlank() && !req.getOwnerPhone().equalsIgnoreCase(owner.getPhoneNumber())) {
            return true;
        }
        if (req.getOwnerAddress() != null && !req.getOwnerAddress().isBlank() && !req.getOwnerAddress().equalsIgnoreCase(owner.getAddress())) {
            return true;
        }
        if (req.getOwnerGender() != null && !req.getOwnerGender().isBlank()) {
            String normalizedGender = req.getOwnerGender().trim().toUpperCase();
            String ownerGender = owner.getGender() != null ? owner.getGender().name() : null;
            if (ownerGender != null && !ownerGender.equalsIgnoreCase(normalizedGender)) {
                return true;
            }
        }
        return false;
    }

    private Optional<User> findExistingLandowner(String ownerEmail, String ownerNid) {
        if (ownerEmail != null && !ownerEmail.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(ownerEmail);
            if (byEmail.isPresent() && byEmail.get().getRole() == User.Role.LAND_OWNER) {
                return byEmail;
            }
        }
        if (ownerNid != null && !ownerNid.isBlank()) {
            Optional<User> byNid = userRepository.findByNationalId(ownerNid);
            if (byNid.isPresent() && byNid.get().getRole() == User.Role.LAND_OWNER) {
                return byNid;
            }
        }
        return Optional.empty();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizeNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return null;
        }
        return nationalId.trim();
    }

    private String generateOwnerUsername(ParcelRegistrationRequest req) {
        String baseUsername;
        if (req.getOwnerEmail() != null && !req.getOwnerEmail().isBlank()) {
            baseUsername = req.getOwnerEmail().split("@")[0].replaceAll("[^A-Za-z0-9._-]", "");
        } else if (req.getOwnerNid() != null && !req.getOwnerNid().isBlank()) {
            baseUsername = "owner" + req.getOwnerNid().replaceAll("[^0-9A-Za-z]", "");
        } else {
            baseUsername = "owner" + UUID.randomUUID().toString().substring(0, 8);
        }
        return ensureUniqueOwnerUsername(baseUsername);
    }

    private String ensureUniqueOwnerUsername(String baseUsername) {
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + suffix++;
        }
        return username;
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID().toString().replaceAll("[-]", "").substring(0, 12);
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                .matcher(email.trim()).matches();
    }

    private String generateTitleDeedNumber(String parcelNumber, String province, String district) {
        var year = java.time.LocalDate.now().getYear();
        String safeParcel = parcelNumber != null ? parcelNumber.replaceAll("[^A-Za-z0-9]", "") : "PARCEL";
        String region = (province != null ? province.trim().toUpperCase().replaceAll("\\s+", "") : "GEN");
        return String.format("ZMB-%s-%s-%s", region, year, safeParcel);
    }

    private record OwnerCreation(User user, String tempPassword, boolean newOwnerCreated, boolean hasRealEmail) {}

    private String generateOwnerEmail(String email, String nationalId) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase();
        }
        if (nationalId != null && !nationalId.isBlank()) {
            String safeNid = nationalId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            return String.format("owner-%s@landverify.local", safeNid);
        }
        return "owner-" + UUID.randomUUID() + "@landverify.local";
    }

    private User.Gender parseGender(String genderValue) {
        if (genderValue == null || genderValue.isBlank()) {
            return null;
        }
        try {
            return User.Gender.valueOf(genderValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LandParcel.LandUse parseLandUse(String landUseValue) {
        if (landUseValue == null || landUseValue.isBlank()) {
            return LandParcel.LandUse.RESIDENTIAL;
        }
        try {
            return LandParcel.LandUse.valueOf(landUseValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return LandParcel.LandUse.RESIDENTIAL;
        }
    }

    private LandParcel.LandCategory parseLandCategory(String landCategoryValue) {
        if (landCategoryValue == null || landCategoryValue.isBlank()) {
            return null;
        }
        try {
            return LandParcel.LandCategory.valueOf(landCategoryValue.trim().toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private LandOwnership.OwnershipType parseOwnershipType(String ownershipType) {
        if (ownershipType == null || ownershipType.isBlank()) {
            return LandOwnership.OwnershipType.SOLE;
        }
        switch (ownershipType.trim().toUpperCase()) {
            case "INDIVIDUAL":
            case "SOLE":
            case "COMPANY":
                return LandOwnership.OwnershipType.SOLE;
            case "JOINT":
                return LandOwnership.OwnershipType.JOINT;
            case "LEASEHOLD":
                return LandOwnership.OwnershipType.LEASEHOLD;
            case "FREEHOLD":
                return LandOwnership.OwnershipType.FREEHOLD;
            default:
                return LandOwnership.OwnershipType.SOLE;
        }
    }

    private LandOwnership.AcquisitionMethod parseAcquisitionMethod(String tenureType) {
        if (tenureType == null || tenureType.isBlank()) {
            return LandOwnership.AcquisitionMethod.PURCHASE;
        }
        switch (tenureType.trim().toUpperCase()) {
            case "LEASEHOLD":
            case "FREEHOLD":
                return LandOwnership.AcquisitionMethod.PURCHASE;
            default:
                return LandOwnership.AcquisitionMethod.PURCHASE;
        }
    }

    private String createDocumentHash(ParcelRegistrationRequest req) {
        String source = String.join("|",
                req.getParcelNumber() != null ? req.getParcelNumber() : "",
                req.getOwnerName() != null ? req.getOwnerName() : "",
                req.getOwnerNid() != null ? req.getOwnerNid() : "",
                req.getProvince() != null ? req.getProvince() : "",
                req.getDistrict() != null ? req.getDistrict() : "",
                req.getLocationAddress() != null ? req.getLocationAddress() : "",
                req.getPlotSize() != null ? req.getPlotSize().toString() : "",
                req.getLandUseType() != null ? req.getLandUseType() : "",
                req.getOwnerEmail() != null ? req.getOwnerEmail() : ""
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to hash parcel record", e);
        }
    }

    public ParcelResponse getParcelByNumber(String parcelNumber, User officer) {
        LandParcel parcel = parcelRepository.findByParcelNumber(parcelNumber)
                .orElseThrow(() -> new RuntimeException("Parcel not found: " + parcelNumber));
        enforceJurisdictionAccess(officer, parcel);
        reconcileCompletedTransfer(parcel);
        LandOwnership current = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId()).orElse(null);
        return toResponse(parcel, current);
    }

    private void reconcileCompletedTransfer(LandParcel parcel) {
        if (parcel.getStatus() != LandParcel.ParcelStatus.PENDING_TRANSFER) {
            return;
        }
        boolean completed = transferRequestRepository
                .findByParcel_ParcelIdOrderBySubmittedAtDesc(parcel.getParcelId())
                .stream()
                .anyMatch(transfer -> transfer.getStatus() == TransferRequest.TransferStatus.APPROVED);
        if (completed) {
            parcel.setStatus(LandParcel.ParcelStatus.ACTIVE);
            parcelRepository.save(parcel);
        }
    }

        public ParcelResponse getParcelById(Integer parcelId) {
        LandParcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new RuntimeException("Parcel not found: " + parcelId));
        LandOwnership current = ownershipRepository
            .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId()).orElse(null);
        return toResponse(parcel, current);
        }

    public List<ParcelResponse> searchParcels(String query, LocalDate fromDate, LocalDate toDate, User officer) {
        List<LandParcel> parcels;
        if (query == null || query.isBlank()) {
            parcels = parcelRepository.findAll();
        } else {
            String cleaned = query.trim();
            Map<Integer, LandParcel> found = new LinkedHashMap<>();
            parcelRepository.search(cleaned).forEach(p -> found.put(p.getParcelId(), p));
            ownershipRepository.findByOwner_FullNameContainingIgnoreCaseAndIsCurrentTrue(cleaned)
                    .stream()
                    .map(LandOwnership::getParcel)
                    .forEach(p -> found.put(p.getParcelId(), p));
            parcels = new ArrayList<>(found.values());
        }

        if (officer != null) {
            parcels = parcels.stream()
                .filter(p -> (p.getRegisteredByOfficerId() != null
                    && p.getRegisteredByOfficerId().equals(officer.getUserId()))
                    || jurisdictionAccessService.canAccess(officer, p))
                    .collect(Collectors.toList());
        }

        if (fromDate != null) {
            parcels = parcels.stream()
                    .filter(p -> p.getRegisteredAt() != null && !p.getRegisteredAt().toLocalDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            parcels = parcels.stream()
                    .filter(p -> p.getRegisteredAt() != null && !p.getRegisteredAt().toLocalDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        return parcels.stream()
            .map(p -> {
                LandOwnership o = ownershipRepository
                    .findByParcel_ParcelIdAndIsCurrentTrue(p.getParcelId()).orElse(null);
                return toResponse(p, o);
            }).collect(Collectors.toList());
    }

    public List<ParcelResponse> getParcelsByOwner(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return getParcelsByOwner(user);
    }

    public List<ParcelResponse> getParcelsByOwner(User user) {
        log.info("Fetching current parcels for user ID: {} (email: {}, nationalId: {})",
                user.getUserId(), user.getEmail(), user.getNationalId());

        List<LandOwnership> ownerships = ownershipRepository.findCurrentByOwnerUserId(user.getUserId());
        if (ownerships.isEmpty() && user.getEmail() != null && !user.getEmail().isBlank()) {
            log.info("No parcels found by userId; falling back to owner email {}", user.getEmail());
            ownerships = ownershipRepository.findByOwner_EmailAndIsCurrentTrue(user.getEmail());
        }
        if (ownerships.isEmpty() && user.getNationalId() != null && !user.getNationalId().isBlank()) {
            log.info("No parcels found by email; falling back to owner nationalId {}", user.getNationalId());
            ownerships = ownershipRepository.findByOwner_NationalIdAndIsCurrentTrue(user.getNationalId());
        }

        log.info("Found {} current ownerships for user {}", ownerships.size(), user.getUserId());
        ownerships.forEach(o -> log.info("  - Ownership: Parcel={}, Owner={}, IsCurrent={}",
            o.getParcel().getParcelNumber(), o.getOwner().getFullName(), o.getIsCurrent()));

        List<ParcelResponse> results = new ArrayList<>();
        Set<Integer> includedParcelIds = new LinkedHashSet<>();

        for (LandOwnership ownership : ownerships) {
            if (ownership == null || ownership.getParcel() == null) {
                continue;
            }
            Integer parcelId = ownership.getParcel().getParcelId();
            if (includedParcelIds.add(parcelId)) {
                results.add(toResponse(ownership.getParcel(), ownership));
            }
        }

        List<TransferRequest> relatedTransfers = new ArrayList<>();
        relatedTransfers.addAll(transferRequestRepository.findBySeller_UserId(user.getUserId()));
        relatedTransfers.addAll(transferRequestRepository.findByBuyer_UserId(user.getUserId()));

        Set<Integer> fallbackParcelIds = relatedTransfers.stream()
                .filter(transfer -> transfer != null && transfer.getParcel() != null)
                .filter(transfer -> transfer.getStatus() == TransferRequest.TransferStatus.REJECTED)
                .map(transfer -> transfer.getParcel().getParcelId())
                .filter(parcelId -> parcelId != null && !includedParcelIds.contains(parcelId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Integer parcelId : fallbackParcelIds) {
            LandParcel parcel = parcelRepository.findById(parcelId).orElse(null);
            if (parcel == null) {
                continue;
            }
            LandOwnership ownership = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(parcelId).orElse(null);
            results.add(toResponse(parcel, ownership));
            includedParcelIds.add(parcelId);
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<ParcelResponse> getRejectedParcels(User officer) {
        // Get all rejected transfer requests with eager-loaded parcels
        List<TransferRequest> rejectedTransfers = transferRequestRepository.findByStatusWithParcel(TransferRequest.TransferStatus.REJECTED);
        
        // Collect unique parcels from rejected transfers using a LinkedHashSet to maintain order
        Set<LandParcel> uniqueParcels = new LinkedHashSet<>();
        for (TransferRequest transfer : rejectedTransfers) {
            if (transfer.getParcel() != null) {
                uniqueParcels.add(transfer.getParcel());
            }
        }
        
        log.info("Found {} rejected transfers with {} unique parcels", rejectedTransfers.size(), uniqueParcels.size());
        
        // Convert to ParcelResponse objects
        return uniqueParcels.stream()
                .filter(parcel -> officer == null || jurisdictionAccessService.canAccess(officer, parcel))
                .map(parcel -> {
                    LandOwnership ownership = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId())
                            .orElse(null);
                    return toResponse(parcel, ownership);
                })
                .collect(Collectors.toList());
    }

    public List<LandownerWithParcelsResponse> searchLandowners(String query) {
        if (query == null || query.isBlank()) {
            return java.util.Collections.emptyList();
        }

        String cleaned = query.trim();
        Set<Integer> ownerIds = new LinkedHashSet<>();
        List<LandownerWithParcelsResponse> results = new ArrayList<>();

        parcelRepository.search(cleaned).stream()
                .map(parcel -> ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(LandOwnership::getOwner)
                .filter(owner -> owner != null && owner.getRole() == User.Role.LAND_OWNER)
                .forEach(owner -> ownerIds.add(owner.getUserId()));

        List<User> matchingOwners = userRepository
                .findByRoleAndEmailContainingIgnoreCaseOrRoleAndFullNameContainingIgnoreCaseOrRoleAndNationalIdContainingIgnoreCase(
                        User.Role.LAND_OWNER, cleaned,
                        User.Role.LAND_OWNER, cleaned,
                        User.Role.LAND_OWNER, cleaned);
        matchingOwners.stream()
            .map(User::getUserId)
            .filter(id -> id != null)
            .forEach(ownerIds::add);

        for (Integer ownerId : ownerIds) {
            User owner = userRepository.findById(ownerId)
                    .orElse(null);
            if (owner != null) {
                results.add(LandownerWithParcelsResponse.fromUser(owner, getParcelsByOwner(ownerId)));
            }
        }

        return results;
    }

    public VerifyResponse verifyParcel(String parcelNumber) {
        LandParcel parcel = parcelRepository.findByParcelNumber(parcelNumber).orElse(null);
        VerifyResponse r = new VerifyResponse();

        if (parcel == null) {
            r.setBlockchainVerificationStatus("PARCEL_NOT_FOUND");
            return r;
        }

        LandOwnership current = ownershipRepository
                .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId()).orElse(null);
        boolean chainValid = blockchainService.verifyParcelOnChain(
            parcelNumber, parcel.getDocumentHash());
        blockchainService.recordVerificationEvent(parcelNumber, parcel.getParcelId(), null, chainValid);

        r.setParcelNumber(parcel.getParcelNumber());
        r.setProvince(parcel.getProvince());
        r.setDistrict(parcel.getDistrict());
        r.setLocationAddress(parcel.getLocationAddress());
        r.setAreaSqm(parcel.getAreaSqm());
        r.setLandUse(parcel.getLandUse().name());
        r.setBlockchainHash(parcel.getBlockchainHash());
        r.setDocumentHash(parcel.getDocumentHash());
        r.setRegisteredAt(parcel.getRegisteredAt());
        r.setOwnershipHistory(blockchainService.getOwnershipHistory(parcelNumber));
        r.setBlockchainVerificationStatus(chainValid ? "VERIFIED" : "MISMATCH_DETECTED");

        if (current != null && current.getOwner() != null) {
            r.setCurrentOwnerName(current.getOwner().getFullName());
            String nid = current.getOwner().getNationalId();
            r.setCurrentOwnerMaskedId(nid.length() > 4
                    ? nid.substring(0, 2) + "****" + nid.substring(nid.length() - 2)
                    : "****");
        }
        return r;
    }

    private List<DocumentResponse> getDocumentsForParcel(Integer parcelId, String parcelNumber) {
        return documentRepository.findByRelatedTypeAndRelatedId(Document.RelatedType.PARCEL, parcelId)
                .stream()
                .map(doc -> {
                    DocumentResponse response = new DocumentResponse();
                    response.setId(doc.getDocumentId().longValue());
                    response.setFileName(doc.getFileName());
                    response.setFilePath(doc.getFilePath());
                    response.setDocumentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null);
                    response.setParcelNumber(parcelNumber);
                    response.setCategory(doc.getDocumentType() != null ? doc.getDocumentType().name() : null);
                    response.setUploaderName(doc.getUploadedBy() != null ? doc.getUploadedBy().getFullName() : null);
                    response.setUploadedAt(doc.getUploadedAt());
                    return response;
                })
                .collect(Collectors.toList());
    }

    private List<String> buildRegistrationHistory(LandParcel parcel, LandOwnership ownership) {
        List<String> history = new ArrayList<>();
        if (parcel.getRegisteredAt() != null) {
            history.add("Parcel registered on " + parcel.getRegisteredAt() + ".");
        }
        if (ownership != null) {
            User owner = ownership.getOwner();
            if (owner != null) {
                history.add(String.format("Ownership assigned to %s (%s).", owner.getFullName(), owner.getEmail()));
                history.add(String.format("Owner contact: %s and national ID %s.",
                        owner.getPhoneNumber() != null ? owner.getPhoneNumber() : "N/A",
                        owner.getNationalId() != null ? maskNationalId(owner.getNationalId()) : "N/A"));
            }
            if (ownership.getOwnershipType() != null) {
                history.add("Ownership type: " + ownership.getOwnershipType().name() + ".");
            }
            if (ownership.getAcquisitionMethod() != null) {
                history.add("Acquisition method: " + ownership.getAcquisitionMethod().name() + ".");
            }
            if (ownership.getStartDate() != null) {
                history.add("Ownership start date: " + ownership.getStartDate() + ".");
            }
            if (ownership.getEndDate() != null) {
                history.add("Ownership end date: " + ownership.getEndDate() + ".");
            }
        }
        if (parcel.getTitleDeedFile() != null && !parcel.getTitleDeedFile().isBlank()) {
            history.add("Title deed generated and available for download.");
        } else {
            history.add("Title deed generation pending.");
        }
        if (parcel.getBlockchainHash() != null && !parcel.getBlockchainHash().isBlank()) {
            history.add("Blockchain registration hash recorded: " + parcel.getBlockchainHash() + ".");
        } else {
            history.add("Blockchain registration has not yet been completed.");
        }
        return history;
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() < 5) {
            return nationalId != null ? nationalId : "N/A";
        }
        int len = nationalId.length();
        return nationalId.substring(0, 2) + "****" + nationalId.substring(len - 2);
    }

    private void sendNotification(User recipient, String message,
                                   Notification.NotificationType type,
                                   Integer parcelId, Integer requestId) {
        try {
            notificationRepository.save(Notification.builder()
                    .recipient(recipient).message(message)
                    .notificationType(type)
                    .relatedParcelId(parcelId)
                    .relatedRequestId(requestId)
                    .isRead(false).build());
        } catch (Exception e) {
            log.warn("Failed to save notification: {}", e.getMessage());
        }
    }

    private void enforceJurisdictionAccess(User officer, LandParcel parcel) {
        if (officer != null && !jurisdictionAccessService.canAccess(officer, parcel)) {
            throw new RuntimeException("You are not authorized to access records outside your assigned jurisdiction");
        }
    }

    private void validateOwnerPhoto(MultipartFile photo) {
        String contentType = photo.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Owner photo must be an image");
        }
        if (photo.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Owner photo must be 10 MB or smaller");
        }
    }

    private ParcelResponse toResponse(LandParcel p, LandOwnership ownership) {
        return toResponse(p, ownership, false, false, null);
    }

    private String resolveRegistrationStatus(LandParcel p) {
        if (p == null) return "PENDING";
        if (p.getStatus() == null) return "PENDING";

        return switch (p.getStatus()) {
            case PENDING_TRANSFER -> "PENDING";
            case DISPUTED -> "DISPUTED";
            case ARCHIVED -> "ARCHIVED";
            default -> p.getRegisteredAt() != null ? "REGISTERED" : "PENDING";
        };
    }

    private String resolveVerificationStatus(LandParcel p) {
        if (p == null) return "NOT_SUBMITTED";
        if (p.getBlockchainHash() != null && !p.getBlockchainHash().isBlank()) return "VERIFIED";
        return p.getRegisteredAt() != null ? "PENDING_VERIFICATION" : "NOT_SUBMITTED";
    }

    private ParcelResponse toResponse(LandParcel p, LandOwnership ownership,
                                      boolean ownerAccountCreated, boolean ownerEmailSent,
                                      Integer registeredByOfficerId) {
        ParcelResponse r = new ParcelResponse();
        r.setParcelId(p.getParcelId());
        r.setParcelNumber(p.getParcelNumber());
        r.setTitleDeedNumber(p.getTitleDeedNumber());
        r.setProvince(p.getProvince());
        r.setDistrict(p.getDistrict());
        r.setLocationAddress(p.getLocationAddress());
        r.setGpsLat(p.getGpsLat());
        r.setGpsLng(p.getGpsLng());
        if (p.getGpsLat() != null && !p.getGpsLat().isBlank() && p.getGpsLng() != null && !p.getGpsLng().isBlank()) {
            r.setGpsCoordinates(p.getGpsLat() + ", " + p.getGpsLng());
        }
        r.setAreaSqm(p.getAreaSqm());
        r.setLandUse(p.getLandUse().name());
        if (ownership != null) {
            User owner = ownership.getOwner();
            if (owner != null) {
                r.setOwnerFullName(owner.getFullName());
                r.setOwnerEmail(owner.getEmail());
                r.setOwnerPhone(owner.getPhoneNumber());
                r.setOwnerAddress(owner.getAddress());
                r.setOwnerGender(owner.getGender() != null ? owner.getGender().name() : null);
                r.setOwnerNationalId(owner.getNationalId());
                r.setOwnerDistrict(owner.getDistrict());
                r.setOwnerPhotoPath(p.getOwnerPhotoPath() != null ? p.getOwnerPhotoPath() : owner.getPhotoPath());
            }
            r.setOwnershipType(ownership.getOwnershipType() != null ? ownership.getOwnershipType().name() : null);
            r.setOwnerSignature(ownership.getOwnerSignature());
            r.setAcquisitionMethod(ownership.getAcquisitionMethod() != null ? ownership.getAcquisitionMethod().name() : null);
            r.setOwnershipStartDate(ownership.getStartDate() != null ? ownership.getStartDate().atStartOfDay() : null);
            r.setOwnershipEndDate(ownership.getEndDate() != null ? ownership.getEndDate().atStartOfDay() : null);
        }
        r.setStatus(p.getStatus().name());
        r.setBlockchainHash(p.getBlockchainHash());
        r.setDocumentHash(p.getDocumentHash());
        r.setRegisteredAt(p.getRegisteredAt());
        r.setUpdatedAt(p.getUpdatedAt());
        r.setRegistrationStatus(resolveRegistrationStatus(p));
        r.setVerificationStatus(resolveVerificationStatus(p));
        if (p.getTitleDeedFile() != null && !p.getTitleDeedFile().isBlank()) {
            Path deedPath = Paths.get(uploadDir != null ? uploadDir : "./uploads")
                    .resolve(p.getTitleDeedFile())
                    .toAbsolutePath().normalize();
            if (Files.exists(deedPath)) {
                r.setTitleDeedUrl("/api/landowner/parcels/" + p.getParcelId() + "/title-deed");
            } else {
                log.warn("Title deed file missing on disk for parcel {}: {}", p.getParcelNumber(), deedPath);
            }
        }
        r.setDocuments(getDocumentsForParcel(p.getParcelId(), p.getParcelNumber()));
        r.setRegistrationHistory(buildRegistrationHistory(p, ownership));
        r.setOwnerAccountCreated(ownerAccountCreated);
        r.setOwnerEmailSent(ownerEmailSent);
        r.setOwnerUserId(ownership != null && ownership.getOwner() != null ? ownership.getOwner().getUserId() : null);
        r.setRegisteredByOfficerId(registeredByOfficerId != null
            ? registeredByOfficerId : p.getRegisteredByOfficerId());
        return r;
    }

    // Document management
    public List<DocumentResponse> attachDocuments(String parcelNumber, String category,
            String notes, List<MultipartFile> files, User officer) {
        LandParcel parcel = parcelRepository.findByParcelNumber(parcelNumber)
                .orElseThrow(() -> new RuntimeException("Parcel not found: " + parcelNumber));

        List<DocumentResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                String safeName = parcelNumber.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path uploadPath = Paths.get(uploadDir != null ? uploadDir : "./uploads").toAbsolutePath().normalize();
                Files.createDirectories(uploadPath);
                Path target = uploadPath.resolve(safeName);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                Document document = Document.builder()
                        .relatedType(Document.RelatedType.PARCEL)
                        .relatedId(parcel.getParcelId())
                        .documentType(Document.DocumentType.valueOf(category != null ? category.toUpperCase() : "OTHER"))
                        .fileName(file.getOriginalFilename())
                        .filePath("/uploads/" + safeName)
                        .fileHash(null)
                        .uploadedBy(officer)
                        .build();
                document = documentRepository.save(document);

                DocumentResponse response = new DocumentResponse();
                response.setId(document.getDocumentId().longValue());
                response.setFileName(document.getFileName());
                response.setFilePath(document.getFilePath());
                response.setDocumentType(document.getDocumentType().name());
                response.setParcelNumber(parcelNumber);
                response.setCategory(document.getDocumentType().name());
                response.setNotes(notes);
                response.setUploaderName(officer.getFullName());
                response.setUploadedAt(document.getUploadedAt());
                responses.add(response);
            } catch (IOException e) {
                log.warn("Unable to store document for parcel {}: {}", parcelNumber, e.getMessage());
            }
        }
        return responses;
    }

    public List<DocumentResponse> getRecentDocuments(Integer userId, int limit) {
        return documentRepository.findByUploadedBy_UserIdOrderByUploadedAtDesc(userId).stream()
                .limit(limit)
                .map(doc -> {
                    DocumentResponse response = new DocumentResponse();
                    response.setId(doc.getDocumentId().longValue());
                    response.setFileName(doc.getFileName());
                    response.setFilePath(doc.getFilePath());
                    response.setDocumentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null);
                    response.setUploaderName(doc.getUploadedBy() != null ? doc.getUploadedBy().getFullName() : null);
                    response.setUploadedAt(doc.getUploadedAt());
                    return response;
                }).collect(Collectors.toList());
    }

    public List<DocumentResponse> getFilteredDocuments(Integer userId, String type, String search) {
        Document.DocumentType docType = null;
        if (type != null && !type.isBlank()) {
            try {
                docType = Document.DocumentType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return documentRepository.searchByTypeAndFileName(docType, normalizedSearch).stream()
                .filter(doc -> userId == null || (doc.getUploadedBy() != null && doc.getUploadedBy().getUserId().equals(userId)))
                .map(doc -> {
                    DocumentResponse response = new DocumentResponse();
                    response.setId(doc.getDocumentId().longValue());
                    response.setFileName(doc.getFileName());
                    response.setFilePath(doc.getFilePath());
                    response.setDocumentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null);
                    response.setUploaderName(doc.getUploadedBy() != null ? doc.getUploadedBy().getFullName() : null);
                    response.setUploadedAt(doc.getUploadedAt());
                    return response;
                }).collect(Collectors.toList());
    }

    /**
     * Generate the next sequential parcel number to avoid duplicates.
     * Format: ZM-{PROVECODE}-{YEAR}-{SEQUENCE}
     * @param province the selected province name
     * @return The next parcel number
     */
    @Transactional
    public String generateNextParcelNumber(String province) {
        String code = province != null ? PROVINCE_CODES.getOrDefault(province.trim(), "GEN") : "GEN";
        int year = LocalDate.now().getYear();
        var sequenceId = new ParcelSequenceId(code, year);

        ParcelSequence parcelSequence = parcelSequenceRepository
                .findByProvinceCodeAndSequenceYearForUpdate(code, year)
                .orElseGet(() -> {
                    Long maxSequence = parcelRepository.getMaxParcelSequenceNumberByProvinceAndYear(code, String.valueOf(year));
                    return ParcelSequence.builder()
                            .id(sequenceId)
                            .lastSequence(maxSequence != null ? maxSequence : 0)
                            .build();
                });

        long nextSequence = parcelSequence.getLastSequence() + 1;
        parcelSequence.setLastSequence(nextSequence);
        parcelSequenceRepository.save(parcelSequence);

        String next = String.format("ZM-%s-%s-%05d", code, year, nextSequence);
        log.info("Generating parcel number for province={} year={} lastSequence={} -> {}", code, year, parcelSequence.getLastSequence(), next);
        return next;
    }

    public LandParcel getParcelForOwnerByNumber(User owner, String parcelNumber) {
        LandParcel parcel = parcelRepository.findByParcelNumber(parcelNumber)
                .orElseThrow(() -> new RuntimeException("Parcel not found: " + parcelNumber));
        LandOwnership ownership = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId())
                .orElseThrow(() -> new RuntimeException("Parcel ownership not found for parcel: " + parcelNumber));
        if (!ownership.getOwner().getUserId().equals(owner.getUserId())) {
            throw new RuntimeException("Parcel does not belong to the authenticated user");
        }
        return parcel;
    }

    public List<ParcelResponse> getApprovedParcels() {
        return getApprovedParcels(false, null);
    }

    /**
     * Get approved parcels. If includePartial is true, include transfers that have only
     * Level-1 approval (APPROVED_L1) as partially approved results.
     */
    public List<ParcelResponse> getApprovedParcels(boolean includePartial, User officer) {
        List<TransferRequest> transfers;
        if (includePartial) {
            transfers = transferRequestRepository.findByStatusIn(
                    List.of(TransferRequest.TransferStatus.APPROVED, TransferRequest.TransferStatus.APPROVED_L1)
            );
        } else {
            transfers = transferRequestRepository.findByStatus(TransferRequest.TransferStatus.APPROVED);
        }
        return transfers.stream()
                .filter(transfer -> transfer.getParcel() != null)
                .filter(transfer -> officer == null || jurisdictionAccessService.canAccess(officer, transfer.getParcel()))
                .filter(transfer -> transfer.getSeller() != null && transfer.getSeller().getRole() == User.Role.LAND_OWNER)
                .map(transfer -> {
                    LandParcel parcel = transfer.getParcel();
                    LandOwnership ownership = ownershipRepository
                            .findByParcel_ParcelIdAndIsCurrentTrue(parcel.getParcelId())
                            .orElse(null);
                    ParcelResponse response = toResponse(parcel, ownership);
                    // set approval metadata depending on whether fully approved or partial
                    boolean fullyApproved = hasCompleteApprovalChain(transfer);
                    response.setStatus("APPROVED");
                    response.setApprovalStatus(fullyApproved ? "FULLY_APPROVED" : "PARTIALLY_APPROVED");
                    response.setApprovalStage(fullyApproved ? "FINAL_APPROVED" : "L1_APPROVED");
                    response.setApprovalHistory(buildApprovalHistory(transfer));
                    return response;
                })
                .collect(Collectors.toList());
    }

    private boolean hasCompleteApprovalChain(TransferRequest transfer) {
        List<ApprovalWorkflow> approvals = approvalWorkflowRepository.findByRequest_RequestId(transfer.getRequestId());
        boolean hasLevel1Approval = approvals.stream()
                .anyMatch(a -> a.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED && a.getApprovalLevel() != null && a.getApprovalLevel() >= 1);
        boolean hasLevel2Approval = approvals.stream()
                .anyMatch(a -> a.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED && a.getApprovalLevel() != null && a.getApprovalLevel() >= 2);
        return hasLevel1Approval && hasLevel2Approval;
    }

    private List<String> buildApprovalHistory(TransferRequest transfer) {
        return approvalWorkflowRepository.findByRequest_RequestId(transfer.getRequestId()).stream()
                .filter(a -> a.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED)
                .sorted(Comparator.comparing(ApprovalWorkflow::getActionedAt))
                .map(this::formatApprovalEntry)
                .collect(Collectors.toList());
    }

    private String formatApprovalEntry(ApprovalWorkflow approval) {
        String officerName = approval.getOfficer() != null && approval.getOfficer().getFullName() != null
                ? approval.getOfficer().getFullName()
                : "Unknown officer";
        String stageName = approval.getApprovalLevel() != null && approval.getApprovalLevel() >= 2
                ? "Final authority"
                : "Officer level " + approval.getApprovalLevel();
        return stageName + " approved by " + officerName;
    }

}

