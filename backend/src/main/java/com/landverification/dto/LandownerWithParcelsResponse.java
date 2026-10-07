package com.landverification.dto;

import com.landverification.model.User;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandownerWithParcelsResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private String gender;
    private String district;
    private String nationalId;
    private String username;
    private List<ParcelResponse> parcels;

    public static LandownerWithParcelsResponse fromUser(User user, List<ParcelResponse> parcels) {
        return LandownerWithParcelsResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .district(user.getDistrict())
                .nationalId(user.getNationalId())
                .username(user.getUsername())
                .parcels(parcels)
                .build();
    }
}
