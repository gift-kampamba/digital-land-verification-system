package com.landverification.controller;

import com.landverification.dto.*;
import com.landverification.dto.FlagRequest;
import com.landverification.model.BlockchainAuditLog;
import com.landverification.model.ApprovalWorkflow;
import com.landverification.model.LandParcel;
import com.landverification.model.TransferRequest;
import com.landverification.model.User;
import com.landverification.repository.BlockchainAuditLogRepository;
import com.landverification.repository.LandParcelRepository;
import com.landverification.repository.LandOwnershipRepository;
import com.landverification.repository.NotificationRepository;
import com.landverification.repository.TransferRequestRepository;
import com.landverification.repository.ApprovalWorkflowRepository;
import com.landverification.repository.UserRepository;
import com.landverification.service.TransferService;
import com.landverification.service.LandParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/senior", "/api/senior-officer"})
@RequiredArgsConstructor
@Slf4j
public class SeniorOfficerController {

        private final TransferService transferService;
        private final LandParcelService parcelService;
    private final UserRepository userRepository;
    private final BlockchainAuditLogRepository auditLogRepository;
    private final LandParcelRepository landParcelRepository;
        private final LandOwnershipRepository ownershipRepository;
    private final TransferRequestRepository transferRequestRepository;
        private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final NotificationRepository notificationRepository;
        private final com.landverification.service.BlockchainService blockchainService;
        private final com.landverification.config.AppProperties appProperties;
        private final com.landverification.service.JurisdictionAccessService jurisdictionAccessService;

        @GetMapping("/parcel/{parcelNumber}")
        public ResponseEntity<ApiResponse<ParcelResponse>> getParcel(
                        @PathVariable String parcelNumber, Authentication auth) {
                User officer = getUser(auth);
                return ResponseEntity.ok(ApiResponse.ok("Parcel details",
                                parcelService.getParcelByNumber(parcelNumber, officer)));
        }

    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<TransferResponse>>> getTransfers(
            @RequestParam(required = false) String status, Authentication auth) {
        User officer = getUser(auth);
        if (status != null) {
            if ("FLAGGED".equals(status)) {
                return ResponseEntity.ok(ApiResponse.ok("Flagged transfers",
                        transferService.getFlaggedTransfers(officer)));
            }
            return ResponseEntity.ok(ApiResponse.ok("Transfers",
                    transferService.getTransfersByStatus(
                            TransferRequest.TransferStatus.valueOf(status), officer)));
        }
        return ResponseEntity.ok(ApiResponse.ok("Senior officer transfers",
                transferService.getSeniorOfficerTransfers(officer)));
    }

        @GetMapping("/audit-log")
        public ResponseEntity<ApiResponse<List<Map<String, Object>>>> auditLog(Authentication auth) {
                User officer = getUser(auth);
                var logs = auditLogRepository.findAllByOrderByRecordedAtDesc().stream()
                                .filter(log -> log.getRelatedParcelId() != null)
                                .filter(log -> landParcelRepository.findById(log.getRelatedParcelId())
                                        .map(parcel -> jurisdictionAccessService.canAccess(officer, parcel)).orElse(false))
                                .toList();
                List<Map<String, Object>> out = new ArrayList<>();
                for (var log : logs) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("logId", log.getLogId());
                        m.put("transactionHash", log.getTransactionHash());
                        m.put("blockNumber", log.getBlockNumber());
                        m.put("contractAddress", log.getContractAddress());
                        m.put("eventType", log.getEventType() != null ? log.getEventType().name() : null);
                        m.put("relatedParcelId", log.getRelatedParcelId());
                        m.put("relatedRequestId", log.getRelatedRequestId());
                        m.put("initiatedBy", log.getInitiatedBy());
                        m.put("gasUsed", log.getGasUsed());
                        m.put("payloadHash", log.getPayloadHash());
                        m.put("recordedAt", log.getRecordedAt());

                        // Enrich with owner summary if parcel exists
                        try {
                                if (log.getRelatedParcelId() != null) {
                                        var currentOpt = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(log.getRelatedParcelId());
                                        if (currentOpt.isPresent()) {
                                                var curr = currentOpt.get();
                                                m.put("currentOwnerName", curr.getOwner() != null ? curr.getOwner().getFullName() : null);
                                        }
                                }
                        } catch (Exception ex) {
                                // ignore
                        }

                        out.add(m);
                }
                return ResponseEntity.ok(ApiResponse.ok("Blockchain audit log", out));
        }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> notifications(Authentication auth) {
        User officer = getUser(auth);
        List<NotificationResponse> responses = notificationRepository
                .findByRecipient_UserIdOrderByCreatedAtDesc(officer.getUserId())
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Notifications", responses));
    }

    @GetMapping("/notifications/count")
    public ResponseEntity<ApiResponse<Long>> unreadNotificationCount(Authentication auth) {
        User officer = getUser(auth);
        long count = notificationRepository.countByRecipient_UserIdAndIsReadFalse(officer.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Unread notification count", count));
    }

        @GetMapping("/parcels/{id}/title-deed")
        public ResponseEntity<org.springframework.core.io.Resource> getTitleDeedFile(@PathVariable Integer id, Authentication auth) {
                User officer = getUser(auth);
                var parcelOpt = landParcelRepository.findById(id);
                if (parcelOpt.isEmpty()) return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
                var parcel = parcelOpt.get();
                if (!jurisdictionAccessService.canAccess(officer, parcel)) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                }
                try {
                        java.nio.file.Path uploadRoot = java.nio.file.Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize();
                        java.nio.file.Path path = resolveTitleDeedPath(parcel, uploadRoot);
                        if (path == null || !java.nio.file.Files.exists(path)) return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
                        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(path.toFile());
                        return ResponseEntity.ok()
                                        .contentType(resolveMediaType(path))
                                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"title-deed.pdf\"")
                                        .body(resource);
                } catch (Exception e) {
                        log.warn("Failed to read title deed file for parcel {}: {}", id, e.getMessage());
                        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
        }

        private java.nio.file.Path resolveTitleDeedPath(LandParcel parcel, java.nio.file.Path uploadRoot)
                        throws java.io.IOException {
                if (parcel.getTitleDeedFile() != null && !parcel.getTitleDeedFile().isBlank()) {
                        java.nio.file.Path storedPath = uploadRoot.resolve(parcel.getTitleDeedFile()).normalize();
                        if (storedPath.startsWith(uploadRoot) && java.nio.file.Files.exists(storedPath)) return storedPath;
                }
                java.nio.file.Path deedFolder = uploadRoot.resolve("title-deeds").normalize();
                if (!java.nio.file.Files.isDirectory(deedFolder) || parcel.getParcelNumber() == null) return null;
                try (java.nio.file.DirectoryStream<java.nio.file.Path> files = java.nio.file.Files.newDirectoryStream(
                                deedFolder, "title-deed-" + parcel.getParcelNumber() + "-*")) {
                        for (java.nio.file.Path candidate : files) {
                                if (java.nio.file.Files.isRegularFile(candidate)) return candidate;
                        }
                }
                return null;
        }

        private org.springframework.http.MediaType resolveMediaType(java.nio.file.Path path) {
                String contentType = org.springframework.http.MediaTypeFactory.getMediaType(path.getFileName().toString())
                                .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).toString();
                return org.springframework.http.MediaType.parseMediaType(contentType);
        }

        @GetMapping("/parcels/{id}/title-deed/view")
        public ResponseEntity<String> viewTitleDeed(@PathVariable Integer id, Authentication auth) {
                User officer = getUser(auth);
                var parcelOpt = landParcelRepository.findById(id);
                if (parcelOpt.isEmpty()) return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("Not found");
                var parcel = parcelOpt.get();
                if (!jurisdictionAccessService.canAccess(officer, parcel)) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body("Forbidden");
                }
                String fileEndpoint = String.format("%s/api/senior/parcels/%d/title-deed", appProperties.getBaseUrl(), id);
                String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Title Deed Viewer</title></head><body style=\"margin:0;height:100vh;\">"
                                + "<iframe src=\"" + fileEndpoint + "\" style=\"width:100%;height:100%;border:0;\"></iframe>"
                                + "</body></html>";
                return ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8").body(html);
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

    @GetMapping("/monthly-report")
    public ResponseEntity<ApiResponse<MonthlyReportResponse>> monthlyReport(
                        @RequestParam(required = false) String period, Authentication auth) {
        ReportPeriod reportPeriod = resolveReportPeriod(period);
                User officer = getUser(auth);

        try {
            LocalDateTime start = reportPeriod.start.atStartOfDay();
            LocalDateTime end = reportPeriod.end.atStartOfDay();
            YearMonth targetMonth = YearMonth.from(reportPeriod.start);

            List<LandParcel> parcels = landParcelRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(parcel -> jurisdictionAccessService.canAccess(officer, parcel))
                    .filter(parcel -> parcel.getRegisteredAt() != null && !parcel.getRegisteredAt().isBefore(start) && parcel.getRegisteredAt().isBefore(end))
                    .toList();

            List<TransferRequest> transfers = transferRequestRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(request -> request.getParcel() != null && jurisdictionAccessService.canAccess(officer, request.getParcel()))
                    .filter(request -> request.getSubmittedAt() != null && !request.getSubmittedAt().isBefore(start) && request.getSubmittedAt().isBefore(end))
                    .toList();

            List<TransferRequest> accessibleTransfers = transferRequestRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(request -> request.getParcel() != null && jurisdictionAccessService.canAccess(officer, request.getParcel()))
                    .toList();

            List<TransferRequest> flaggedTransfers = transfers.stream()
                    .filter(Objects::nonNull)
                    .filter(TransferRequest::isFlagged)
                    .toList();

            List<BlockchainAuditLog> logs = auditLogRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(log -> log.getRelatedParcelId() != null && landParcelRepository.findById(log.getRelatedParcelId())
                            .map(parcel -> jurisdictionAccessService.canAccess(officer, parcel)).orElse(false))
                    .filter(log -> log.getRecordedAt() != null && !log.getRecordedAt().isBefore(start) && log.getRecordedAt().isBefore(end))
                    .toList();

            List<String> trendMonths = new ArrayList<>();
            List<Long> registrations = new ArrayList<>();
            List<Long> transferSeries = new ArrayList<>();
            List<Long> approvedSeries = new ArrayList<>();
            List<Long> flaggedSeries = new ArrayList<>();
            List<Long> rejectedSeries = new ArrayList<>();
            List<ApprovalWorkflow> approvalHistory = approvalWorkflowRepository.findAll();
            for (int i = 5; i >= 0; i--) {
                YearMonth month = targetMonth.minusMonths(i);
                trendMonths.add(month.format(DateTimeFormatter.ofPattern("MMM yyyy")));
                LocalDateTime monthStart = month.atDay(1).atStartOfDay();
                LocalDateTime nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay();
                registrations.add(landParcelRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(parcel -> jurisdictionAccessService.canAccess(officer, parcel))
                        .filter(parcel -> parcel.getRegisteredAt() != null && !parcel.getRegisteredAt().isBefore(monthStart) && parcel.getRegisteredAt().isBefore(nextMonthStart))
                        .count());
                transferSeries.add(transferRequestRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(request -> request.getParcel() != null && jurisdictionAccessService.canAccess(officer, request.getParcel()))
                        .filter(request -> request.getSubmittedAt() != null && !request.getSubmittedAt().isBefore(monthStart) && request.getSubmittedAt().isBefore(nextMonthStart))
                        .count());
                List<TransferRequest> monthTransfers = transferRequestRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(request -> request.getParcel() != null && jurisdictionAccessService.canAccess(officer, request.getParcel()))
                        .filter(request -> request.getSubmittedAt() != null && !request.getSubmittedAt().isBefore(monthStart) && request.getSubmittedAt().isBefore(nextMonthStart))
                        .toList();
                Set<Integer> approvedRequestIds = new HashSet<>();
                transferRequestRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(request -> request.getRequestId() != null
                                && request.getStatus() == TransferRequest.TransferStatus.APPROVED
                                && request.getParcel() != null
                                && jurisdictionAccessService.canAccess(officer, request.getParcel())
                                && request.getUpdatedAt() != null
                                && !request.getUpdatedAt().isBefore(monthStart)
                                && request.getUpdatedAt().isBefore(nextMonthStart))
                        .forEach(request -> approvedRequestIds.add(request.getRequestId()));
                approvalHistory.stream()
                        .filter(Objects::nonNull)
                        .filter(approval -> approval.getAction() == ApprovalWorkflow.ApprovalAction.APPROVED)
                        .filter(approval -> approval.getApprovalLevel() != null && approval.getApprovalLevel() == 2)
                        .filter(approval -> approval.getRequest() != null && approval.getRequest().getParcel() != null
                                && jurisdictionAccessService.canAccess(officer, approval.getRequest().getParcel()))
                        .filter(approval -> approval.getActionedAt() != null
                                && !approval.getActionedAt().isBefore(monthStart)
                                && approval.getActionedAt().isBefore(nextMonthStart))
                        .forEach(approval -> approvedRequestIds.add(approval.getRequest().getRequestId()));
                approvedSeries.add((long) approvedRequestIds.size());
                flaggedSeries.add(transferRequestRepository.findAll().stream()
                        .filter(Objects::nonNull)
                        .filter(request -> request.getParcel() != null && jurisdictionAccessService.canAccess(officer, request.getParcel()))
                        .filter(TransferRequest::isFlagged)
                        .filter(request -> request.getFlaggedAt() != null
                                && !request.getFlaggedAt().isBefore(monthStart)
                                && request.getFlaggedAt().isBefore(nextMonthStart))
                        .count());
                rejectedSeries.add(approvalHistory.stream()
                        .filter(Objects::nonNull)
                        .filter(approval -> approval.getAction() == ApprovalWorkflow.ApprovalAction.REJECTED)
                        .filter(approval -> approval.getRequest() != null && approval.getRequest().getParcel() != null
                                && jurisdictionAccessService.canAccess(officer, approval.getRequest().getParcel()))
                        .filter(approval -> approval.getActionedAt() != null
                                && !approval.getActionedAt().isBefore(monthStart)
                                && approval.getActionedAt().isBefore(nextMonthStart))
                        .count());
            }

            Map<String, Long> districtCounts = parcels.stream()
                    .filter(Objects::nonNull)
                    .filter(parcel -> parcel.getDistrict() != null && !parcel.getDistrict().isBlank())
                    .collect(Collectors.groupingBy(LandParcel::getDistrict, LinkedHashMap::new, Collectors.counting()));
            List<String> districtLabels = new ArrayList<>(districtCounts.keySet().stream().limit(5).toList());
            List<Long> districtValues = new ArrayList<>(districtLabels.stream().map(districtCounts::get).toList());
            if (districtLabels.size() < 5) {
                List<String> fallback = List.of("Lusaka", "Kafue", "Chongwe", "Ndola", "Mufulira");
                for (String name : fallback) {
                    if (!districtLabels.contains(name)) {
                        districtLabels.add(name);
                        districtValues.add(0L);
                    }
                }
                districtLabels = districtLabels.stream().limit(5).toList();
                districtValues = districtValues.stream().limit(5).toList();
            }

            Map<LandParcel.LandUse, Long> landUseCounts = parcels.stream()
                    .filter(Objects::nonNull)
                    .filter(parcel -> parcel.getLandUse() != null)
                    .collect(Collectors.groupingBy(LandParcel::getLandUse, Collectors.counting()));
            List<String> landUseLabels = List.of("Residential", "Commercial", "Agricultural");
            List<Long> landUseValues = List.of(
                    landUseCounts.getOrDefault(LandParcel.LandUse.RESIDENTIAL, 0L),
                    landUseCounts.getOrDefault(LandParcel.LandUse.COMMERCIAL, 0L),
                    landUseCounts.getOrDefault(LandParcel.LandUse.AGRICULTURAL, 0L));

            List<MonthlyReportResponse.WeeklyTransfer> weeklyTransfers = List.<MonthlyReportResponse.WeeklyTransfer>of(
                    new MonthlyReportResponse.WeeklyTransfer("W1", "1–7 " + targetMonth.format(DateTimeFormatter.ofPattern("MMM")), transfers.stream().filter(t -> t != null && t.getSubmittedAt() != null && t.getSubmittedAt().toLocalDate().getDayOfMonth() <= 7).count()),
                    new MonthlyReportResponse.WeeklyTransfer("W2", "8–14 " + targetMonth.format(DateTimeFormatter.ofPattern("MMM")), transfers.stream().filter(t -> t != null && t.getSubmittedAt() != null && t.getSubmittedAt().toLocalDate().getDayOfMonth() >= 8 && t.getSubmittedAt().toLocalDate().getDayOfMonth() <= 14).count()),
                    new MonthlyReportResponse.WeeklyTransfer("W3", "15–21 " + targetMonth.format(DateTimeFormatter.ofPattern("MMM")), transfers.stream().filter(t -> t != null && t.getSubmittedAt() != null && t.getSubmittedAt().toLocalDate().getDayOfMonth() >= 15 && t.getSubmittedAt().toLocalDate().getDayOfMonth() <= 21).count()),
                    new MonthlyReportResponse.WeeklyTransfer("W4", "22–31 " + targetMonth.format(DateTimeFormatter.ofPattern("MMM")), transfers.stream().filter(t -> t != null && t.getSubmittedAt() != null && t.getSubmittedAt().toLocalDate().getDayOfMonth() >= 22).count())
            );

            List<MonthlyReportResponse.MetricRow> registrationRows = List.of(
                    new MonthlyReportResponse.MetricRow("New Parcels Registered", String.format("%,d", parcels.size()), null, true),
                    new MonthlyReportResponse.MetricRow("Residential Parcels", String.format("%,d", parcels.stream().filter(p -> p != null && p.getLandUse() == LandParcel.LandUse.RESIDENTIAL).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Commercial Parcels", String.format("%,d", parcels.stream().filter(p -> p != null && p.getLandUse() == LandParcel.LandUse.COMMERCIAL).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Agricultural Parcels", String.format("%,d", parcels.stream().filter(p -> p != null && p.getLandUse() == LandParcel.LandUse.AGRICULTURAL).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Total Land Area Registered", String.format("%.2f ha", parcels.stream().filter(Objects::nonNull).mapToDouble(parcel -> parcel.getAreaSqm() == null ? 0 : parcel.getAreaSqm().doubleValue() / 10000.0).sum()), null, true)
            );

            long approvedCount = approvalHistory.stream().filter(Objects::nonNull)
                    .filter(approval -> isSeniorActionByOfficer(approval, officer, start, end, ApprovalWorkflow.ApprovalAction.APPROVED)).count();
            long rejectedCount = approvalHistory.stream().filter(Objects::nonNull)
                    .filter(approval -> isSeniorActionByOfficer(approval, officer, start, end, ApprovalWorkflow.ApprovalAction.REJECTED)).count();

            List<MonthlyReportResponse.MetricRow> transferRows = List.of(
                    new MonthlyReportResponse.MetricRow("Transfer Requests", String.format("%,d", transfers.size()), null, true),
                    new MonthlyReportResponse.MetricRow("Approved Transfers", String.format("%,d", approvedCount), null, true),
                    new MonthlyReportResponse.MetricRow("Pending Transfers", String.format("%,d", transfers.stream().filter(t -> t != null && (t.getStatus() == TransferRequest.TransferStatus.SUBMITTED || t.getStatus() == TransferRequest.TransferStatus.UNDER_REVIEW)).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Rejected Transfers", String.format("%,d", rejectedCount), null, false),
                    new MonthlyReportResponse.MetricRow("Average Processing Time", averageProcessingDays(transfers), null, true)
            );

            long decidedCount = approvedCount + rejectedCount;
            List<MonthlyReportResponse.MetricRow> approvalRows = List.of(
                    new MonthlyReportResponse.MetricRow("Approved Transfers", formatPercent(approvedCount, decidedCount), null, true),
                    new MonthlyReportResponse.MetricRow("Rejected Transfers", formatPercent(rejectedCount, decidedCount), null, false),
                    new MonthlyReportResponse.MetricRow("Overall Decision Rate", formatPercent(decidedCount, transfers.size()), null, true)
            );

            List<MonthlyReportResponse.OfficerPerformanceRow> officerRows = userRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(user -> user.getRole() == User.Role.LAND_OFFICER)
                    .filter(user -> officer.getProvince() != null && officer.getProvince().equalsIgnoreCase(user.getProvince()))
                    .map(user -> {
                        long registrationsCount = parcels.stream().filter(parcel -> parcel != null && parcel.getRegisteredAt() != null && parcel.getRegisteredAt().getMonthValue() == targetMonth.getMonthValue() && parcel.getRegisteredAt().getYear() == targetMonth.getYear()).count();
                        long transfersCount = transfers.stream().filter(t -> t != null && t.getSeller() != null && t.getSeller().getUserId() != null && t.getSeller().getUserId().equals(user.getUserId())).count();
                        long pendingCount = transfers.stream().filter(t -> t != null && t.getSeller() != null && t.getSeller().getUserId() != null && t.getSeller().getUserId().equals(user.getUserId()) && (t.getStatus() == TransferRequest.TransferStatus.SUBMITTED || t.getStatus() == TransferRequest.TransferStatus.UNDER_REVIEW)).count();
                        return MonthlyReportResponse.OfficerPerformanceRow.builder()
                                .officer(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail())
                                .registrations(registrationsCount)
                                .transfers(transfersCount)
                                .pending(pendingCount)
                                .performance(Math.min(5, Math.max(3, (int) (registrationsCount / 10 + transfersCount / 20))))
                                .build();
                    })
                    .sorted(Comparator.comparingLong(MonthlyReportResponse.OfficerPerformanceRow::getRegistrations).reversed())
                    .limit(5)
                    .toList();

            List<MonthlyReportResponse.MetricRow> blockchainRows = List.of(
                    new MonthlyReportResponse.MetricRow("Blockchain Transactions Recorded", String.format("%,d", logs.size()), null, true),
                    new MonthlyReportResponse.MetricRow("Smart Contracts Executed", String.format("%,d", logs.stream().filter(log -> log != null && log.getEventType() != null).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Hash Verifications", String.format("%,d", logs.stream().filter(log -> log != null && log.getPayloadHash() != null && !log.getPayloadHash().isBlank()).count()), null, true),
                    new MonthlyReportResponse.MetricRow("Integrity Verification Success Rate", formatPercent(logs.stream().filter(log -> Boolean.TRUE.equals(log.getOnChain())).count(), logs.size()), null, true),
                    new MonthlyReportResponse.MetricRow("Failed Transactions", String.format("%,d", logs.stream().filter(log -> Boolean.FALSE.equals(log.getOnChain())).count()), null, true)
            );

            List<MonthlyReportResponse.MetricRow> fraudRows = List.of(
                    new MonthlyReportResponse.MetricRow("Flagged Cases", String.format("%,d", flaggedTransfers.size()), null, true),
                    new MonthlyReportResponse.MetricRow("Resolved Cases", String.format("%,d", approvedCount + rejectedCount), null, true)
            );

            List<MonthlyReportResponse.ActivityLogRow> activityLog = new ArrayList<>();
            approvalHistory.stream()
                    .filter(Objects::nonNull)
                    .filter(approval -> approval.getRequest() != null
                            && approval.getApprovalLevel() != null && approval.getApprovalLevel() == 2
                            && approval.getOfficer() != null
                            && approval.getOfficer().getUserId().equals(officer.getUserId())
                            && approval.getActionedAt() != null
                            && !approval.getActionedAt().isBefore(start)
                            && approval.getActionedAt().isBefore(end))
                    .forEach(approval -> activityLog.add(activityRow(approval.getRequest(), approval.getAction().name(),
                            approval.getActionedAt(), officer.getFullName(), false)));

            accessibleTransfers.stream()
                    .filter(request -> request.getFlaggedBy() != null
                            && request.getFlaggedBy().getUserId().equals(officer.getUserId())
                            && request.getFlaggedAt() != null
                            && !request.getFlaggedAt().isBefore(start)
                            && request.getFlaggedAt().isBefore(end))
                    .forEach(request -> activityLog.add(activityRow(request, "FLAGGED", request.getFlaggedAt(),
                            officer.getFullName(), false)));

            logs.stream()
                    .filter(log -> log.getInitiatedBy() != null && log.getInitiatedBy().equals(officer.getUserId()))
                    .forEach(log -> {
                        TransferRequest request = log.getRelatedRequestId() == null ? null
                                : transferRequestRepository.findById(log.getRelatedRequestId()).orElse(null);
                        if (request != null) {
                            activityLog.add(activityRow(request,
                                    log.getEventType() != null ? log.getEventType().name() : "BLOCKCHAIN_EVENT",
                                    log.getRecordedAt(), officer.getFullName(), true));
                        }
                    });
            activityLog.sort(Comparator.comparing(MonthlyReportResponse.ActivityLogRow::getActivityAt).reversed());

            MonthlyReportResponse response = MonthlyReportResponse.builder()
                    .reportPeriod(reportPeriod.periodKey)
                    .periodLabel(reportPeriod.label)
                    .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .reportId("RPT-" + reportPeriod.periodKey)
                    .dateRange(reportPeriod.label + " — " + reportPeriod.start.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + " – " + reportPeriod.end.minusDays(1).format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    .trendMonths(trendMonths)
                    .trendData(MonthlyReportResponse.TrendData.builder().registrations(registrations).transfers(transferSeries).approved(approvedSeries).flagged(flaggedSeries).rejected(rejectedSeries).build())
                    .districtData(MonthlyReportResponse.DistrictData.builder().labels(districtLabels).values(districtValues).build())
                    .landUseData(MonthlyReportResponse.LandUseData.builder().labels(landUseLabels).values(landUseValues).build())
                    .transfersByWeek(weeklyTransfers)
                    .registrationRows(registrationRows)
                    .transferRows(transferRows)
                    .approvalRows(approvalRows)
                    .officerRows(officerRows)
                    .blockchainRows(blockchainRows)
                    .fraudRows(fraudRows)
                    .activityLog(activityLog)
                    .executiveSummary(String.format("During %s, %d new parcels were registered and %d transfer requests were processed. Approval performance remained strong with %d flagged cases under review.",
                            targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")), parcels.size(), transfers.size(), flaggedTransfers.size()))
                    .build();

            return ResponseEntity.ok(ApiResponse.ok("Monthly report generated", response));
        } catch (Exception ex) {
            ex.printStackTrace();
            MonthlyReportResponse fallback = MonthlyReportResponse.builder()
                    .reportPeriod(reportPeriod.periodKey)
                    .periodLabel(reportPeriod.label)
                    .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .reportId("RPT-" + reportPeriod.periodKey)
                    .dateRange(reportPeriod.label + " — " + reportPeriod.start.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + " – " + reportPeriod.end.minusDays(1).format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    .trendMonths(List.of())
                    .trendData(MonthlyReportResponse.TrendData.builder().registrations(List.of()).transfers(List.of()).flagged(List.of()).build())
                    .districtData(MonthlyReportResponse.DistrictData.builder().labels(List.of()).values(List.of()).build())
                    .landUseData(MonthlyReportResponse.LandUseData.builder().labels(List.of()).values(List.of()).build())
                    .transfersByWeek(List.of())
                    .registrationRows(List.of())
                    .transferRows(List.of())
                    .approvalRows(List.of())
                    .officerRows(List.of())
                    .blockchainRows(List.of())
                    .fraudRows(List.of())
                    .activityLog(List.of())
                    .executiveSummary("The monthly report is temporarily unavailable. Please try again shortly.")
                    .build();
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("Monthly report generated with fallback data", fallback));
        }
    }

        private static MonthlyReportResponse.ActivityLogRow activityRow(TransferRequest request, String action,
                        LocalDateTime activityAt, String officerName, boolean blockchain) {
                return MonthlyReportResponse.ActivityLogRow.builder()
                                .reference("TRF-" + request.getRequestId())
                                .action(action.replace('_', ' '))
                                .type("Transfer")
                                .district(request.getParcel() != null && request.getParcel().getDistrict() != null
                                                ? request.getParcel().getDistrict() : "Unknown")
                                .officer(officerName != null && !officerName.isBlank() ? officerName : "System")
                                .date(activityAt != null ? activityAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "Unknown")
                                .activityAt(activityAt)
                                .status(request.getStatus() != null ? request.getStatus().name() : "UNKNOWN")
                                .blockchain(blockchain || (request.getBlockchainTxHash() != null && !request.getBlockchainTxHash().isBlank()))
                                .build();
        }

        private static boolean isSeniorActionByOfficer(ApprovalWorkflow approval, User officer,
                        LocalDateTime start, LocalDateTime end, ApprovalWorkflow.ApprovalAction action) {
                return approval.getApprovalLevel() != null && approval.getApprovalLevel() == 2
                                && approval.getAction() == action
                                && approval.getOfficer() != null
                                && approval.getOfficer().getUserId().equals(officer.getUserId())
                                && approval.getActionedAt() != null
                                && !approval.getActionedAt().isBefore(start)
                                && approval.getActionedAt().isBefore(end);
        }

        private static String formatPercent(long numerator, long denominator) {
                if (denominator <= 0) return "0%";
                return String.format("%.0f%%", numerator * 100.0 / denominator);
        }

        private static String averageProcessingDays(List<TransferRequest> transfers) {
                List<Long> durations = transfers.stream()
                                .filter(Objects::nonNull)
                                .filter(transfer -> transfer.getSubmittedAt() != null && transfer.getUpdatedAt() != null)
                                .map(transfer -> java.time.Duration.between(transfer.getSubmittedAt(), transfer.getUpdatedAt()).toHours() / 24)
                                .toList();
                if (durations.isEmpty()) return "0 Days";
                return String.format("%.1f Days", durations.stream().mapToLong(Long::longValue).average().orElse(0));
        }

        private static final class ReportPeriod {
        private final LocalDate start;
        private final LocalDate end;
        private final String label;
        private final String periodKey;

        ReportPeriod(LocalDate start, LocalDate end, String label, String periodKey) {
            this.start = start;
            this.end = end;
            this.label = label;
            this.periodKey = periodKey;
        }
    }

    private ReportPeriod resolveReportPeriod(String period) {
        LocalDate now = LocalDate.now();
        if (period == null || period.isBlank()) {
            YearMonth currentMonth = YearMonth.from(now);
            LocalDate start = currentMonth.atDay(1);
            LocalDate end = start.plusMonths(1);
            return new ReportPeriod(start, end, currentMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")), currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }

        String normalized = period.trim().toUpperCase();
        try {
            YearMonth month = YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM"));
            LocalDate start = month.atDay(1);
            LocalDate end = start.plusMonths(1);
            return new ReportPeriod(start, end, month.format(DateTimeFormatter.ofPattern("MMM yyyy")), normalized);
        } catch (Exception ex) {
            if (normalized.matches("\\d{4}-Q[1-4]")) {
                int year = Integer.parseInt(normalized.substring(0, 4));
                int quarter = Integer.parseInt(normalized.substring(6, 7));
                int monthValue = (quarter - 1) * 3 + 1;
                LocalDate start = LocalDate.of(year, monthValue, 1);
                LocalDate end = start.plusMonths(3);
                return new ReportPeriod(start, end, "Q" + quarter + " " + year, normalized);
            }
            if (normalized.matches("\\d{4}")) {
                int year = Integer.parseInt(normalized);
                LocalDate start = LocalDate.of(year, 1, 1);
                LocalDate end = start.plusYears(1);
                return new ReportPeriod(start, end, String.valueOf(year), normalized);
            }
            YearMonth currentMonth = YearMonth.from(now);
            LocalDate start = currentMonth.atDay(1);
            LocalDate end = start.plusMonths(1);
            return new ReportPeriod(start, end, currentMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")), currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
    }

        @GetMapping("/audit-log/{id}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> auditLogById(
                @PathVariable Integer id, Authentication auth) {
                User officer = getUser(auth);
                return auditLogRepository.findById(id)
                                .filter(log -> log.getRelatedParcelId() != null
                                        && landParcelRepository.findById(log.getRelatedParcelId())
                                                .map(parcel -> jurisdictionAccessService.canAccess(officer, parcel))
                                                .orElse(false))
                                .map(log -> {
                                        Map<String, Object> evidence = Map.of();
                                        try {
                                                if (log.getTransactionHash() != null && !log.getTransactionHash().isBlank()) {
                                                        evidence = blockchainService.fetchTransactionEvidence(log.getTransactionHash());
                                                }
                                        } catch (Exception ex) {
                                                evidence = Map.of("available", false, "error", ex.getMessage());
                                        }

                                        // Build an enriched log representation for frontend consumption
                                        Map<String, Object> enriched = new LinkedHashMap<>();
                                        enriched.put("logId", log.getLogId());
                                        enriched.put("transactionHash", log.getTransactionHash());
                                        enriched.put("blockNumber", log.getBlockNumber());
                                        enriched.put("blockHash", null);
                                        enriched.put("contractAddress", log.getContractAddress());
                                        enriched.put("eventType", log.getEventType() != null ? log.getEventType().name() : null);
                                        enriched.put("relatedParcelId", log.getRelatedParcelId());
                                        enriched.put("relatedRequestId", log.getRelatedRequestId());
                                        enriched.put("initiatedBy", log.getInitiatedBy());
                                        enriched.put("gasUsed", log.getGasUsed());
                                        enriched.put("payloadHash", log.getPayloadHash());
                                        // Provide aliases expected by the frontend
                                        enriched.put("currentHash", log.getPayloadHash());
                                        try {
                                                if (log.getRelatedParcelId() != null) {
                                                        var parcelOpt = landParcelRepository.findById(log.getRelatedParcelId());
                                                        if (parcelOpt.isPresent()) {
                                                                enriched.put("databaseHash", parcelOpt.get().getBlockchainHash());
                                                        }
                                                }
                                        } catch (Exception ex) {
                                                // ignore parcel lookup failures
                                        }
                                        enriched.put("recordedAt", log.getRecordedAt());

                                        // Ownership: current and history
                                        try {
                                                if (log.getRelatedParcelId() != null) {
                                                        var currentOpt = ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(log.getRelatedParcelId());
                                                        if (currentOpt.isPresent()) {
                                                                var curr = currentOpt.get();
                                                                enriched.put("currentOwnerName", curr.getOwner() != null ? curr.getOwner().getFullName() : null);
                                                                enriched.put("currentOwnerPhone", curr.getOwner() != null ? curr.getOwner().getPhoneNumber() : null);
                                                                enriched.put("currentOwnerNationalId", curr.getOwner() != null ? curr.getOwner().getNationalId() : null);
                                                        }

                                                        List<Map<String, Object>> history = new ArrayList<>();
                                                        var ownerships = ownershipRepository.findByParcel_ParcelIdOrderByCreatedAtAsc(log.getRelatedParcelId());
                                                        for (var o : ownerships) {
                                                                Map<String, Object> h = new LinkedHashMap<>();
                                                                h.put("ownerName", o.getOwner() != null ? o.getOwner().getFullName() : null);
                                                                h.put("acquisitionMethod", o.getAcquisitionMethod() != null ? o.getAcquisitionMethod().name() : null);
                                                                h.put("ownershipType", o.getOwnershipType() != null ? o.getOwnershipType().name() : null);
                                                                h.put("startDate", o.getStartDate());
                                                                h.put("endDate", o.getEndDate());
                                                                history.add(h);
                                                        }
                                                        enriched.put("ownershipHistory", history);
                                                }
                                        } catch (Exception ex) {
                                                // ignore ownership enrichment failures
                                        }

                                        // Approvals and digital signatures
                                        try {
                                                if (log.getRelatedRequestId() != null) {
                                                        List<Map<String, Object>> approvals = new ArrayList<>();
                                                        var apprs = approvalWorkflowRepository.findByRequest_RequestId(log.getRelatedRequestId());
                                                        for (var a : apprs) {
                                                                Map<String, Object> ap = new LinkedHashMap<>();
                                                                ap.put("approvalId", a.getApprovalId());
                                                                ap.put("stage", "Level " + a.getApprovalLevel());
                                                                ap.put("officerName", a.getOfficer() != null ? a.getOfficer().getFullName() : null);
                                                                ap.put("officerId", a.getOfficer() != null ? a.getOfficer().getUserId() : null);
                                                                ap.put("status", a.getAction() != null ? a.getAction().name() : null);
                                                                ap.put("comments", a.getComments());
                                                                ap.put("digitalSignature", a.getDigitalSignature());
                                                                ap.put("date", a.getActionedAt());
                                                                approvals.add(ap);
                                                        }
                                                        enriched.put("approvals", approvals);
                                                }
                                        } catch (Exception ex) {
                                                // ignore approvals enrichment failures
                                        }

                                        // If evidence contains a blockHash, surface it on the enriched log too
                                        try {
                                                if (evidence != null && evidence instanceof Map && ((Map<?,?>) evidence).get("blockHash") != null) {
                                                        enriched.put("blockHash", ((Map<?,?>) evidence).get("blockHash"));
                                                }
                                        } catch (Exception ex) {
                                                // ignore
                                        }

                                        Map<String, Object> payload = Map.of("log", enriched, "evidence", evidence);
                                        return ResponseEntity.ok(ApiResponse.ok("Blockchain audit log", payload));
                                })
                                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Transaction not found")));
        }

    @PostMapping("/transfer/approve")
    public ResponseEntity<ApiResponse<TransferResponse>> approve(
            @Valid @RequestBody ApproveRequest req, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Transfer approved (Level 1)",
                transferService.seniorOfficerApprove(req, officer.getUserId())));
    }

    @PostMapping("/transfer/reject")
    public ResponseEntity<ApiResponse<TransferResponse>> reject(
            @Valid @RequestBody RejectionRequest req, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Transfer rejected",
                transferService.rejectTransfer(req, officer.getUserId())));
    }

    @PostMapping("/transfer/flag")
    public ResponseEntity<ApiResponse<TransferResponse>> flag(
            @Valid @RequestBody FlagRequest req, Authentication auth) {
        User officer = getUser(auth);
        return ResponseEntity.ok(ApiResponse.ok("Transfer flagged for investigation",
                transferService.flagTransfer(req, officer.getUserId())));
    }

    private User getUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
