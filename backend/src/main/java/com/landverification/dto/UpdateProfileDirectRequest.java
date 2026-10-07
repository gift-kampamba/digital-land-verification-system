package com.landverification.dto;

import lombok.Data;

@Data
public class UpdateProfileDirectRequest {
    private String fullName;
    private String phoneNumber;
    private String nationalId;
    private String district;
    private String province;
    private String address;
    private String gender;
}
