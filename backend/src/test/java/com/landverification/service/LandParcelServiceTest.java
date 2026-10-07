package com.landverification.service;

import com.landverification.dto.ParcelRegistrationRequest;
import com.landverification.dto.ParcelResponse;
import com.landverification.model.LandOwnership;
import com.landverification.model.LandParcel;
import com.landverification.model.TransferRequest;
import com.landverification.model.User;
import com.landverification.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LandParcelServiceTest {

    @Mock
    private LandParcelRepository parcelRepository;
    @Mock
    private LandOwnershipRepository ownershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private BlockchainService blockchainService;
    @Mock
    private TitleDeedService titleDeedService;
    @Mock
    private ParcelSequenceRepository parcelSequenceRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TransferRequestRepository transferRequestRepository;
    @Mock
    private ApprovalWorkflowRepository approvalWorkflowRepository;
        @Mock
        private FileService fileService;

    @InjectMocks
    private LandParcelService landParcelService;

    @Test
    void registerParcelSendsCompletionEmailForExistingLandownerWithValidEmail() throws Exception {
        ParcelRegistrationRequest request = new ParcelRegistrationRequest();
        request.setParcelNumber("ZM-LUS-2026-0001");
        request.setPlotSize(1200);
        request.setLandUseType("RESIDENTIAL");
        request.setTenureType("FREEHOLD");
        request.setOwnershipType("INDIVIDUAL");
        request.setDistrict("Lusaka");
        request.setProvince("Lusaka");
        request.setLocationAddress("Plot 12, Kabulonga");
        request.setOwnerName("Jane Doe");
        request.setOwnerNid("12345678");
        request.setOwnerPhone("+260971234567");
        request.setOwnerEmail("jane@example.com");
        request.setOwnerAddress("Lusaka");
        request.setOwnerGender("FEMALE");
        request.setOwnerSignature("SIG-1:registration-signature");

        User existingOwner = User.builder()
                .userId(42)
                .fullName("Jane Doe")
                .nationalId("12345678")
                .phoneNumber("+260971234567")
                .email("jane@example.com")
                .username("jane")
                .role(User.Role.LAND_OWNER)
                .build();

        when(parcelRepository.existsByParcelNumber("ZM-LUS-2026-0001")).thenReturn(false);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(existingOwner));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(parcelRepository.save(any(LandParcel.class))).thenAnswer(invocation -> {
            LandParcel parcel = invocation.getArgument(0);
            parcel.setParcelId(1001);
            return parcel;
        });
        when(ownershipRepository.save(any(LandOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(1001)).thenReturn(Optional.of(LandOwnership.builder()
                .owner(existingOwner)
                .isCurrent(true)
                .build()));
        when(titleDeedService.generateCertificate(any(LandParcel.class), any(User.class), any(ParcelRegistrationRequest.class)))
                .thenReturn("title-deed.pdf");
        when(blockchainService.getDefaultOwnerAddress()).thenReturn("0x1111111111111111111111111111111111111111");
        when(blockchainService.registerParcel(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("0xabc123");
        doNothing().when(blockchainService).recordOwnershipChange(anyString(), anyInt(), isNull(), anyInt());

        ParcelResponse response = landParcelService.registerParcel(request, 99);

        verify(emailService).sendLandownerCredentials(
                eq("jane@example.com"),
                eq("jane"),
                isNull(),
                eq("Jane Doe"),
                eq("ZM-LUS-2026-0001"),
                eq("Plot 12, Kabulonga"),
                eq(BigDecimal.valueOf(1200)),
                eq("SOLE"),
                contains("/uploads/title-deed.pdf")
        );
        verify(notificationRepository, atLeastOnce()).save(any());
        assert response != null;
    }

    @Test
    void registerParcelRejectsDuplicateOwnerAndLocationDetails() {
        ParcelRegistrationRequest request = new ParcelRegistrationRequest();
        request.setParcelNumber("ZM-LUS-2026-0002");
        request.setPlotSize(800);
        request.setLandUseType("RESIDENTIAL");
        request.setTenureType("FREEHOLD");
        request.setOwnershipType("INDIVIDUAL");
        request.setDistrict("Lusaka");
        request.setProvince("Lusaka");
        request.setLocationAddress("Plot 12, Kabulonga");
        request.setOwnerName("Jane Doe");
        request.setOwnerNid("12345678");
        request.setOwnerPhone("+260971234567");
        request.setOwnerEmail("jane@example.com");
        request.setOwnerAddress("Lusaka");
        request.setOwnerGender("FEMALE");
        request.setOwnerSignature("SIG-2:registration-signature");

        LandParcel existingParcel = LandParcel.builder()
                .parcelNumber("ZM-LUS-2026-0001")
                .plotNumber("PLOT-01")
                .province("Lusaka")
                .district("Lusaka")
                .locationAddress("Plot 12, Kabulonga")
                .areaSqm(BigDecimal.valueOf(800))
                .landUse(LandParcel.LandUse.RESIDENTIAL)
                .build();
        existingParcel.setParcelId(1001);

        when(parcelRepository.existsByParcelNumber("ZM-LUS-2026-0002")).thenReturn(false);
        when(parcelRepository.findAll()).thenReturn(List.of(existingParcel));
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(1001)).thenReturn(Optional.of(LandOwnership.builder()
                .owner(User.builder().fullName("Jane Doe").nationalId("12345678").email("jane@example.com").build())
                .isCurrent(true)
                .build()));

        assertThrows(IllegalArgumentException.class, () -> landParcelService.registerParcel(request, 99));
        verify(userRepository, never()).save(any(User.class));
        verify(parcelRepository, never()).save(any(LandParcel.class));
    }

    @Test
    void registerParcelStoresOwnerPhotoWhenProvided() throws Exception {
        ParcelRegistrationRequest request = new ParcelRegistrationRequest();
        request.setParcelNumber("ZM-LUS-2026-0003");
        request.setPlotSize(1400);
        request.setLandUseType("RESIDENTIAL");
        request.setTenureType("FREEHOLD");
        request.setOwnershipType("INDIVIDUAL");
        request.setDistrict("Lusaka");
        request.setProvince("Lusaka");
        request.setLocationAddress("Plot 88, Kanyama");
        request.setOwnerName("James Phiri");
        request.setOwnerNid("98765432");
        request.setOwnerPhone("+260972345678");
        request.setOwnerEmail("james@example.com");
        request.setOwnerAddress("Lusaka");
        request.setOwnerGender("MALE");
        request.setOwnerSignature("SIG-3:registration-signature");

        User existingOwner = User.builder()
                .userId(55)
                .fullName("James Phiri")
                .nationalId("98765432")
                .phoneNumber("+260972345678")
                .email("james@example.com")
                .username("james")
                .role(User.Role.LAND_OWNER)
                .build();

        MockMultipartFile ownerPhoto = new MockMultipartFile(
                "ownerPhoto", "owner-photo.jpg", "image/jpeg", "photo-bytes".getBytes()
        );

        when(parcelRepository.existsByParcelNumber("ZM-LUS-2026-0003")).thenReturn(false);
        when(userRepository.findByEmail("james@example.com")).thenReturn(Optional.of(existingOwner));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(parcelRepository.save(any(LandParcel.class))).thenAnswer(invocation -> {
            LandParcel parcel = invocation.getArgument(0);
            parcel.setParcelId(2001);
            return parcel;
        });
        when(ownershipRepository.save(any(LandOwnership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(2001)).thenReturn(Optional.of(LandOwnership.builder()
                .owner(existingOwner)
                .isCurrent(true)
                .build()));
        when(titleDeedService.generateCertificate(any(LandParcel.class), any(User.class), any(ParcelRegistrationRequest.class)))
                .thenReturn("title-deed-2.pdf");
        when(blockchainService.getDefaultOwnerAddress()).thenReturn("0x2222222222222222222222222222222222222222");
        when(blockchainService.registerParcel(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn("0xdef456");
        doNothing().when(blockchainService).recordOwnershipChange(anyString(), anyInt(), isNull(), anyInt());
        when(userRepository.findById(55)).thenReturn(Optional.of(existingOwner));
        when(fileService.saveFile(ownerPhoto)).thenReturn("owner-photo.jpg");

        ParcelResponse response = landParcelService.registerParcel(request, 99, ownerPhoto);

        verify(userRepository, atLeastOnce()).save(argThat(user -> "owner-photo.jpg".equals(user.getPhotoPath()) || user.getPhotoPath() != null));
        verify(parcelRepository, atLeastOnce()).save(argThat(parcel -> "owner-photo.jpg".equals(parcel.getOwnerPhotoPath())));
        assert response != null;
    }

    @Test
    void getParcelsByOwnerReturnsRejectedTransferParcelWhenOwnershipIsMissing() {
        User owner = User.builder()
                .userId(42)
                .email("owner@example.com")
                .nationalId("12345678")
                .build();

        LandParcel parcel = LandParcel.builder()
                .parcelId(1001)
                .parcelNumber("ZM-LPL-2026-00003")
                .status(LandParcel.ParcelStatus.ACTIVE)
                .build();

        TransferRequest rejectedTransfer = TransferRequest.builder()
                .requestId(7)
                .parcel(parcel)
                .seller(owner)
                .status(TransferRequest.TransferStatus.REJECTED)
                .build();

        when(ownershipRepository.findCurrentByOwnerUserId(42)).thenReturn(List.of());
        when(ownershipRepository.findByOwner_EmailAndIsCurrentTrue("owner@example.com")).thenReturn(List.of());
        when(ownershipRepository.findByOwner_NationalIdAndIsCurrentTrue("12345678")).thenReturn(List.of());
        when(transferRequestRepository.findBySeller_UserId(42)).thenReturn(List.of(rejectedTransfer));
        when(transferRequestRepository.findByBuyer_UserId(42)).thenReturn(List.of());
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(1001)).thenReturn(Optional.empty());
        when(parcelRepository.findById(1001)).thenReturn(Optional.of(parcel));

        List<ParcelResponse> result = landParcelService.getParcelsByOwner(owner);

        assert result != null;
        assert result.size() == 1;
        assert result.get(0).getParcelNumber().equals("ZM-LPL-2026-00003");
    }
}
