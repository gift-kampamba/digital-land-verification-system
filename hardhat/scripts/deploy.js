const { ethers } = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const [deployer, finalAuthority] = await ethers.getSigners();

  console.log("Deploying with account:", deployer.address);
  console.log("Final authority:       ", finalAuthority.address);

  const LandVerification = await ethers.getContractFactory("LandVerification");
  const contract = await LandVerification.deploy(finalAuthority.address);
  await contract.waitForDeployment();

  const contractAddress = await contract.getAddress();
  console.log("\n✅ LandVerification deployed to:", contractAddress);
  console.log("\n👉 Copy this address into:");
  console.log("   backend/src/main/resources/application.properties");
  console.log("   blockchain.contract-address=" + contractAddress);

  // Save deployment info for backend
  const info = {
    contractAddress,
    deployerAddress:      deployer.address,
    finalAuthorityAddress: finalAuthority.address,
    network:    "localhost",
    chainId:    31337,
    deployedAt: new Date().toISOString()
  };

  const dest = path.join(__dirname, "../../backend/src/main/resources/abi/deployment.json");
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, JSON.stringify(info, null, 2));
  console.log("\n✅ Deployment info saved to backend/resources/abi/deployment.json");
}

main().catch((err) => { console.error(err); process.exitCode = 1; });
