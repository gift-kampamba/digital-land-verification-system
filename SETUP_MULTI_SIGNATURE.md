# Multi-Signature Approval Implementation - Setup Guide

## Quick Start

This guide helps you set up and test the multi-signature approval workflow for land ownership transfers.

---

## Prerequisites

- Node.js v16+ and npm
- Hardhat
- ethers.js v6
- OpenZeppelin Contracts

---

## Installation

### 1. Install Dependencies

```bash
cd hardhat
npm install
```

This will install:
- `@openzeppelin/contracts` - Secure ECDSA signature utilities
- `hardhat` - Ethereum development environment
- `ethers` - Blockchain library

### 2. Compile Smart Contract

```bash
npm run compile
```

Output:
```
Successfully compiled 1 contract
```

---

## Understanding the Workflow

### Three-Level Approval Process

```
Level 0: Land Owner
├─ Action: Initiate Transfer
└─ Signs: Transfer request (digital signature)

Level 1: Senior Land Officer
├─ Action: Review and Approve
├─ Signs: ECDSA signature on message hash
└─ Event: SeniorOfficerApproved (with signature recorded)

Level 2: Final Approving Authority
├─ Action: Final Approval
├─ Signs: ECDSA signature on message hash
├─ Event: FinalApproved (with signature recorded)
└─ Result: Ownership transferred on blockchain
```

### Signature Generation

Each signature is generated from a unique message hash:

```javascript
// Message includes:
// - Approval type ("SENIOR_APPROVAL" or "FINAL_APPROVAL")
// - Request ID (prevents replay attacks)
// - Parcel number (ties signature to specific parcel)
// - Signer address (ties signature to specific officer)

messageHash = keccak256(abi.encodePacked(
  "SENIOR_APPROVAL",
  requestId,
  parcelNumber,
  signerAddress
));
```

---

## Running Tests

### Run All Tests

```bash
npm test
```

### Run Multi-Signature Tests Only

```bash
npm test -- test/LandVerification.multisig.test.js
```

### Expected Output

```
LandVerification - Multi-Signature Approval Workflow
  Parcel Registration
    ✓ Should register a parcel (45ms)
  Transfer Initiation
    ✓ Should initiate a transfer (52ms)
  Multi-Signature Approval Workflow
    ✓ Should complete senior officer approval with valid signature (78ms)
    ✓ Should reject senior officer approval with invalid signature (34ms)
    ✓ Should complete final authority approval with valid signature (92ms)
    ✓ Should complete full workflow: register → initiate → senior approve → final approve (145ms)
    ✓ Should retrieve transfer with signature details (48ms)
  Rejection Workflow
    ✓ Should reject transfer at L1 stage (56ms)

8 passing (600ms)
```

---

## Testing Locally

### Start Local Blockchain

Terminal 1:
```bash
npx hardhat node
```

Output:
```
Started HTTP and WebSocket JSON-RPC server at http://127.0.0.1:8545/

Accounts (10 available and unlocked):
Account #0: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 (10000 ETH)
Account #1: 0x70997970C51812e339D9B73b0245ad59E1edd61d (10000 ETH)
...
```

### Deploy Contract

Terminal 2:
```bash
npm run deploy
```

Output:
```
Deploying LandVerification...
Contract deployed at: 0x...
```

### Interact with Contract

Terminal 3:
```bash
node
> const { ethers } = require('ethers');
> const provider = new ethers.JsonRpcProvider('http://127.0.0.1:8545');
> const signer = new ethers.Wallet(privateKey, provider);
> // Use signer to call contract functions
```

---

## Code Examples

### Example 1: Register a Parcel

```javascript
const LandVerification = await ethers.getContractFactory("LandVerification");
const contract = LandVerification.attach(contractAddress);

// Register parcel
const tx = await contract.connect(officer).registerParcel(
  "PARCEL-001",
  "Lusaka",
  "Lusaka District", 
  "123 Main Street",
  1000,
  "Residential",
  ownerAddress,
  "QmHashOfDocument"
);

await tx.wait();
console.log("Parcel registered");
```

### Example 2: Generate Senior Officer Signature

```javascript
const signatureHelper = require('./utils/signatureHelper');

// Generate signature
const signature = await signatureHelper.signApproval(
  seniorOfficerSigner,
  "SENIOR_APPROVAL",
  requestId,
  "PARCEL-001"
);

console.log("Signature:", signature);
// Output: 0x1a2b3c4d...
```

### Example 3: Submit Approval with Signature

```javascript
// Senior officer approves with signature
const tx = await contract
  .connect(seniorOfficerSigner)
  .seniorOfficerApprove(requestId, signature);

const receipt = await tx.wait();
console.log("Approval submitted in tx:", receipt.hash);

// Check transfer status
const transfer = await contract.getTransfer(requestId);
const status = ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED_L1', 'APPROVED_L2', 'APPROVED'];
console.log("Transfer status:", status[transfer[3]]);
```

### Example 4: Get Approval Details

```javascript
// Get transfer with signature information
const transferData = await contract.getTransferWithSignatures(requestId);

console.log({
  parcelNumber: transferData[0],
  senior_officer: transferData[5],
  has_l1_signature: transferData[6],
  approved_l1_at: transferData[7],
  final_authority: transferData[8],
  has_final_signature: transferData[9],
  approved_final_at: transferData[10]
});
```

---

## Signature Verification Flow

### Step 1: Generate Message Hash

```javascript
const messageData = ethers.solidityPacked(
  ['string', 'uint256', 'string', 'address'],
  ['SENIOR_APPROVAL', requestId, parcelNumber, signerAddress]
);
const messageHash = ethers.keccak256(messageData);
```

### Step 2: Sign Message

```javascript
const signature = await signer.signMessage(
  ethers.getBytes(messageHash)
);
// Signature: 0x + 130 hex characters
```

### Step 3: Verify Signature

On-chain verification in contract:
```solidity
bytes32 ethSignedMessageHash = messageHash.toEthSignedMessageHash();
address recoveredSigner = ethSignedMessageHash.recover(signature);
require(recoveredSigner == msg.sender, "Invalid signature");
```

---

## Files Overview

| File | Purpose |
|------|---------|
| `contracts/LandVerification.sol` | Main smart contract with multi-signature support |
| `utils/signatureHelper.js` | Signature generation and verification utilities |
| `test/LandVerification.multisig.test.js` | Comprehensive test suite |
| `blockchain/TransferApprovalService.js` | Backend service for approval workflow |
| `SMART_CONTRACT_MULTI_SIGNATURE_WORKFLOW.md` | Complete documentation |

---

## Common Issues & Solutions

### Issue: "Cannot find module '@openzeppelin/contracts'"

**Solution:**
```bash
npm install @openzeppelin/contracts
```

### Issue: "Invalid signature" Error

**Solution:** Ensure the message hash matches exactly:
- Include all parameters in the same order
- Use correct approval type ("SENIOR_APPROVAL" or "FINAL_APPROVAL")
- Ensure signer address is correct

```javascript
// Wrong - missing parcelNumber
const message = ethers.solidityPacked(
  ['string', 'uint256', 'address'],
  ['SENIOR_APPROVAL', requestId, signerAddress]
);

// Correct - includes all parameters
const message = ethers.solidityPacked(
  ['string', 'uint256', 'string', 'address'],
  ['SENIOR_APPROVAL', requestId, parcelNumber, signerAddress]
);
```

### Issue: "Signer is not authorized"

**Solution:** Register the officer address before using it:
```javascript
// As admin:
await contract.addSeniorOfficer(officerAddress);
```

### Issue: Tests timeout

**Solution:** Increase timeout in test file:
```javascript
describe("My Test", function() {
  this.timeout(10000); // 10 seconds
  
  it("should complete", async function() {
    // Test code
  });
});
```

---

## Deployment Checklist

- [ ] Smart contract compiled without errors
- [ ] All tests passing
- [ ] Officer addresses registered in contract
- [ ] Final authority address set
- [ ] Environment variables configured
- [ ] Signature generation tested
- [ ] Approval workflow tested end-to-end
- [ ] Audit trail verification works
- [ ] Frontend integrated with approval UI

---

## Next Steps

1. **Integration**: Connect with backend API to handle approvals
2. **Frontend**: Create approval UI for officers
3. **Audit**: Log all approvals to database
4. **Testing**: Run end-to-end tests in staging environment
5. **Deployment**: Deploy to testnet, then mainnet

---

## Support

Refer to [SMART_CONTRACT_MULTI_SIGNATURE_WORKFLOW.md](./SMART_CONTRACT_MULTI_SIGNATURE_WORKFLOW.md) for:
- Detailed API documentation
- Architecture overview
- Security considerations
- Complete workflow examples
- Troubleshooting guide

