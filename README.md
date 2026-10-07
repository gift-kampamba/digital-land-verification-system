# Blockchain-Based Digital Land Verification System
**Student:** Gift Kampamba | **Student No:** 22103754
**Supervisor:** Dr Alice P. Shemi | **CBU — School of ICT**

---

## Prerequisites
| Tool | Version |
|------|---------|
| Java (Temurin) | 25 LTS |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Maven | 3.9+ |
| VS Code | Latest |

---

## System-based Digital Signing

Instead of a wallet-driven approval flow, the land verification system should use a government-style digital signing process:

```text
Click Approve & Sign
      ↓
System displays signing confirmation
      ↓
User confirms their identity
      ↓
Digital signature generated
      ↓
Approval recorded
      ↓
Blockchain transaction recorded
```

### Example interface

#### Landowner
```text
TRANSFER CONFIRMATION

Parcel: ZM-LUS-2026-00041
Seller: Nazdac Zambia
Buyer: John Banda

By signing below, you confirm that the
information provided is correct.

[ Digital Signature Area ]

John Banda
____________________________

[ Cancel ]     [ Sign & Submit ]
```

#### Land Officer
```text
LAND OFFICER APPROVAL

Transfer Request: TR-2026-0018

I confirm that I have reviewed the transfer
request and supporting information.

Signed by:
Sydney Nkumbila

Signature Status: Required

[ Reject ]     [ Approve & Sign ]
```

#### Senior Officer
```text
FINAL APPROVAL

Transfer Request: TR-2026-0018

I confirm that this transfer has passed the
required review and approval procedures.

Senior Officer: Sydney Nkumbila

Signature Status: Required

[ Reject ]     [ Final Approve & Sign ]
```

### Recommended architecture

The blockchain remains part of the system, but the user experience should remain simple and professional:

```text
User
   ↓
ARMS System
   ↓
System Authentication
   ↓
Digital Signing Confirmation
   ↓
Spring Boot Backend
   ↓
Cryptographic Hash Generated
   ↓
LandRegistry Smart Contract
   ↓
Hardhat Blockchain
```

For a government land verification platform, the interface should use clearer language such as:
- Sign & Submit Transfer
- Approve & Sign
- Final Approve & Sign
- Signature Verified
- Transaction Authenticated

This keeps blockchain details available for authorised officers and audit interfaces without exposing confusing wallet metaphors to ordinary users.

---

## Setup Instructions (follow in order)

### 1. Database
Open MySQL Workbench or terminal and run:
```
mysql -u root -p < backend/src/main/resources/db/schema.sql
```

### 2. Hardhat Blockchain
```bash
cd hardhat
npm install
npx hardhat node                                              # terminal 1 — keep running
npx hardhat run scripts/deploy.js --network localhost         # terminal 2
npx hardhat run scripts/exportAbi.js --network localhost      # terminal 2
```
Copy the printed contract address into `backend/src/main/resources/application.properties`.

### 3. Generate Java Web3j Wrapper (one time)
Download the Web3j CLI jar from https://github.com/web3j/web3j/releases
```bash
java -jar web3j-4.10.3.jar generate solidity \
  -a backend/src/main/resources/abi/LandVerification.json \
  -o backend/src/main/java \
  -p com.landverification.web3
```

### 4. Email Configuration (CRITICAL FOR PRODUCTION)
The system requires email configuration to send invitation codes to land officers.

#### For Gmail (Recommended):
1. **Enable 2-Factor Authentication** on your Gmail account
2. **Generate App Password**:
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (custom name)"
   - Enter "Land Verification System" as the name
   - Copy the 16-character password
3. **Update Configuration**:
   - Open `backend/src/main/resources/application.properties`
   - Replace `YOUR_EMAIL@gmail.com` with your Gmail address
   - Replace `YOUR_16_CHAR_APP_PASSWORD` with the generated app password

#### For Other Email Providers:
- **Outlook/Hotmail**: `smtp-mail.outlook.com:587`
- **Yahoo**: `smtp.mail.yahoo.com:587`
- **Custom SMTP**: Contact your email administrator for settings

#### Test Email Configuration:
1. Start the backend server
2. Login as System Admin
3. Go to Admin Dashboard → "Test Email Configuration"
4. Enter your email and click "Send Test Email"
5. Check your inbox for the test message

### 5. Backend
```bash
cd backend
mvn spring-boot:run
# API  → http://localhost:8080
# Docs → http://localhost:8080/swagger-ui.html
```

### 5. Frontend
In VS Code install the **Live Server** extension, right-click `frontend/public/index.html` → Open with Live Server.
