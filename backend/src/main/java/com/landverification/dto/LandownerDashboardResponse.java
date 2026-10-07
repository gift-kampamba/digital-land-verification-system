package com.landverification.dto;

import com.landverification.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandownerDashboardResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private String gender;
    private String district;
    private String username;
    private String photoPath;
    private Boolean profileComplete;
    private List<ParcelResponse> parcels;

    public static LandownerDashboardResponse fromUser(User user, List<ParcelResponse> parcels) {
        return LandownerDashboardResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .district(user.getDistrict())
                .username(user.getUsername())
                .photoPath(user.getPhotoPath())
                .profileComplete(user.getProfileComplete())
                .parcels(parcels)
                .build();
    }
}
