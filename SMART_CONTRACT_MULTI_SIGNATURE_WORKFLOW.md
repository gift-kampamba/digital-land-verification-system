# Smart Contract Multi-Signature Approval Workflow

## Overview

The LandVerification smart contract implements a **three-level digital signature-based approval workflow** for land ownership transfers:

1. **Level 0**: Land Owner initiates transfer (with digital signature)
2. **Level 1**: Senior Land Officer approves (with ECDSA signature verification)
3. **Level 2**: Final Approving Authority approves (with ECDSA signature verification)

Each approval requires a valid digital signature to prevent unauthorized approvals.

---

## Architecture

### Smart Contract Components

#### Roles

- **Land Officer** - Registers parcels on the blockchain
- **Senior Officer** - Level-1 approval authority (requires digital signature)
- **Final Authority** - Level-2 approval authority (requires digital signature)
- **System Admin** - Manages officer addresses

#### Transfer States

```
SUBMITTED (0)
    ↓
UNDER_REVIEW (1)
    ↓
APPROVED_L1 (2) ← Senior Officer signature required
    ↓
APPROVED_L2 (3)
    ↓
APPROVED (4) ← Final Authority signature required
    ↓
Ownership updated on blockchain
```

Or at any stage:
```
REJECTED (5) - Transfer cancelled
CANCELLED (6) - Transfer cancelled by owner
```

---

## Digital Signature Process

### Message Hashing

For Senior Officer Approval:
```solidity
bytes32 messageHash = keccak256(abi.encodePacked(
    "SENIOR_APPROVAL",
    requestId,
    parcelNumber,
    signerAddress
));
```

For Final Authority Approval:
```solidity
bytes32 messageHash = keccak256(abi.encodePacked(
    "FINAL_APPROVAL",
    requestId,
    parcelNumber,
    signerAddress
));
```

### Signature Verification

The contract uses ECDSA (Elliptic Curve Digital Signature Algorithm) to:
1. Recover the signer address from the signature
2. Verify it matches the expected officer/authority
3. Check that the signer is authorized for that role

```solidity
bytes32 ethSignedMessageHash = messageHash.toEthSignedMessageHash();
address recoveredSigner = ethSignedMessageHash.recover(signature);
require(recoveredSigner == msg.sender, "Invalid signature");
require(seniorOfficers[recoveredSigner], "Signer is not authorized");
```

---

## Implementation Guide

### 1. Register a Parcel

```javascript
// Officer registers a parcel
const tx = await landVerification.connect(officer).registerParcel(
  "PARCEL-001",
  "Lusaka",
  "Lusaka District",
  "123 Main Street",
  1000,        // area in sqm
  "Residential",
  ownerAddress,
  "QmDocumentHash"
);

await tx.wait();
```

### 2. Initiate Transfer

```javascript
// Owner initiates transfer request
const tx = await landVerification.connect(owner).initiateTransfer(
  "PARCEL-001",
  buyerAddress,
  "Selling property",
  "owner_digital_signature_hex"
);

const receipt = await tx.wait();
const transferEvent = receipt.logs[0];
const requestId = transferEvent.args[0];
```

### 3. Senior Officer Approves (Level 1)

```javascript
// Step 1: Generate signature
const signatureHelper = require('./utils/signatureHelper');
const signature = await signatureHelper.signApproval(
  seniorOfficerSigner,
  "SENIOR_APPROVAL",
  requestId,
  "PARCEL-001"
);

// Step 2: Submit approval with signature
const tx = await landVerification
  .connect(seniorOfficerSigner)
  .seniorOfficerApprove(requestId, signature);

await tx.wait();
console.log("✓ Senior Officer Approval: Level-1 complete");
```

### 4. Final Authority Approves (Level 2)

```javascript
// Step 1: Generate signature
const signature = await signatureHelper.signApproval(
  finalAuthoritySigner,
  "FINAL_APPROVAL",
  requestId,
  "PARCEL-001"
);

// Step 2: Submit approval with signature
const tx = await landVerification
  .connect(finalAuthoritySigner)
  .finalAuthorityApprove(requestId, signature);

await tx.wait();
console.log("✓ Final Authority Approval: Transfer approved");
console.log("✓ Ownership transferred to buyer on blockchain");
```

---

## API Functions

### Writing Functions (Require Signatures)

#### `seniorOfficerApprove(requestId, signature)`
- **Caller**: Senior Officer
- **Requires**: Valid ECDSA signature
- **Updates**: Transfer status to APPROVED_L1
- **Events**: `SeniorOfficerApproved`

```solidity
function seniorOfficerApprove(uint256 requestId, bytes memory signature) external onlySeniorOfficer
```

#### `finalAuthorityApprove(requestId, signature)`
- **Caller**: Final Authority
- **Requires**: Valid ECDSA signature
- **Requires**: Previous L1 approval
- **Updates**: Transfer status to APPROVED, transfers ownership
- **Events**: `FinalApproved`, `OwnershipRecorded`

```solidity
function finalAuthorityApprove(uint256 requestId, bytes memory signature) external onlyFinalAuthority
```

### Reading Functions (View Only)

#### `getTransferWithSignatures(requestId)`
Returns transfer details including signature presence flags:
```javascript
{
  parcelNumber,
  sellerWallet,
  buyerWallet,
  status,
  submittedAt,
  seniorOfficer,           // Address of approving officer
  hasL1Signature: boolean, // True if signed
  approvedL1At,            // Timestamp of approval
  approverAuthority,       // Address of final approver
  hasFinalSignature: boolean,
  approvedFinalAt          // Timestamp of approval
}
```

#### `verifyApprovalSignature(requestId, approvalType, signer, signature)`
Frontend validation before submitting to blockchain:
```javascript
const isValid = await landVerification.verifyApprovalSignature(
  requestId,
  "SENIOR_APPROVAL",
  seniorOfficerAddress,
  signatureHex
);
```

---

## Signature Generation Examples

### Using ethers.js v6

```javascript
const { ethers } = require('ethers');

// Create signer from private key
const signer = new ethers.Wallet(privateKey, provider);

// Generate approval message and signature
async function approveTransfer(requestId, parcelNumber) {
  const message = ethers.solidityPacked(
    ['string', 'uint256', 'string', 'address'],
    ['SENIOR_APPROVAL', requestId, parcelNumber, signer.address]
  );
  
  const messageHash = ethers.keccak256(message);
  const signature = await signer.signMessage(ethers.getBytes(messageHash));
  
  return signature; // 0x + 130 hex characters
}
```

### Using Helper Functions

```javascript
const signatureHelper = require('./utils/signatureHelper');

// Generate senior officer signature
const signature = await signatureHelper.signApproval(
  seniorOfficer,
  "SENIOR_APPROVAL",
  requestId,
  "PARCEL-001"
);

// Verify before submission
const isValid = signatureHelper.verifyApprovalSignature(
  messageHash,
  signature,
  seniorOfficer.address
);
```

---

## Frontend Integration

### Step-by-Step Approval Flow

```javascript
// 1. Get pending transfers
const pendingTransfers = await api.getTransfersAwaitingApproval();

// 2. For each transfer, show approval UI
const transfer = pendingTransfers[0];
console.log(`Transfer ${transfer.requestId}: PARCEL-${transfer.parcelNumber}`);

// 3. Officer clicks "Approve"
async function approveTransfer(requestId, parcelNumber) {
  // Generate signature
  const signature = await signatureHelper.signApproval(
    currentUserSigner,
    "SENIOR_APPROVAL",
    requestId,
    parcelNumber
  );
  
  // Show signature to user for confirmation
  showSignatureDialog({
    message: `Approve transfer of ${parcelNumber}?`,
    signature: signature.substring(0, 20) + "..."
  });
  
  // Submit to blockchain
  const result = await submitApproval(requestId, signature);
  
  if (result.success) {
    showNotification("✓ Approval submitted to blockchain");
    // Update UI with new status
    updateTransferStatus(requestId, "APPROVED_L1");
  } else {
    showError("Approval failed: " + result.error);
  }
}
```

---

## Testing

### Run Multi-Signature Tests

```bash
cd hardhat
npm install
npm run test -- test/LandVerification.multisig.test.js
```

### Test Coverage

The test suite validates:
- ✅ Parcel registration
- ✅ Transfer initiation
- ✅ Senior officer approval with valid signature
- ✅ Rejection of approvals with invalid signatures
- ✅ Final authority approval
- ✅ Complete workflow: register → initiate → approve L1 → approve L2
- ✅ Signature verification and recovery
- ✅ Ownership transfer on blockchain
- ✅ Audit trail generation

---

## Security Considerations

### Signature Verification

1. **ECDSA Standard**: Uses OpenZeppelin's ECDSA library for secure signature recovery
2. **Message Hashing**: Includes nonce (requestId) to prevent replay attacks
3. **Signer Verification**: Recovered signer must match caller address
4. **Role Verification**: Recovered signer must be authorized for the role

### Attack Prevention

| Attack | Prevention |
|--------|-----------|
| **Replay Attack** | Unique (requestId, parcelNumber, signer) in message |
| **Wrong Signer** | Signature verification + signer role check |
| **Order Violation** | Status machine enforces L1→L2 sequence |
| **Unauthorized Role** | Role-based access control (onlySeniorOfficer, onlyFinalAuthority) |
| **Signature Forgery** | ECDSA cryptographic verification |

---

## Events and Audit Trail

### Approval Events

Each approval emits an event with full details:

```javascript
event SeniorOfficerApproved(
  uint256 indexed requestId,
  address indexed officer,
  bytes signature,
  uint256 timestamp
);

event FinalApproved(
  uint256 indexed requestId,
  string indexed parcelNumber,
  address newOwner,
  address approver,
  bytes signature,
  uint256 timestamp
);
```

### Retrieving Audit Trail

```javascript
// Get complete approval history
const auditTrail = await api.getApprovalAuditTrail(requestId);

console.log(auditTrail);
// {
//   requestId: 1,
//   parcelNumber: "PARCEL-001",
//   currentStatus: "APPROVED",
//   submittedAt: "2024-06-01T10:00:00Z",
//   approvals: {
//     level1: {
//       officer: "0x...",
//       status: "SIGNED",
//       approvedAt: "2024-06-01T11:00:00Z"
//     },
//     level2: {
//       authority: "0x...",
//       status: "SIGNED",
//       approvedAt: "2024-06-01T12:00:00Z"
//     }
//   }
// }
```

---

## Deployment

### Deploy to Local Network

```bash
cd hardhat
npm install
npm run compile
npm run node      # In terminal 1
npm run deploy    # In terminal 2
npm run test
```

### Deploy to Testnet/Mainnet

1. Update `hardhat.config.js` with network RPC URL
2. Set environment variables for deployer private key
3. Update `scripts/deploy.js` with correct addresses
4. Run: `npx hardhat run scripts/deploy.js --network <network-name>`

---

## References

- [ECDSA Signature Standard](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm)
- [OpenZeppelin ECDSA Library](https://docs.openzeppelin.com/contracts/4.x/utilities#cryptography)
- [Solidity Message Signing](https://docs.soliditylang.org/en/latest/units-and-global-variables.html#mathematical-and-cryptographic-functions)
- [EIP-191: Personal Sign](https://eips.ethereum.org/EIPS/eip-191)

---

## Troubleshooting

### "Invalid signature" Error

```javascript
// ❌ Wrong: Using different data for hash and signature
const messageHash = keccak256(abi.encodePacked("SENIOR_APPROVAL", requestId));
// But signature was created with parcelNumber included

// ✅ Correct: Must match exactly
const messageHash = keccak256(abi.encodePacked(
  "SENIOR_APPROVAL",
  requestId,
  parcelNumber,  // Must be included
  signer.address  // Must be included
));
```

### "Signer is not authorized" Error

```javascript
// ❌ Wrong: Officer not registered
// ✅ Correct: Register officer first
await landVerification.addSeniorOfficer(officerAddress);
```

### Signature Not Verified

```javascript
// ❌ Wrong: Signer submits signature from different address
await landVerification.connect(differentOfficer).seniorOfficerApprove(requestId, signature);

// ✅ Correct: Signature must be from caller
const signature = await signerA.signMessage(...);
await landVerification.connect(signerA).seniorOfficerApprove(..., signature);
```

---

## Support

For issues or questions:
1. Check the test file: `hardhat/test/LandVerification.multisig.test.js`
2. Review signature helper: `hardhat/utils/signatureHelper.js`
3. Consult smart contract documentation in contract comments
