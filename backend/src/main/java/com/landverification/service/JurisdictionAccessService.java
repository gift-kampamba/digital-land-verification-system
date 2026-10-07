package com.landverification.service;

import com.landverification.model.LandParcel;
import com.landverification.model.User;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class JurisdictionAccessService {

    private static final String DEMO_PROVINCE = "lusaka";

    public boolean canAccess(User officer, LandParcel parcel) {
        if (officer == null || parcel == null) {
            return false;
        }

        if (officer.getRole() == User.Role.SYSTEM_ADMIN) {
            return true;
        }

        String parcelProvince = normalize(parcel.getProvince());

        if ((officer.getRole() == User.Role.LAND_OFFICER
                || officer.getRole() == User.Role.SENIOR_OFFICER)) {
            return parcelProvince == null || DEMO_PROVINCE.equals(parcelProvince);
        }

        return false;
    }

    public boolean isDemoParcel(LandParcel parcel) {
        if (parcel == null) {
            return false;
        }
        String province = normalize(parcel.getProvince());
        return province == null || DEMO_PROVINCE.equals(province);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceFirst("\\s+(province|district)$", "")
                .trim();
    }
}
