// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/MessageHashUtils.sol";

/**
 * LandVerification.sol
 * Blockchain-Based Digital Land Verification System
 * Student: Gift Kampamba | CBU 22103754
 *
 * Roles (match DB users.role):
 *   LAND_OFFICER    — registers parcels, Level-1 review
 *   SENIOR_OFFICER  — Level-1 approval / rejection (requires digital signature)
 *   FINAL_AUTHORITY — Level-2 final approval (requires digital signature)
 *   SYSTEM_ADMIN    — manages officer addresses on-chain
 *
 * Multi-Signature Approval Workflow:
 *   registerParcel() → initiateTransfer() → seniorOfficerApprove(signature) → finalAuthorityApprove(signature)
 *
 * Each approval stage requires:
 *   1. Officer/Authority address authorization
 *   2. Digital signature verification
 *   3. Timestamp recording for audit trail
 */
contract LandVerification {
    using ECDSA for bytes32;

    // ─── Enums ────────────────────────────────────────────────────────────
    enum TransferStatus { SUBMITTED, UNDER_REVIEW, APPROVED_L1, APPROVED_L2, APPROVED, REJECTED, CANCELLED }

    // ─── Structs ──────────────────────────────────────────────────────────
    struct Parcel {
        string  parcelNumber;
        string  province;
        string  district;
        string  locationAddress;
        uint256 areaSqm;
        string  landUse;
        address currentOwnerWallet;
        string  documentHash;
        uint256 registeredAt;
        bool    exists;
    }

    struct Transfer {
        uint256        requestId;
        string         parcelNumber;
        address        sellerWallet;
        address        buyerWallet;
        string         transferReason;
        TransferStatus status;
        string         ownerSignature;
        
        // Level-1 (Senior Officer) Approval
        address        seniorOfficer;
        bytes          seniorOfficerSignature;
        uint256        approvedL1At;
        
        // Level-2 (Final Authority) Approval
        address        approverAuthority;
        bytes          authoritySignature;
        uint256        approvedFinalAt;
        
        uint256        submittedAt;
        string         rejectionReason;
    }

    // ─── State ────────────────────────────────────────────────────────────
    address public admin;
    address public finalAuthority;

    mapping(address => bool) public landOfficers;
    mapping(address => bool) public seniorOfficers;

    mapping(string  => Parcel)   public parcels;
    mapping(uint256 => Transfer) public transfers;
    mapping(string  => string[]) public ownershipHistory; // parcelNumber → owner wallet strings

    uint256 private _transferCounter;

    // ─── Events (immutable audit trail) ───────────────────────────────────
    event ParcelRegistered(string indexed parcelNumber, address indexed owner, string documentHash, uint256 timestamp);
    event TransferInitiated(uint256 indexed requestId, string indexed parcelNumber, address from, address to, uint256 timestamp);
    event SeniorOfficerApproved(uint256 indexed requestId, address indexed officer, bytes signature, uint256 timestamp);
    event FinalApproved(uint256 indexed requestId, string indexed parcelNumber, address newOwner, address approver, bytes signature, uint256 timestamp);
    event TransferRejected(uint256 indexed requestId, string reason, address rejectedBy, uint256 timestamp);
    event OwnershipRecorded(string indexed parcelNumber, address newOwner, uint256 timestamp);
    event RecordVerified(string indexed parcelNumber, address verifiedBy, uint256 timestamp);

    // ─── Modifiers ────────────────────────────────────────────────────────
    modifier onlyAdmin() { require(msg.sender == admin, "Only admin"); _; }
    modifier onlyOfficer() { require(landOfficers[msg.sender] || seniorOfficers[msg.sender], "Not an officer"); _; }
    modifier onlySeniorOfficer() { require(seniorOfficers[msg.sender], "Only senior officer"); _; }
    modifier onlyFinalAuthority() { require(msg.sender == finalAuthority, "Only final authority"); _; }
    modifier parcelExists(string memory parcelNumber) { require(parcels[parcelNumber].exists, "Parcel not found"); _; }

    // ─── Constructor ──────────────────────────────────────────────────────
    constructor(address _finalAuthority) {
        admin          = msg.sender;
        finalAuthority = _finalAuthority;
        landOfficers[msg.sender]  = true;
        seniorOfficers[msg.sender] = true;
    }

    // ─── Admin: manage officers ────────────────────────────────────────────
    function addLandOfficer(address officer) external onlyAdmin {
        landOfficers[officer] = true;
    }

    function addSeniorOfficer(address officer) external onlyAdmin {
        seniorOfficers[officer] = true;
    }

    function removeOfficer(address officer) external onlyAdmin {
        landOfficers[officer]  = false;
        seniorOfficers[officer] = false;
    }

    function setFinalAuthority(address newAuth) external onlyAdmin {
        finalAuthority = newAuth;
    }

    // ─── Step 1: Land Officer registers a parcel ──────────────────────────
    function registerParcel(
        string memory parcelNumber,
        string memory province,
        string memory district,
        string memory locationAddress,
        uint256       areaSqm,
        string memory landUse,
        address       ownerWallet,
        string memory documentHash
    ) external onlyOfficer {
        require(!parcels[parcelNumber].exists, "Parcel already registered");
        require(ownerWallet != address(0), "Invalid owner address");

        parcels[parcelNumber] = Parcel({
            parcelNumber:       parcelNumber,
            province:           province,
            district:           district,
            locationAddress:    locationAddress,
            areaSqm:            areaSqm,
            landUse:            landUse,
            currentOwnerWallet: ownerWallet,
            documentHash:       documentHash,
            registeredAt:       block.timestamp,
            exists:             true
        });

        ownershipHistory[parcelNumber].push(_toAsciiString(ownerWallet));
        emit ParcelRegistered(parcelNumber, ownerWallet, documentHash, block.timestamp);
    }

    // ─── Step 2: Land owner initiates transfer ────────────────────────────
    function initiateTransfer(
        string memory parcelNumber,
        address       buyerWallet,
        string memory transferReason,
        string memory ownerSignature
    ) external parcelExists(parcelNumber) returns (uint256) {
        require(parcels[parcelNumber].currentOwnerWallet == msg.sender, "Not the parcel owner");
        require(buyerWallet != address(0) && buyerWallet != msg.sender, "Invalid buyer");
        require(bytes(ownerSignature).length > 0, "Digital signature required");

        _transferCounter++;
        uint256 reqId = _transferCounter;

        transfers[reqId] = Transfer({
            requestId:                reqId,
            parcelNumber:             parcelNumber,
            sellerWallet:             msg.sender,
            buyerWallet:              buyerWallet,
            transferReason:           transferReason,
            status:                   TransferStatus.SUBMITTED,
            ownerSignature:           ownerSignature,
            seniorOfficer:            address(0),
            seniorOfficerSignature:   new bytes(0),
            approvedL1At:             0,
            approverAuthority:        address(0),
            authoritySignature:       new bytes(0),
            approvedFinalAt:          0,
            submittedAt:              block.timestamp,
            rejectionReason:          ""
        });

        emit TransferInitiated(reqId, parcelNumber, msg.sender, buyerWallet, block.timestamp);
        return reqId;
    }

    // ─── Step 3: Senior Officer — Level-1 approval with digital signature ──
    /**
     * @param requestId The transfer request ID
     * @param signature Digital signature from senior officer
     *        Signature is created by signing the message hash:
     *        keccak256(abi.encodePacked(requestId, parcelNumber, approval_timestamp))
     */
    function seniorOfficerApprove(uint256 requestId, address signer, bytes memory signature) external {
        Transfer storage t = transfers[requestId];
        require(
            t.status == TransferStatus.SUBMITTED || t.status == TransferStatus.UNDER_REVIEW,
            "Transfer not awaiting L1 approval"
        );
        require(signature.length > 0, "Signature required");

        // Verify the signature
        bytes32 messageHash = keccak256(abi.encodePacked(
            "SENIOR_APPROVAL",
            requestId,
            t.parcelNumber,
            signer
        ));
        bytes32 ethSignedMessageHash = MessageHashUtils.toEthSignedMessageHash(messageHash);
        address recoveredSigner = ECDSA.recover(ethSignedMessageHash, signature);
        
        require(recoveredSigner == signer, "Invalid signature");
        require(seniorOfficers[signer], "Signer is not authorized senior officer");
        require(seniorOfficers[msg.sender] || msg.sender == admin, "Not an authorized relayer");

        // Record approval
        t.status                    = TransferStatus.APPROVED_L1;
        t.seniorOfficer             = msg.sender;
        t.seniorOfficerSignature    = signature;
        t.approvedL1At              = block.timestamp;

        emit SeniorOfficerApproved(requestId, msg.sender, signature, block.timestamp);
    }

    // ─── Step 4: Final Authority — Level-2 final approval with digital signature ─
    /**
     * @param requestId The transfer request ID
     * @param signature Digital signature from final authority
     *        Signature is created by signing the message hash:
     *        keccak256(abi.encodePacked(requestId, parcelNumber, approval_timestamp))
     */
    function finalAuthorityApprove(uint256 requestId, address signer, bytes memory signature) external {
        Transfer storage t = transfers[requestId];
        require(t.status == TransferStatus.APPROVED_L1, "Senior officer approval required first");
        require(signature.length > 0, "Signature required");

        // Verify the signature
        bytes32 messageHash = keccak256(abi.encodePacked(
            "FINAL_APPROVAL",
            requestId,
            t.parcelNumber,
            signer
        ));
        bytes32 ethSignedMessageHash = MessageHashUtils.toEthSignedMessageHash(messageHash);
        address recoveredSigner = ECDSA.recover(ethSignedMessageHash, signature);
        
        require(recoveredSigner == signer, "Invalid signature");
        require(signer == finalAuthority, "Signer is not final authority");
        require(msg.sender == finalAuthority || msg.sender == admin, "Not an authorized relayer");

        // Record new ownership on blockchain
        address newOwner = t.buyerWallet;
        parcels[t.parcelNumber].currentOwnerWallet = newOwner;
        ownershipHistory[t.parcelNumber].push(_toAsciiString(newOwner));

        // Record approval
        t.status              = TransferStatus.APPROVED;
        t.approverAuthority   = msg.sender;
        t.authoritySignature  = signature;
        t.approvedFinalAt     = block.timestamp;

        emit FinalApproved(requestId, t.parcelNumber, newOwner, msg.sender, signature, block.timestamp);
        emit OwnershipRecorded(t.parcelNumber, newOwner, block.timestamp);
    }

    // ─── Reject transfer (Senior Officer OR Final Authority) ──────────────
    function rejectTransfer(uint256 requestId, string memory reason) external {
        Transfer storage t = transfers[requestId];
        require(bytes(reason).length > 0, "Rejection reason required");
        bool isSenior = seniorOfficers[msg.sender] &&
            (t.status == TransferStatus.SUBMITTED || t.status == TransferStatus.UNDER_REVIEW);
        bool isFinal  = msg.sender == finalAuthority && t.status == TransferStatus.APPROVED_L1;
        require(isSenior || isFinal, "Not authorised to reject at this stage");

        t.status          = TransferStatus.REJECTED;
        t.rejectionReason = reason;
        emit TransferRejected(requestId, reason, msg.sender, block.timestamp);
    }

    // ─── Public verify (emit event for audit log) ─────────────────────────
    function verifyRecord(string memory parcelNumber) external parcelExists(parcelNumber) {
        emit RecordVerified(parcelNumber, msg.sender, block.timestamp);
    }

    // ─── Read functions ───────────────────────────────────────────────────
    function getParcel(string memory parcelNumber) external view
        returns (string memory, string memory, string memory, uint256, address, string memory, uint256)
    {
        Parcel memory p = parcels[parcelNumber];
        require(p.exists, "Parcel not found");
        return (p.province, p.district, p.locationAddress, p.areaSqm,
                p.currentOwnerWallet, p.documentHash, p.registeredAt);
    }

    function getTransfer(uint256 requestId) external view
        returns (
            string memory parcelNumber,
            address sellerWallet,
            address buyerWallet,
            uint8 status,
            uint256 submittedAt,
            string memory rejectionReason,
            address seniorOfficer,
            uint256 approvedL1At,
            address approverAuthority,
            uint256 approvedFinalAt
        )
    {
        Transfer memory t = transfers[requestId];
        return (
            t.parcelNumber,
            t.sellerWallet,
            t.buyerWallet,
            uint8(t.status),
            t.submittedAt,
            t.rejectionReason,
            t.seniorOfficer,
            t.approvedL1At,
            t.approverAuthority,
            t.approvedFinalAt
        );
    }

    /**
     * Get transfer with signature details
     */
    function getTransferWithSignatures(uint256 requestId) external view
        returns (
            string memory parcelNumber,
            address sellerWallet,
            address buyerWallet,
            uint8 status,
            uint256 submittedAt,
            address seniorOfficer,
            bool hasL1Signature,
            uint256 approvedL1At,
            address approverAuthority,
            bool hasFinalSignature,
            uint256 approvedFinalAt
        )
    {
        Transfer memory t = transfers[requestId];
        return (
            t.parcelNumber,
            t.sellerWallet,
            t.buyerWallet,
            uint8(t.status),
            t.submittedAt,
            t.seniorOfficer,
            t.seniorOfficerSignature.length > 0,
            t.approvedL1At,
            t.approverAuthority,
            t.authoritySignature.length > 0,
            t.approvedFinalAt
        );
    }

    function getOwnershipHistory(string memory parcelNumber) external view returns (string[] memory) {
        return ownershipHistory[parcelNumber];
    }

    /**
     * Verify that a signature is valid for an approval
     * Used for frontend validation before sending transaction
     */
    function verifyApprovalSignature(
        uint256 requestId,
        string memory approvalType, // "SENIOR_APPROVAL" or "FINAL_APPROVAL"
        address signer,
        bytes memory signature
    ) external view returns (bool) {
        require(signature.length > 0, "Invalid signature");
        Transfer memory t = transfers[requestId];
        
        bytes32 messageHash = keccak256(abi.encodePacked(
            approvalType,
            requestId,
            t.parcelNumber,
            signer
        ));
        bytes32 ethSignedMessageHash = MessageHashUtils.toEthSignedMessageHash(messageHash);
        address recoveredSigner = ECDSA.recover(ethSignedMessageHash, signature);
        
        return recoveredSigner == signer;
    }

    // ─── Helper ───────────────────────────────────────────────────────────
    function _toAsciiString(address addr) internal pure returns (string memory) {
        bytes memory s = new bytes(42);
        s[0] = '0'; s[1] = 'x';
        for (uint i = 0; i < 20; i++) {
            bytes1 b = bytes1(uint8(uint(uint160(addr)) / (2**(8*(19-i)))));
            s[2+i*2]   = _nibble(uint8(b) / 16);
            s[3+i*2]   = _nibble(uint8(b) % 16);
        }
        return string(s);
    }

    function _nibble(uint8 v) internal pure returns (bytes1) {
        return v < 10 ? bytes1(v + 48) : bytes1(v + 87);
    }
}
