package com.landverification.dto;

import com.landverification.model.PendingProfileChange;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PendingProfileChangeResponse {
    private Integer changeId;
    private Integer userId;
    private String userName;
    private String userEmail;
    private String fullName;
    private String phoneNumber;
    private String address;
    private String photoPath;
    private PendingProfileChange.ChangeType changeType;
    private PendingProfileChange.Status status;
    private String adminComments;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;

    public static PendingProfileChangeResponse fromEntity(PendingProfileChange change) {
        PendingProfileChangeResponse response = new PendingProfileChangeResponse();
        response.setChangeId(change.getChangeId());
        response.setUserId(change.getUser().getUserId());
        response.setUserName(change.getUser().getFullName());
        response.setUserEmail(change.getUser().getEmail());
        response.setFullName(change.getFullName());
        response.setPhoneNumber(change.getPhoneNumber());
        response.setAddress(change.getAddress());
        response.setPhotoPath(change.getPhotoPath());
        response.setChangeType(change.getChangeType());
        response.setStatus(change.getStatus());
        response.setAdminComments(change.getAdminComments());
        response.setRequestedAt(change.getRequestedAt());
        response.setApprovedAt(change.getApprovedAt());
        if (change.getApprovedBy() != null) {
            response.setApprovedByName(change.getApprovedBy().getFullName());
        }
        return response;
    }
}