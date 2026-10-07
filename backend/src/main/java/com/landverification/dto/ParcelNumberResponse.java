package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for auto-generated parcel numbers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParcelNumberResponse {
    private String parcelNumber;
}
