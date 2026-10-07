const { expect } = require("chai");
const { ethers } = require("hardhat");
const signatureHelper = require("../utils/signatureHelper");

describe("LandVerification - Multi-Signature Approval Workflow", function () {
  let landVerification;
  let admin, officer, seniorOfficer, finalAuthority, owner, buyer;

  beforeEach(async function () {
    // Get signers
    [admin, officer, seniorOfficer, finalAuthority, owner, buyer] = await ethers.getSigners();

    // Deploy contract
    const LandVerification = await ethers.getContractFactory("LandVerification");
    landVerification = await LandVerification.deploy(finalAuthority.address);
    await landVerification.waitForDeployment();

    // Setup officers
    await landVerification.addLandOfficer(officer.address);
    await landVerification.addSeniorOfficer(seniorOfficer.address);
  });

  describe("Parcel Registration", function () {
    it("Should register a parcel", async function () {
      await landVerification.connect(officer).registerParcel(
        "PARCEL-001",
        "Lusaka",
        "Lusaka District",
        "123 Main Street, Lusaka",
        1000,
        "Residential",
        owner.address,
        "QmHashOfDocument123"
      );

      const parcel = await landVerification.getParcel("PARCEL-001");
      expect(parcel[0]).to.equal("Lusaka");
      expect(parcel[4]).to.equal(owner.address);
    });
  });

  describe("Transfer Initiation", function () {
    beforeEach(async function () {
      // Register a parcel
      await landVerification.connect(officer).registerParcel(
        "PARCEL-001",
        "Lusaka",
        "Lusaka District",
        "123 Main Street, Lusaka",
        1000,
        "Residential",
        owner.address,
        "QmHashOfDocument123"
      );
    });

    it("Should initiate a transfer", async function () {
      const txn = await landVerification.connect(owner).initiateTransfer(
        "PARCEL-001",
        buyer.address,
        "Selling property",
        "owner_digital_signature_123"
      );

      const receipt = await txn.wait();
      const event = receipt.logs.find(log => {
        try {
          const decoded = landVerification.interface.parseLog(log);
          return decoded?.name === "TransferInitiated";
        } catch (e) {
          return false;
        }
      });

      expect(event).to.exist;
    });
  });

  describe("Multi-Signature Approval Workflow", function () {
    let requestId;

    beforeEach(async function () {
      // Register parcel
      await landVerification.connect(officer).registerParcel(
        "PARCEL-002",
        "Ndola",
        "Ndola District",
        "456 Oak Avenue, Ndola",
        2000,
        "Commercial",
        owner.address,
        "QmHashOfDocument456"
      );

      // Initiate transfer
      const txn = await landVerification.connect(owner).initiateTransfer(
        "PARCEL-002",
        buyer.address,
        "Commercial property transfer",
        "owner_digital_signature_456"
      );

      const receipt = await txn.wait();
      const event = receipt.logs.find(log => {
        try {
          const decoded = landVerification.interface.parseLog(log);
          return decoded?.name === "TransferInitiated";
        } catch (e) {
          return false;
        }
      });

      requestId = event.args[0];
    });

    it("Should complete senior officer approval with valid signature", async function () {
      // Generate signature
      const signature = await signatureHelper.signApproval(
        seniorOfficer,
        "SENIOR_APPROVAL",
        requestId,
        "PARCEL-002"
      );

      // Submit approval
      const txn = await landVerification
        .connect(seniorOfficer)
        .seniorOfficerApprove(requestId, seniorOfficer.address, signature);

      const receipt = await txn.wait();
      const event = receipt.logs.find(log => {
        try {
          const decoded = landVerification.interface.parseLog(log);
          return decoded?.name === "SeniorOfficerApproved";
        } catch (e) {
          return false;
        }
      });

      expect(event).to.exist;
      expect(event.args[1]).to.equal(seniorOfficer.address);

      // Verify transfer status
      const transfer = await landVerification.getTransfer(requestId);
      expect(transfer[3]).to.equal(1); // APPROVED_L1 status
    });

    it("Should reject senior officer approval with invalid signature", async function () {
      // Generate signature with wrong officer
      const wrongSignature = await signatureHelper.signApproval(
        officer,
        "SENIOR_APPROVAL",
        requestId,
        "PARCEL-002"
      );

      // Try to submit with senior officer but wrong signature
      await expect(
        landVerification.connect(seniorOfficer).seniorOfficerApprove(requestId, seniorOfficer.address, wrongSignature)
      ).to.be.revertedWith("Invalid signature");
    });

    it("Should complete final authority approval with valid signature", async function () {
      // Senior officer approval
      const seniorSignature = await signatureHelper.signApproval(
        seniorOfficer,
        "SENIOR_APPROVAL",
        requestId,
        "PARCEL-002"
      );
      await landVerification.connect(seniorOfficer).seniorOfficerApprove(requestId, seniorOfficer.address, seniorSignature);

      // Final authority approval
      const finalSignature = await signatureHelper.signApproval(
        finalAuthority,
        "FINAL_APPROVAL",
        requestId,
        "PARCEL-002"
      );

      const txn = await landVerification
        .connect(finalAuthority)
        .finalAuthorityApprove(requestId, finalAuthority.address, finalSignature);

      const receipt = await txn.wait();
      const event = receipt.logs.find(log => {
        try {
          const decoded = landVerification.interface.parseLog(log);
          return decoded?.name === "FinalApproved";
        } catch (e) {
          return false;
        }
      });

      expect(event).to.exist;

      // Verify parcel ownership changed
      const parcel = await landVerification.getParcel("PARCEL-002");
      expect(parcel[4]).to.equal(buyer.address);
    });

    it("Should complete full workflow: register → initiate → senior approve → final approve", async function () {
      // Parcel already registered in beforeEach
      // Transfer already initiated in beforeEach

      // Step 1: Senior Officer approves with signature
      const seniorSignature = await signatureHelper.signApproval(
        seniorOfficer,
        "SENIOR_APPROVAL",
        requestId,
        "PARCEL-002"
      );

      await landVerification
        .connect(seniorOfficer)
        .seniorOfficerApprove(requestId, seniorOfficer.address, seniorSignature);

      let transfer = await landVerification.getTransfer(requestId);
      expect(transfer[3]).to.equal(1); // APPROVED_L1

      // Step 2: Final Authority approves with signature
      const finalSignature = await signatureHelper.signApproval(
        finalAuthority,
        "FINAL_APPROVAL",
        requestId,
        "PARCEL-002"
      );

      await landVerification
        .connect(finalAuthority)
        .finalAuthorityApprove(requestId, finalAuthority.address, finalSignature);

      transfer = await landVerification.getTransfer(requestId);
      expect(transfer[3]).to.equal(4); // APPROVED

      // Verify new owner
      const parcel = await landVerification.getParcel("PARCEL-002");
      expect(parcel[4]).to.equal(buyer.address);

      // Verify ownership history
      const history = await landVerification.getOwnershipHistory("PARCEL-002");
      expect(history[history.length - 1]).to.include(buyer.address.substring(2).toLowerCase());
    });

    it("Should retrieve transfer with signature details", async function () {
      // Senior approval
      const seniorSignature = await signatureHelper.signApproval(
        seniorOfficer,
        "SENIOR_APPROVAL",
        requestId,
        "PARCEL-002"
      );
      await landVerification.connect(seniorOfficer).seniorOfficerApprove(requestId, seniorOfficer.address, seniorSignature);

      // Get transfer with signatures
      const transferData = await landVerification.getTransferWithSignatures(requestId);
      
      expect(transferData[5]).to.equal(seniorOfficer.address); // seniorOfficer address
      expect(transferData[6]).to.be.true; // hasL1Signature
      expect(transferData[7]).to.be.gt(0); // approvedL1At timestamp
    });
  });

  describe("Rejection Workflow", function () {
    let requestId;

    beforeEach(async function () {
      // Register parcel
      await landVerification.connect(officer).registerParcel(
        "PARCEL-003",
        "Copperbelt",
        "Ndola",
        "789 Copper Street",
        5000,
        "Industrial",
        owner.address,
        "QmHashOfDocument789"
      );

      // Initiate transfer
      const txn = await landVerification.connect(owner).initiateTransfer(
        "PARCEL-003",
        buyer.address,
        "Industrial property transfer",
        "owner_digital_signature_789"
      );

      const receipt = await txn.wait();
      const event = receipt.logs.find(log => {
        try {
          const decoded = landVerification.interface.parseLog(log);
          return decoded?.name === "TransferInitiated";
        } catch (e) {
          return false;
        }
      });

      requestId = event.args[0];
    });

    it("Should reject transfer at L1 stage", async function () {
      await landVerification
        .connect(seniorOfficer)
        .rejectTransfer(requestId, "Insufficient documentation");

      const transfer = await landVerification.getTransfer(requestId);
      expect(transfer[3]).to.equal(5); // REJECTED status
    });
  });
});
