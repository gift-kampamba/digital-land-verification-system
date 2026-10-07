const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("LandVerification", function () {
  let contract, admin, officer, senior, finalAuth, owner, buyer;

  beforeEach(async function () {
    [admin, officer, senior, finalAuth, owner, buyer] = await ethers.getSigners();
    const Factory = await ethers.getContractFactory("LandVerification");
    contract = await Factory.deploy(finalAuth.address);
    await contract.waitForDeployment();
    await contract.addLandOfficer(officer.address);
    await contract.addSeniorOfficer(senior.address);
  });

  it("registers a parcel", async function () {
    await contract.connect(officer).registerParcel(
      "ZM-LSK-00001", "Lusaka", "Lusaka Central", "Plot 12, Cairo Rd",
      500, "RESIDENTIAL", owner.address, "hash_abc123"
    );
    const [province] = await contract.getParcel("ZM-LSK-00001");
    expect(province).to.equal("Lusaka");
  });

  it("completes the full 4-step transfer workflow", async function () {
    await contract.connect(officer).registerParcel(
      "ZM-LSK-00002", "Lusaka", "Chilenje", "Plot 5",
      300, "RESIDENTIAL", owner.address, "hash_def456"
    );
    const reqId = await contract.connect(owner)
      .initiateTransfer.staticCall("ZM-LSK-00002", buyer.address, "Selling", "0xsig");
    await contract.connect(owner).initiateTransfer("ZM-LSK-00002", buyer.address, "Selling", "0xsig");
    const seniorMessage = ethers.solidityPackedKeccak256(
      ["string", "uint256", "string", "address"],
      ["SENIOR_APPROVAL", reqId, "ZM-LSK-00002", senior.address]
    );
    const finalMessage = ethers.solidityPackedKeccak256(
      ["string", "uint256", "string", "address"],
      ["FINAL_APPROVAL", reqId, "ZM-LSK-00002", finalAuth.address]
    );
    await contract.connect(senior).seniorOfficerApprove(
      reqId, senior.address, await senior.signMessage(ethers.getBytes(seniorMessage))
    );
    await contract.connect(finalAuth).finalAuthorityApprove(
      reqId, finalAuth.address, await finalAuth.signMessage(ethers.getBytes(finalMessage))
    );

    const [,,,, currentOwner] = await contract.getParcel("ZM-LSK-00002");
    expect(currentOwner).to.equal(buyer.address);
  });

  it("rejects a transfer with a reason", async function () {
    await contract.connect(officer).registerParcel(
      "ZM-LSK-00003", "Copperbelt", "Ndola", "Plot 9",
      200, "COMMERCIAL", owner.address, "hash_ghi"
    );
    const reqId = await contract.connect(owner)
      .initiateTransfer.staticCall("ZM-LSK-00003", buyer.address, "Sale", "0xsig2");
    await contract.connect(owner).initiateTransfer("ZM-LSK-00003", buyer.address, "Sale", "0xsig2");
    await contract.connect(senior).rejectTransfer(reqId, "Incomplete documents submitted");

    const [,,,,, rejection] = await contract.getTransfer(reqId);
    expect(rejection).to.equal("Incomplete documents submitted");
  });

  it("prevents non-owner from initiating transfer", async function () {
    await contract.connect(officer).registerParcel(
      "ZM-LSK-00004", "Lusaka", "Matero", "House 7",
      150, "RESIDENTIAL", owner.address, "hash_jkl"
    );
    await expect(
      contract.connect(buyer).initiateTransfer("ZM-LSK-00004", admin.address, "Fake", "0xbad")
    ).to.be.revertedWith("Not the parcel owner");
  });

  it("returns ownership history", async function () {
    await contract.connect(officer).registerParcel(
      "ZM-LSK-00005", "Eastern", "Chipata", "Farm 3",
      5000, "AGRICULTURAL", owner.address, "hash_mno"
    );
    const history = await contract.getOwnershipHistory("ZM-LSK-00005");
    expect(history.length).to.equal(1);
  });
});
