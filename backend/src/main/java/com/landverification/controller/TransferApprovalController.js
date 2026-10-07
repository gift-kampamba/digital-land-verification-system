/**
 * Transfer Approval Controller
 * REST API endpoints for multi-signature approval workflow
 * 
 * Endpoints:
 * POST   /api/transfers/:requestId/approve-l1       - Submit Level-1 approval
 * POST   /api/transfers/:requestId/approve-final    - Submit Level-2 approval
 * GET    /api/transfers/:requestId/status           - Get approval status
 * GET    /api/transfers/:requestId/audit-trail      - Get approval audit trail
 * POST   /api/transfers/:requestId/generate-signature - Generate signature for frontend
 */

const express = require('express');
const router = express.Router();
const TransferApprovalService = require('../blockchain/TransferApprovalService');
const { ethers } = require('ethers');

// Initialize approval service
// In production: load from environment variables
const contractAddress = process.env.LAND_VERIFICATION_CONTRACT;
const contractABI = require('../blockchain/LandVerification.abi.json');
const provider = new ethers.JsonRpcProvider(process.env.ETHEREUM_RPC_URL);

const approvalService = new TransferApprovalService(
  contractAddress,
  contractABI,
  provider
);

/**
 * GET /api/transfers/:requestId/status
 * Get current approval status of a transfer request
 */
router.get('/transfers/:requestId/status', async (req, res) => {
  try {
    const { requestId } = req.params;
    
    const status = await approvalService.getTransferApprovalStatus(parseInt(requestId));
    
    res.json({
      success: true,
      data: status
    });
  } catch (error) {
    console.error('Error fetching transfer status:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * GET /api/transfers/:requestId/audit-trail
 * Get complete approval audit trail with signatures
 */
router.get('/transfers/:requestId/audit-trail', async (req, res) => {
  try {
    const { requestId } = req.params;
    
    const auditTrail = await approvalService.getApprovalAuditTrail(parseInt(requestId));
    
    res.json({
      success: true,
      data: auditTrail
    });
  } catch (error) {
    console.error('Error fetching audit trail:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/transfers/:requestId/generate-signature
 * Generate a signature for the officer to approve
 * 
 * Request body:
 * {
 *   "approvalType": "SENIOR_APPROVAL" | "FINAL_APPROVAL",
 *   "officerAddress": "0x..."
 * }
 */
router.post('/transfers/:requestId/generate-signature', async (req, res) => {
  try {
    const { requestId } = req.params;
    const { approvalType, officerAddress } = req.body;
    
    if (!['SENIOR_APPROVAL', 'FINAL_APPROVAL'].includes(approvalType)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid approval type'
      });
    }

    // Get transfer details
    const transfer = await approvalService.contract.getTransfer(parseInt(requestId));
    const parcelNumber = transfer[0];

    // Create message for signing
    const message = ethers.solidityPacked(
      ['string', 'uint256', 'string', 'address'],
      [approvalType, requestId, parcelNumber, officerAddress]
    );
    
    const messageHash = ethers.keccak256(message);

    // Return message for frontend to sign
    res.json({
      success: true,
      data: {
        requestId: parseInt(requestId),
        parcelNumber,
        approvalType,
        officer: officerAddress,
        messageHash, // Frontend will sign this with officer's wallet
        instructions: `Please sign this message with your ${approvalType === 'SENIOR_APPROVAL' ? 'Senior Officer' : 'Final Authority'} wallet to approve the transfer.`
      }
    });
  } catch (error) {
    console.error('Error generating signature data:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/transfers/:requestId/approve-l1
 * Submit Level-1 (Senior Officer) approval with signature
 * 
 * Request body:
 * {
 *   "signature": "0x1a2b3c...", // Signed by senior officer
 *   "officerAddress": "0x...",
 *   "officerPrivateKey": "0x..." // For server-side signing (secure mode)
 * }
 * 
 * IMPORTANT: In production, never send private keys in requests!
 * Use client-side signing with MetaMask or similar.
 */
router.post('/transfers/:requestId/approve-l1', async (req, res) => {
  try {
    const { requestId } = req.params;
    const { signature, officerAddress } = req.body;

    if (!signature || !officerAddress) {
      return res.status(400).json({
        success: false,
        error: 'Missing signature or officer address'
      });
    }

    // Verify signature before submitting to blockchain
    const transfer = await approvalService.contract.getTransfer(parseInt(requestId));
    const parcelNumber = transfer[0];

    const isValid = approvalService.verifySignature(
      'SENIOR_APPROVAL',
      parseInt(requestId),
      parcelNumber,
      signature,
      officerAddress
    );

    if (!isValid) {
      return res.status(400).json({
        success: false,
        error: 'Invalid signature - signature verification failed'
      });
    }

    // Create signer from officer address and private key
    // PRODUCTION: Implement secure key management (AWS KMS, Azure Key Vault, etc.)
    const { officerPrivateKey } = req.body;
    if (!officerPrivateKey) {
      return res.status(400).json({
        success: false,
        error: 'Officer private key required for server-side signing'
      });
    }

    const officerSigner = new ethers.Wallet(officerPrivateKey, provider);
    
    // Verify the signer address matches
    if (officerSigner.address.toLowerCase() !== officerAddress.toLowerCase()) {
      return res.status(400).json({
        success: false,
        error: 'Signer address does not match'
      });
    }

    // Submit approval to blockchain
    const result = await approvalService.submitSeniorApproval(
      officerSigner,
      parseInt(requestId),
      signature
    );

    if (result.success) {
      res.json({
        success: true,
        data: {
          status: 'APPROVED_L1',
          txHash: result.txHash,
          blockNumber: result.blockNumber,
          message: 'Level-1 approval submitted successfully'
        }
      });
    } else {
      res.status(500).json({
        success: false,
        error: result.error
      });
    }
  } catch (error) {
    console.error('Error submitting L1 approval:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/transfers/:requestId/approve-final
 * Submit Level-2 (Final Authority) approval with signature
 * 
 * Request body:
 * {
 *   "signature": "0x1a2b3c...", // Signed by final authority
 *   "authorityAddress": "0x...",
 *   "authorityPrivateKey": "0x..." // For server-side signing (secure mode)
 * }
 */
router.post('/transfers/:requestId/approve-final', async (req, res) => {
  try {
    const { requestId } = req.params;
    const { signature, authorityAddress } = req.body;

    if (!signature || !authorityAddress) {
      return res.status(400).json({
        success: false,
        error: 'Missing signature or authority address'
      });
    }

    // Get transfer to verify approval stage
    const transfer = await approvalService.contract.getTransfer(parseInt(requestId));
    const parcelNumber = transfer[0];
    const currentStatus = transfer[3]; // APPROVED_L1 = 2

    if (currentStatus !== 2) { // APPROVED_L1
      return res.status(400).json({
        success: false,
        error: 'Transfer must have Level-1 approval before Level-2 approval'
      });
    }

    // Verify signature
    const isValid = approvalService.verifySignature(
      'FINAL_APPROVAL',
      parseInt(requestId),
      parcelNumber,
      signature,
      authorityAddress
    );

    if (!isValid) {
      return res.status(400).json({
        success: false,
        error: 'Invalid signature - signature verification failed'
      });
    }

    // Create signer
    const { authorityPrivateKey } = req.body;
    if (!authorityPrivateKey) {
      return res.status(400).json({
        success: false,
        error: 'Authority private key required for server-side signing'
      });
    }

    const authoritySigner = new ethers.Wallet(authorityPrivateKey, provider);
    
    if (authoritySigner.address.toLowerCase() !== authorityAddress.toLowerCase()) {
      return res.status(400).json({
        success: false,
        error: 'Signer address does not match'
      });
    }

    // Submit approval to blockchain
    const result = await approvalService.submitFinalApproval(
      authoritySigner,
      parseInt(requestId),
      signature
    );

    if (result.success) {
      res.json({
        success: true,
        data: {
          status: 'APPROVED',
          txHash: result.txHash,
          blockNumber: result.blockNumber,
          message: 'Level-2 approval submitted successfully. Ownership transferred to buyer.',
          ownershipStatus: 'TRANSFERRED'
        }
      });
    } else {
      res.status(500).json({
        success: false,
        error: result.error
      });
    }
  } catch (error) {
    console.error('Error submitting final approval:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * GET /api/transfers/pending-approval/list
 * Get all transfers awaiting approval by current user
 * 
 * Query params:
 * - role: "SENIOR_OFFICER"
 * - limit: Number of results (default: 20)
 * - offset: Pagination offset (default: 0)
 */
router.get('/transfers/pending-approval/list', async (req, res) => {
  try {
    const { role, limit = 20, offset = 0 } = req.query;

    // Get transfers from contract based on role
    // This would require additional contract function:
    // getTransfersWithStatus(status, limit, offset)
    
    // For now, return sample structure
    res.json({
      success: true,
      data: {
        role,
        limit: parseInt(limit),
        offset: parseInt(offset),
        total: 0,
        transfers: []
        // [
        //   {
        //     requestId: 1,
        //     parcelNumber: "PARCEL-001",
        //     seller: "0x...",
        //     buyer: "0x...",
        //     status: "SUBMITTED",
        //     submittedAt: "2024-06-01T10:00:00Z",
        //     requiresYourApproval: true
        //   }
        // ]
      }
    });
  } catch (error) {
    console.error('Error fetching pending approvals:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/transfers/:requestId/verify-signature
 * Verify a signature before submission (frontend validation)
 * 
 * Request body:
 * {
 *   "signature": "0x1a2b3c...",
 *   "approvalType": "SENIOR_APPROVAL" | "FINAL_APPROVAL",
 *   "signerAddress": "0x..."
 * }
 */
router.post('/transfers/:requestId/verify-signature', async (req, res) => {
  try {
    const { requestId } = req.params;
    const { signature, approvalType, signerAddress } = req.body;

    if (!signature || !approvalType || !signerAddress) {
      return res.status(400).json({
        success: false,
        error: 'Missing required fields'
      });
    }

    // Get transfer details
    const transfer = await approvalService.contract.getTransfer(parseInt(requestId));
    const parcelNumber = transfer[0];

    // Verify using on-chain function
    const isValid = await approvalService.contract.verifyApprovalSignature(
      parseInt(requestId),
      approvalType,
      signerAddress,
      signature
    );

    res.json({
      success: true,
      data: {
        isValid,
        requestId: parseInt(requestId),
        approvalType,
        signer: signerAddress,
        message: isValid ? 'Signature is valid' : 'Signature is invalid'
      }
    });
  } catch (error) {
    console.error('Error verifying signature:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

module.exports = router;

/*
 * EXAMPLE USAGE
 * 
 * 1. Get transfer status
 * GET /api/transfers/1/status
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": {
 *     "requestId": 1,
 *     "parcelNumber": "PARCEL-001",
 *     "status": "SUBMITTED",
 *     "seniorOfficer": "0x...",
 *     "hasL1Signature": false,
 *     "approverAuthority": "0x...",
 *     "hasFinalSignature": false
 *   }
 * }
 * 
 * 2. Generate signature for senior officer approval
 * POST /api/transfers/1/generate-signature
 * {
 *   "approvalType": "SENIOR_APPROVAL",
 *   "officerAddress": "0x..."
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": {
 *     "requestId": 1,
 *     "parcelNumber": "PARCEL-001",
 *     "approvalType": "SENIOR_APPROVAL",
 *     "messageHash": "0x...",
 *     "instructions": "Please sign this message..."
 *   }
 * }
 * 
 * 3. Submit approval with signature
 * POST /api/transfers/1/approve-l1
 * {
 *   "signature": "0x...",
 *   "officerAddress": "0x...",
 *   "officerPrivateKey": "0x..."
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": {
 *     "status": "APPROVED_L1",
 *     "txHash": "0x...",
 *     "blockNumber": 12345
 *   }
 * }
 * 
 * 4. Get audit trail
 * GET /api/transfers/1/audit-trail
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": {
 *     "requestId": 1,
 *     "currentStatus": "APPROVED",
 *     "approvals": {
 *       "level1": {
 *         "officer": "0x...",
 *         "status": "SIGNED",
 *         "approvedAt": "2024-06-01T11:00:00Z"
 *       },
 *       "level2": {
 *         "authority": "0x...",
 *         "status": "SIGNED",
 *         "approvedAt": "2024-06-01T12:00:00Z"
 *       }
 *     }
 *   }
 * }
 */
