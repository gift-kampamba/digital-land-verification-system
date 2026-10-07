package com.landverification.controller;

import com.landverification.dto.*;
import com.landverification.model.ApprovalWorkflow;
import com.landverification.model.TransferRequest;
import com.landverification.repository.ApprovalWorkflowRepository;
import com.landverification.repository.TransferRequestRepository;
import com.landverification.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public Transfer Controller
 * Provides endpoints for viewing transfer details and approval status
 * These endpoints are accessible to authenticated officers and landowners
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferRequestRepository transferRequestRepository;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;
    private final TransferService transferService;

    /**
     * GET /api/transfers/{requestId}/status
     * Get the current status of a transfer request
     */
    @GetMapping("/{requestId}/status")
    public ResponseEntity<ApiResponse<TransferStatusResponse>> getTransferStatus(
            @PathVariable Integer requestId) {
        TransferStatusResponse response = transferService.getTransferStatus(requestId);
        return ResponseEntity.ok(ApiResponse.ok("Transfer status retrieved", response));
    }

    /**
     * GET /api/transfers/{requestId}/audit-trail
     * Get the approval history/audit trail for a transfer request
     */
    @GetMapping("/{requestId}/audit-trail")
    public ResponseEntity<ApiResponse<ApprovalAuditResponse>> getTransferAuditTrail(
            @PathVariable Integer requestId) {
        TransferRequest transfer = transferRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));

        List<ApprovalWorkflow> approvals = approvalWorkflowRepository.findByRequest_RequestId(requestId);

        List<ApprovalAuditResponse.ApprovalRecord> records = approvals.stream()
                .map(approval -> {
                    String officerName = approval.getOfficer().getFullName() != null 
                            ? approval.getOfficer().getFullName() 
                            : approval.getOfficer().getEmail();
                    return ApprovalAuditResponse.ApprovalRecord.builder()
                            .action(approval.getAction().toString())
                            .status(approval.getAction().toString())
                            .officer(officerName)
                            .officerName(officerName)
                            .approvalLevel(approval.getApprovalLevel())
                            .staffId(approval.getOfficer() != null ? approval.getOfficer().getUserId() : null)
                            .approvedAt(approval.getActionedAt().toString())
                            .actionedAt(approval.getActionedAt().toString())
                            .timestamp(approval.getActionedAt().toString())
                            .comments(approval.getComments() != null ? approval.getComments() : "")
                            .digitalSignature(approval.getDigitalSignature())
                            .build();
                })
                .collect(Collectors.toList());

        ApprovalAuditResponse auditResponse = ApprovalAuditResponse.builder()
                .requestId(requestId)
                .approvals(records)
                .totalApprovals(records.size())
                .currentStatus(transfer.getStatus().toString())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Approval audit trail retrieved", auditResponse));
    }
}
