/**
 * Transfer Approval Service
 * Handles multi-signature approval workflow for land ownership transfers
 */

const { ethers } = require('ethers');

class TransferApprovalService {
  constructor(contractAddress, contractABI, provider) {
    this.contractAddress = contractAddress;
    this.contract = new ethers.Contract(contractAddress, contractABI, provider);
    this.provider = provider;
  }

  /**
   * Generate approval signature for senior officer
   * @param {ethers.Signer} signer - Senior officer signer
   * @param {number} requestId - Transfer request ID
   * @param {string} parcelNumber - Parcel number
   * @returns {Promise<string>} - Signature hex string
   */
  async generateSeniorApprovalSignature(signer, requestId, parcelNumber) {
    const message = ethers.solidityPacked(
      ['string', 'uint256', 'string', 'address'],
      ['SENIOR_APPROVAL', requestId, parcelNumber, signer.address]
    );
    
    const messageHash = ethers.keccak256(message);
    const signature = await signer.signMessage(ethers.getBytes(messageHash));
    
    return {
      signature,
      signer: signer.address,
      approvalType: 'SENIOR_APPROVAL',
      requestId,
      parcelNumber,
      timestamp: Math.floor(Date.now() / 1000)
    };
  }

  /**
  * Generate approval signature for the senior officer's final approval
  * @param {ethers.Signer} signer - Senior officer signer
   * @param {number} requestId - Transfer request ID
   * @param {string} parcelNumber - Parcel number
   * @returns {Promise<string>} - Signature hex string
   */
  async generateFinalApprovalSignature(signer, requestId, parcelNumber) {
    const message = ethers.solidityPacked(
      ['string', 'uint256', 'string', 'address'],
      ['FINAL_APPROVAL', requestId, parcelNumber, signer.address]
    );
    
    const messageHash = ethers.keccak256(message);
    const signature = await signer.signMessage(ethers.getBytes(messageHash));
    
    return {
      signature,
      signer: signer.address,
      approvalType: 'FINAL_APPROVAL',
      requestId,
      parcelNumber,
      timestamp: Math.floor(Date.now() / 1000)
    };
  }

  /**
   * Verify a signature before submitting to blockchain
   * @param {string} approvalType - "SENIOR_APPROVAL" or "FINAL_APPROVAL"
   * @param {number} requestId - Transfer request ID
   * @param {string} parcelNumber - Parcel number
   * @param {string} signature - Signature to verify
   * @param {string} expectedSigner - Expected signer address
   * @returns {boolean} - True if signature is valid
   */
  verifySignature(approvalType, requestId, parcelNumber, signature, expectedSigner) {
    try {
      const message = ethers.solidityPacked(
        ['string', 'uint256', 'string', 'address'],
        [approvalType, requestId, parcelNumber, expectedSigner]
      );
      
      const messageHash = ethers.keccak256(message);
      const recovered = ethers.verifyMessage(ethers.getBytes(messageHash), signature);
      
      return recovered.toLowerCase() === expectedSigner.toLowerCase();
    } catch (error) {
      console.error('Signature verification error:', error);
      return false;
    }
  }

  /**
   * Submit senior officer approval to blockchain
   * @param {ethers.Signer} signer - Senior officer signer (connected to contract)
   * @param {number} requestId - Transfer request ID
   * @param {string} signature - Signature from senior officer
   * @returns {Promise<Transaction>} - Transaction receipt
   */
  async submitSeniorApproval(signer, requestId, signature) {
    try {
      const contractWithSigner = this.contract.connect(signer);
      const tx = await contractWithSigner.seniorOfficerApprove(requestId, signature);
      const receipt = await tx.wait();
      
      return {
        success: true,
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber,
        timestamp: Math.floor(Date.now() / 1000),
        status: 'APPROVED_L1'
      };
    } catch (error) {
      console.error('Senior approval submission error:', error);
      return {
        success: false,
        error: error.message,
        timestamp: Math.floor(Date.now() / 1000)
      };
    }
  }

  /**
  * Submit senior officer final approval to blockchain
  * @param {ethers.Signer} signer - Senior officer signer (connected to contract)
   * @param {number} requestId - Transfer request ID
   * @param {string} signature - Signature from final authority
   * @returns {Promise<Transaction>} - Transaction receipt
   */
  async submitFinalApproval(signer, requestId, signature) {
    try {
      const contractWithSigner = this.contract.connect(signer);
      const tx = await contractWithSigner.finalAuthorityApprove(requestId, signature);
      const receipt = await tx.wait();
      
      return {
        success: true,
        txHash: receipt.hash,
        blockNumber: receipt.blockNumber,
        timestamp: Math.floor(Date.now() / 1000),
        status: 'APPROVED'
      };
    } catch (error) {
      console.error('Final approval submission error:', error);
      return {
        success: false,
        error: error.message,
        timestamp: Math.floor(Date.now() / 1000)
      };
    }
  }

  /**
   * Get transfer approval status
   * @param {number} requestId - Transfer request ID
   * @returns {Promise<object>} - Transfer details with approval status
   */
  async getTransferApprovalStatus(requestId) {
    try {
      const transfer = await this.contract.getTransferWithSignatures(requestId);
      
      return {
        requestId,
        parcelNumber: transfer[0],
        status: this._getStatusName(transfer[3]),
        numericStatus: transfer[3],
        sellerWallet: transfer[1],
        buyerWallet: transfer[2],
        submittedAt: transfer[4],
        seniorOfficer: transfer[5],
        hasL1Signature: transfer[6],
        approvedL1At: transfer[7],
        approverAuthority: transfer[8],
        hasFinalSignature: transfer[9],
        approvedFinalAt: transfer[10]
      };
    } catch (error) {
      console.error('Error fetching transfer status:', error);
      return { error: error.message };
    }
  }

  /**
   * Convert numeric status to human-readable name
   * @param {number} statusCode - Numeric status from contract
   * @returns {string} - Human-readable status
   */
  _getStatusName(statusCode) {
    const statuses = {
      0: 'SUBMITTED',
      1: 'UNDER_REVIEW',
      2: 'APPROVED_L1',
      3: 'APPROVED_L2',
      4: 'APPROVED',
      5: 'REJECTED',
      6: 'CANCELLED'
    };
    return statuses[statusCode] || 'UNKNOWN';
  }

  /**
   * Get complete approval audit trail
   * @param {number} requestId - Transfer request ID
   * @returns {Promise<object>} - Audit trail with all approvals and timestamps
   */
  async getApprovalAuditTrail(requestId) {
    try {
      const transfer = await this.contract.getTransfer(requestId);
      const transferWithSigs = await this.contract.getTransferWithSignatures(requestId);
      
      return {
        requestId,
        parcelNumber: transfer[0],
        sellerWallet: transfer[1],
        buyerWallet: transfer[2],
        currentStatus: this._getStatusName(transfer[3]),
        submittedAt: new Date(transfer[4] * 1000).toISOString(),
        approvals: {
          level1: {
            officer: transferWithSigs[5],
            status: transferWithSigs[6] ? 'SIGNED' : 'PENDING',
            approvedAt: transferWithSigs[7] > 0 ? new Date(transferWithSigs[7] * 1000).toISOString() : null
          },
          level2: {
            authority: transferWithSigs[8],
            status: transferWithSigs[9] ? 'SIGNED' : 'PENDING',
            approvedAt: transferWithSigs[10] > 0 ? new Date(transferWithSigs[10] * 1000).toISOString() : null
          }
        },
        rejectionReason: transfer[5] || null
      };
    } catch (error) {
      console.error('Error fetching audit trail:', error);
      return { error: error.message };
    }
  }
}

module.exports = TransferApprovalService;
