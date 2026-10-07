package com.landverification.service;

import com.landverification.config.AppProperties;
import com.landverification.dto.ApproveRequest;
import com.landverification.dto.InitiateTransferRequest;
import com.landverification.dto.TransferResponse;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRequestRepository transferRepository;

    @Mock
    private ApprovalWorkflowRepository approvalRepository;

    @Mock
    private LandParcelRepository parcelRepository;

    @Mock
    private LandOwnershipRepository ownershipRepository;

        @Mock
        private com.landverification.repository.DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BlockchainService blockchainService;

    @Mock
    private EmailService emailService;

    @Mock
    private TitleDeedService titleDeedService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private NotificationRepository notificationRepository;

        @Mock
        private FileService fileService;

    @InjectMocks
    private TransferService transferService;

    @Test
    void seniorOfficerApprove_rejectsTransferNotYetApprovedByLandOfficer() {
        TransferRequest transfer = createTransfer(TransferRequest.TransferStatus.SUBMITTED);
        when(transferRepository.findById(1)).thenReturn(Optional.of(transfer));

        ApproveRequest request = new ApproveRequest();
        request.setRequestId(1L);
        request.setComments("Ready for review");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> transferService.seniorOfficerApprove(request, 99));

        assertTrue(ex.getMessage().toLowerCase().contains("land officer"));
    }

    @Test
    void seniorOfficerApprove_generatesTitleDeedUsingBuyerAccountDetails() throws Exception {
        TransferRequest transfer = createTransfer(TransferRequest.TransferStatus.APPROVED_L1);
        transfer.setBuyerFullName("Buyer Name");
        transfer.setBuyerNationalId("B1234567");
        transfer.setBuyerPhoneNumber("+260123456789");
        transfer.setBuyerAddress("123 Buyer Lane");

        User officer = User.builder().userId(99).fullName("Senior Officer").email("officer@example.com").build();
        User buyer = User.builder()
                .userId(2)
                .fullName("Buyer Name")
                .nationalId("B1234567")
                .phoneNumber("+260123456789")
                .address("123 Buyer Lane")
                .email("buyer@example.com")
                .isActive(true)
                .build();
        transfer.setBuyer(buyer);

        LandOwnership currentOwnership = LandOwnership.builder()
                .parcel(transfer.getParcel())
                .owner(User.builder().userId(1).build())
                .isCurrent(true)
                .build();

        when(transferRepository.findById(1)).thenReturn(Optional.of(transfer));
        when(userRepository.findById(99)).thenReturn(Optional.of(officer));
        when(approvalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(10)).thenReturn(Optional.of(currentOwnership));
        when(ownershipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(parcelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(titleDeedService.generateCertificate(any(), any(), any())).thenReturn("title-deeds/sample.pdf");

        ApproveRequest approveReq = new ApproveRequest();
        approveReq.setRequestId(1L);
        transferService.seniorOfficerApprove(approveReq, 99);

        verify(titleDeedService).generateCertificate(any(), any(), any());
    }

    @Test
        void initiateTransfer_sendsNotificationsToSellerAndBuyer() throws Exception {
        InitiateTransferRequest req = new InitiateTransferRequest();
        req.setParcelId(10);
        req.setBuyerName("Buyer Name");
        req.setBuyerEmail("buyer@example.com");
        req.setBuyerNid("B1234567");
        req.setBuyerPhone("+260123456789");
        req.setBuyerAddress("123 Buyer Lane");
        req.setBuyerNationality("Zambian");
        req.setBuyerIdType("NID");
        req.setTransferDate(java.time.LocalDate.now().toString());
        req.setPaymentTerms("Cash");
        req.setTransferReason("Sale");
        req.setAgreedPriceZmw(java.math.BigDecimal.valueOf(10000));
        req.setOwnerSignature("sig");
        req.setBuyerSignature("sig");

        User seller = User.builder()
                .userId(1)
                .fullName("Seller Name")
                .email("seller@example.com")
                .build();

        User buyer = User.builder()
                .userId(2)
                .fullName("Buyer Name")
                .email("buyer@example.com")
                .build();

        LandParcel parcel = LandParcel.builder()
                .parcelId(10)
                .parcelNumber("P123")
                .status(LandParcel.ParcelStatus.ACTIVE)
                .build();

        LandOwnership currentOwnership = LandOwnership.builder()
                .parcel(parcel)
                .owner(seller)
                .isCurrent(true)
                .build();

        when(parcelRepository.findById(10)).thenReturn(Optional.of(parcel));
        when(userRepository.findById(1)).thenReturn(Optional.of(seller));
        when(userRepository.findByNationalId("B1234567")).thenReturn(Optional.of(buyer));
        when(ownershipRepository.findByParcel_ParcelIdAndIsCurrentTrue(10)).thenReturn(Optional.of(currentOwnership));
        when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(parcelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.sendEmail(any(), any(), any())).thenReturn("Email sent successfully to: buyer@example.com");
        when(blockchainService.initiateTransfer(any(), any(), any(), any(), any(), any(), any())).thenReturn("0xdeadbeef");

        MultipartFile sellerPhoto = new MockMultipartFile("sellerPhoto", "seller.jpg", "image/jpeg", "seller".getBytes());
        MultipartFile buyerPhoto = new MockMultipartFile("buyerPhoto", "buyer.jpg", "image/jpeg", "buyer".getBytes());
        when(fileService.saveFile(sellerPhoto)).thenReturn("seller.jpg");
        when(fileService.saveFile(buyerPhoto)).thenReturn("buyer.jpg");
        TransferResponse result = transferService.initiateTransfer(req, 1, Collections.<MultipartFile>emptyList(), sellerPhoto, buyerPhoto);

        verify(notificationRepository, times(2)).save(any());
        assertTrue(result.getEmailStatusMessage() != null && result.getEmailStatusMessage().contains("Email sent successfully"));
    }

    private TransferRequest createTransfer(TransferRequest.TransferStatus status) {
        User seller = User.builder()
                .userId(1)
                .fullName("Seller Name")
                .email("seller@example.com")
                .build();
        User buyer = User.builder()
                .userId(2)
                .fullName("Buyer Name")
                .email("buyer@example.com")
                .build();
        LandParcel parcel = LandParcel.builder()
                .parcelId(10)
                .parcelNumber("P123")
                .status(LandParcel.ParcelStatus.PENDING_TRANSFER)
                .build();

        TransferRequest transfer = TransferRequest.builder()
                .requestId(1)
                .parcel(parcel)
                .seller(seller)
                .buyer(buyer)
                .status(status)
                .build();

        return transfer;
    }
}
