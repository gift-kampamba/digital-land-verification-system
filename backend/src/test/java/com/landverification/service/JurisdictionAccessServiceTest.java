package com.landverification.service;

import com.landverification.model.LandParcel;
import com.landverification.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JurisdictionAccessServiceTest {

    private final JurisdictionAccessService jurisdictionAccessService = new JurisdictionAccessService();

    @Test
    void landOfficerCanAccessOnlyAssignedDistrictRecords() {
        User officer = User.builder()
                .role(User.Role.LAND_OFFICER)
                .district("Lusaka")
                .province("Lusaka")
                .build();

        LandParcel sameJurisdictionParcel = LandParcel.builder()
                .province("Lusaka")
                .district("Lusaka")
                .build();
        LandParcel differentJurisdictionParcel = LandParcel.builder()
                .province("Copperbelt")
                .district("Ndola")
                .build();

        assertTrue(jurisdictionAccessService.canAccess(officer, sameJurisdictionParcel));
        assertFalse(jurisdictionAccessService.canAccess(officer, differentJurisdictionParcel));
    }

    @Test
    void seniorOfficerCanAccessRecordsInAssignedProvince() {
        User officer = User.builder()
                .role(User.Role.SENIOR_OFFICER)
                .province("Lusaka")
                .build();

        LandParcel sameProvinceParcel = LandParcel.builder()
                .province("Lusaka")
                .district("Lusaka")
                .build();
        LandParcel otherProvinceParcel = LandParcel.builder()
                .province("Copperbelt")
                .district("Ndola")
                .build();

        assertTrue(jurisdictionAccessService.canAccess(officer, sameProvinceParcel));
        assertFalse(jurisdictionAccessService.canAccess(officer, otherProvinceParcel));
    }

        @Test
        void landOfficerMatchesLocationNamesWithProvinceAndDistrictSuffixes() {
                User officer = User.builder()
                                .role(User.Role.LAND_OFFICER)
                                .district("Lusaka District")
                                .province("Lusaka Province")
                                .build();

                LandParcel parcel = LandParcel.builder()
                                .province("Lusaka")
                                .district("Lusaka")
                                .build();

                assertTrue(jurisdictionAccessService.canAccess(officer, parcel));
        }

        @Test
        void landOfficerCanAccessAnyLusakaDistrictInDemoMode() {
                User officer = User.builder()
                                .role(User.Role.LAND_OFFICER)
                                .province("Copperbelt")
                                .district("Ndola")
                                .build();

                LandParcel lusakaParcel = LandParcel.builder()
                                .province("Lusaka Province")
                                .district("Kafue")
                                .build();

                assertTrue(jurisdictionAccessService.canAccess(officer, lusakaParcel));
        }

    @Test
    void systemAdministratorBypassesJurisdictionRestrictions() {
        User administrator = User.builder()
                .role(User.Role.SYSTEM_ADMIN)
                .build();

        LandParcel parcel = LandParcel.builder()
                .province("Copperbelt")
                .district("Ndola")
                .build();

        assertTrue(jurisdictionAccessService.canAccess(administrator, parcel));
    }
}
