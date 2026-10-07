const { ethers } = require('ethers');

/**
 * Signature Helper Utilities
 * Used to generate and verify digital signatures for land transfer approvals
 */

/**
 * Generate a digital signature for Senior Officer approval
 * @param {ethers.Signer} signer - The signer (senior officer wallet)
 * @param {number} requestId - Transfer request ID
 * @param {string} parcelNumber - Parcel number
 * @returns {Promise<string>} - Hex-encoded signature
 */
async function generateSeniorOfficerApprovalSignature(signer, requestId, parcelNumber) {
  const message = ethers.solidityPacked(
    ['string', 'uint256', 'string', 'address'],
    ['SENIOR_APPROVAL', requestId, parcelNumber, signer.address]
  );
  
  const messageHash = ethers.keccak256(message);
  const signature = await signer.signMessage(ethers.getBytes(messageHash));
  
  return signature;
}

/**
 * Generate a digital signature for Final Authority approval
 * @param {ethers.Signer} signer - The signer (final authority wallet)
 * @param {number} requestId - Transfer request ID
 * @param {string} parcelNumber - Parcel number
 * @returns {Promise<string>} - Hex-encoded signature
 */
async function generateFinalAuthorityApprovalSignature(signer, requestId, parcelNumber) {
  const message = ethers.solidityPacked(
    ['string', 'uint256', 'string', 'address'],
    ['FINAL_APPROVAL', requestId, parcelNumber, signer.address]
  );
  
  const messageHash = ethers.keccak256(message);
  const signature = await signer.signMessage(ethers.getBytes(messageHash));
  
  return signature;
}

/**
 * Verify a signature against the signer address
 * @param {string} message - Original message that was signed
 * @param {string} signature - The signature to verify
 * @param {string} expectedSigner - Expected signer address
 * @returns {boolean} - True if signature is valid
 */
function verifySignature(message, signature, expectedSigner) {
  try {
    const messageHash = ethers.keccak256(message);
    const recovered = ethers.verifyMessage(ethers.getBytes(messageHash), signature);
    return recovered.toLowerCase() === expectedSigner.toLowerCase();
  } catch (error) {
    console.error('Signature verification error:', error);
    return false;
  }
}

/**
 * Create approval message hash (matches smart contract)
 * @param {string} approvalType - "SENIOR_APPROVAL" or "FINAL_APPROVAL"
 * @param {number} requestId - Transfer request ID
 * @param {string} parcelNumber - Parcel number
 * @param {string} signerAddress - Address of signer
 * @returns {string} - Message hash (hex string)
 */
function createApprovalMessageHash(approvalType, requestId, parcelNumber, signerAddress) {
  const message = ethers.solidityPacked(
    ['string', 'uint256', 'string', 'address'],
    [approvalType, requestId, parcelNumber, signerAddress]
  );
  
  return ethers.keccak256(message);
}

/**
 * Sign an approval message
 * @param {ethers.Signer} signer - The signer
 * @param {string} approvalType - "SENIOR_APPROVAL" or "FINAL_APPROVAL"
 * @param {number} requestId - Transfer request ID
 * @param {string} parcelNumber - Parcel number
 * @returns {Promise<string>} - Signature (hex string)
 */
async function signApproval(signer, approvalType, requestId, parcelNumber) {
  const messageHash = createApprovalMessageHash(approvalType, requestId, parcelNumber, signer.address);
  const signature = await signer.signMessage(ethers.getBytes(messageHash));
  return signature;
}

/**
 * Recover signer address from signature
 * @param {string} message - Original message
 * @param {string} signature - The signature
 * @returns {string} - Recovered address
 */
function recoverSignerAddress(message, signature) {
  try {
    const messageHash = ethers.keccak256(message);
    const recovered = ethers.verifyMessage(ethers.getBytes(messageHash), signature);
    return recovered;
  } catch (error) {
    console.error('Address recovery error:', error);
    return null;
  }
}

module.exports = {
  generateSeniorOfficerApprovalSignature,
  generateFinalAuthorityApprovalSignature,
  verifySignature,
  createApprovalMessageHash,
  signApproval,
  recoverSignerAddress
};
