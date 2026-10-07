const fs = require("fs");
const path = require("path");

async function main() {
  const src  = path.join(__dirname, "../artifacts/contracts/LandVerification.sol/LandVerification.json");
  const dest = path.join(__dirname, "../../backend/src/main/resources/abi/LandVerification.json");

  if (!fs.existsSync(src)) {
    throw new Error("Run 'npx hardhat compile' first — artifact not found at:\n" + src);
  }

  const artifact = JSON.parse(fs.readFileSync(src, "utf8"));
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, JSON.stringify({ abi: artifact.abi }, null, 2));
  console.log("✅ ABI exported to:", dest);
}

main().catch(console.error);
