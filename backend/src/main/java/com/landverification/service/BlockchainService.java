package com.landverification.service;

import com.landverification.model.BlockchainAuditLog;
import com.landverification.repository.BlockchainAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainService {

    private final Web3j web3j;
    private final Credentials credentials;
    private final DefaultGasProvider gasProvider;
    private final BlockchainAuditLogRepository auditLogRepository;

    @Value("${blockchain.contract-address}")
    private String contractAddress;

    private static final long CHAIN_ID = 31337L;

    private record TransactionResult(String txHash, Long blockNumber, Long gasUsed) {}

    @PostConstruct
    public void validateBlockchainConnection() {
        if (contractAddress == null || contractAddress.isBlank()
                || !contractAddress.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new IllegalStateException("Invalid or missing blockchain.contract-address: " + contractAddress);
        }

        try {
            var chainIdResponse = web3j.ethChainId().send();
            if (chainIdResponse.hasError()) {
                throw new IllegalStateException("Failed to connect to blockchain node: "
                        + chainIdResponse.getError().getMessage());
            }
            log.info("Blockchain node connected. Chain ID: {}. Contract address: {}",
                    chainIdResponse.getChainId(), contractAddress);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to connect to the blockchain node", e);
        }
    }

    private RawTransactionManager transactionManager() {
        return new RawTransactionManager(web3j, credentials, CHAIN_ID);
    }

    // ── Register parcel ───────────────────────────────────────────────────
    public String registerParcel(String parcelNumber, String province, String district,
                                  String locationAddress, BigInteger areaSqm, String landUse,
                                  String ownerWallet, String documentHash,
                                  Integer relatedParcelId, Integer initiatedBy) {
        try {
            String data = FunctionEncoder.encode(new Function(
                    "registerParcel",
                    List.of(new Utf8String(parcelNumber), new Utf8String(province),
                            new Utf8String(district), new Utf8String(locationAddress),
                            new Uint256(areaSqm), new Utf8String(landUse),
                            new Address(ownerWallet), new Utf8String(documentHash)),
                    List.of()));
            TransactionResult result = sendTransaction(data);
                saveAuditLog(result.txHash(), BlockchainAuditLog.EventType.PARCEL_REGISTERED,
                    relatedParcelId, null, initiatedBy,
                    sha256(parcelNumber + ownerWallet + documentHash),
                    result.blockNumber(), result.gasUsed(), true);
            log.info("Parcel {} registered on blockchain. TxHash: {}", parcelNumber, result.txHash());
            return result.txHash();
        } catch (Exception e) {
            log.error("Blockchain registerParcel failed: {}", e.getMessage());
            throw new RuntimeException("Blockchain error: " + e.getMessage(), e);
        }
    }

    public String getDefaultOwnerAddress() {
        return credentials.getAddress();
    }

    public String getContractAddress() {
        return contractAddress;
    }

    // ── Initiate transfer ─────────────────────────────────────────────────
    public String initiateTransfer(String parcelNumber, String buyerWallet,
                                    String reason, String ownerSignature,
                                    Integer relatedParcelId, Integer relatedRequestId,
                                    Integer initiatedBy) {
        try {
            String data = FunctionEncoder.encode(new Function(
                    "initiateTransfer",
                    List.of(new Utf8String(parcelNumber), new Address(buyerWallet),
                            new Utf8String(reason), new Utf8String(ownerSignature)),
                    List.of()));
            TransactionResult result = sendTransaction(data);
                saveAuditLog(result.txHash(), BlockchainAuditLog.EventType.TRANSFER_INITIATED,
                    relatedParcelId, relatedRequestId, initiatedBy,
                    sha256(parcelNumber + buyerWallet + ownerSignature),
                    result.blockNumber(), result.gasUsed(), true);
            return result.txHash();
        } catch (Exception e) {
            log.error("Blockchain initiateTransfer failed: {}", e.getMessage());
            throw new RuntimeException("Blockchain error: " + e.getMessage(), e);
        }
    }

    // ── Senior officer approve ────────────────────────────────────────────
    public String seniorOfficerApprove(BigInteger requestId,
                                        String signerAddress,
                                        String digitalSignature,
                                        Integer relatedParcelId,
                                        Integer relatedRequestId,
                                        Integer initiatedBy) {
        try {
            String data = FunctionEncoder.encode(new Function(
                    "seniorOfficerApprove",
                        List.of(new Uint256(requestId), new Address(signerAddress),
                            new DynamicBytes(hexToBytes(digitalSignature))),
                    List.of()));
            TransactionResult result = sendTransaction(data);
                saveAuditLog(result.txHash(), BlockchainAuditLog.EventType.TRANSFER_APPROVED,
                    relatedParcelId, relatedRequestId, initiatedBy,
                    sha256("L1_APPROVE_" + requestId),
                    result.blockNumber(), result.gasUsed(), true);
            return result.txHash();
        } catch (Exception e) {
            log.error("Blockchain L1 approve failed: {}", e.getMessage());
            throw new RuntimeException("Blockchain error: " + e.getMessage(), e);
        }
    }

    // ── Final authority approve ───────────────────────────────────────────
    public String levelTwoApprove(BigInteger requestId,
                                         String signerAddress,
                                         String digitalSignature,
                                         Integer relatedParcelId,
                                         Integer relatedRequestId,
                                         Integer initiatedBy) {
        try {
            String data = FunctionEncoder.encode(new Function(
                    "finalAuthorityApprove",
                        List.of(new Uint256(requestId), new Address(signerAddress),
                            new DynamicBytes(hexToBytes(digitalSignature))),
                    List.of()));
            TransactionResult result = sendTransaction(data);
                saveAuditLog(result.txHash(), BlockchainAuditLog.EventType.TRANSFER_APPROVED,
                    relatedParcelId, relatedRequestId, initiatedBy,
                    sha256("L2_APPROVE_" + requestId),
                    result.blockNumber(), result.gasUsed(), true);
            return result.txHash();
        } catch (Exception e) {
            log.error("Blockchain L2 approve failed: {}", e.getMessage());
            throw new RuntimeException("Blockchain error: " + e.getMessage(), e);
        }
    }

    // ── Reject transfer ───────────────────────────────────────────────────
    public String rejectTransfer(BigInteger requestId, String reason,
                                  Integer relatedParcelId, Integer relatedRequestId,
                                  Integer initiatedBy) {
        try {
            String data = FunctionEncoder.encode(new Function(
                    "rejectTransfer",
                    List.of(new Uint256(requestId), new Utf8String(reason)),
                    List.of()));
            TransactionResult result = sendTransaction(data);
                saveAuditLog(result.txHash(), BlockchainAuditLog.EventType.TRANSFER_REJECTED,
                    relatedParcelId, relatedRequestId, initiatedBy,
                    sha256("REJECT_" + requestId),
                    result.blockNumber(), result.gasUsed(), true);
            return result.txHash();
        } catch (Exception e) {
            log.error("Blockchain rejectTransfer failed: {}", e.getMessage());
            throw new RuntimeException("Blockchain error: " + e.getMessage(), e);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────
    private TransactionResult sendTransaction(String encodedFunction) throws Exception {
        try {
            EthSendTransaction ethSendTransaction = transactionManager().sendTransaction(
                    gasProvider.getGasPrice(),
                    gasProvider.getGasLimit(),
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO);

            if (ethSendTransaction.hasError()) {
                throw new RuntimeException("Blockchain transaction failed: " + ethSendTransaction.getError().getMessage());
            }

            String txHash = ethSendTransaction.getTransactionHash();
            TransactionReceipt receipt = waitForTransactionReceipt(txHash);
            Long blockNumber = receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValue() : null;
            Long gasUsed = receipt.getGasUsed() != null ? receipt.getGasUsed().longValue() : null;
            return new TransactionResult(txHash, blockNumber, gasUsed);
        } catch (Exception e) {
            log.error("Failed to send transaction to blockchain: {}", e.getMessage());
            throw new RuntimeException("Transaction submission failed", e);
        }
    }

    private TransactionReceipt waitForTransactionReceipt(String txHash) throws Exception {
        int attempts = 0;
        while (attempts < 10) {
            var receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            if (receiptResponse.hasError()) {
                throw new RuntimeException("Failed to fetch transaction receipt: " + receiptResponse.getError().getMessage());
            }
            Optional<TransactionReceipt> receipt = receiptResponse.getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.get();
            }
            Thread.sleep(Duration.ofMillis(500).toMillis());
            attempts++;
        }
        throw new RuntimeException("Transaction receipt unavailable for tx " + txHash);
    }

    @SuppressWarnings("rawtypes")
    private List<? extends Type> callContract(Function function) throws Exception {
        String encoded = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(
                credentials.getAddress(), contractAddress, encoded);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        if (response.isReverted() || response.hasError()) {
            throw new RuntimeException("Contract call reverted: " + response.getError());
        }
        return FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    }

    @SuppressWarnings("rawtypes")
    public boolean verifyParcelOnChain(String parcelNumber, String expectedHash) {
        try {
            Function function = new Function(
                    "getParcel",
                    List.of(new Utf8String(parcelNumber)),
                    List.of(
                            TypeReference.create(Utf8String.class),
                            TypeReference.create(Utf8String.class),
                            TypeReference.create(Utf8String.class),
                            TypeReference.create(Uint256.class),
                            TypeReference.create(Address.class),
                            TypeReference.create(Utf8String.class),
                            TypeReference.create(Uint256.class)
                    ));
            List<? extends Type> output = callContract(function);
            if (output.size() != 7 || expectedHash == null || expectedHash.isBlank()) {
                return false;
            }
            String onChainHash = ((Utf8String) output.get(5)).getValue();
            return expectedHash.trim().equals(onChainHash == null ? "" : onChainHash.trim());
        } catch (Exception e) {
            log.error("Blockchain verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetch basic transaction evidence for a given transaction hash from the connected node.
     * Returns a map suitable for JSON serialization with keys such as transactionHash,
     * blockHash, blockNumber, gasUsed, status and logs (list of simple maps).
     */
    public Map<String, Object> fetchTransactionEvidence(String txHash) {
        try {
            var receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            if (receiptResponse.hasError()) {
                log.warn("eth_getTransactionReceipt returned error for {}: {}", txHash, receiptResponse.getError().getMessage());
                return Map.of("available", false, "error", receiptResponse.getError().getMessage());
            }
            Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();
            if (receiptOpt.isEmpty()) {
                return Map.of("available", false, "message", "Receipt not yet available");
            }
            TransactionReceipt receipt = receiptOpt.get();
            List<Map<String, Object>> logs = new ArrayList<>();
            if (receipt.getLogs() != null) {
                for (var l : receipt.getLogs()) {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("address", l.getAddress());
                    logEntry.put("data", l.getData());
                    logEntry.put("topics", l.getTopics());
                    logs.add(logEntry);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("available", true);
            out.put("transactionHash", receipt.getTransactionHash());
            out.put("blockHash", receipt.getBlockHash());
            out.put("blockNumber", receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValue() : null);
            out.put("gasUsed", receipt.getGasUsed() != null ? receipt.getGasUsed().longValue() : null);
            out.put("status", receipt.getStatus());
            out.put("logs", logs);
            return out;
        } catch (Exception e) {
            log.warn("Failed to fetch transaction evidence for {}: {}", txHash, e.getMessage());
            return Map.of("available", false, "error", e.getMessage());
        }
    }

    @SuppressWarnings("rawtypes")
    public List<String> getOwnershipHistory(String parcelNumber) {
        try {
            Function function = new Function(
                    "getOwnershipHistory",
                    List.of(new Utf8String(parcelNumber)),
                    List.of(new TypeReference<DynamicArray<Utf8String>>() {}));
            List<? extends Type> output = callContract(function);
            if (output.isEmpty()) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            DynamicArray<Utf8String> array = (DynamicArray<Utf8String>) output.get(0);
            List<String> history = new ArrayList<>();
            for (Utf8String item : array.getValue()) {
                history.add(item.getValue());
            }
            return history;
        } catch (Exception e) {
            log.error("Failed to fetch ownership history: {}", e.getMessage());
            return List.of();
        }
    }

    private void saveAuditLog(String txHash, BlockchainAuditLog.EventType eventType,
                               Integer parcelId, Integer requestId,
                               Integer userId, String payloadHash,
                               Long blockNumber, Long gasUsed, boolean onChain) {
        try {
            auditLogRepository.save(BlockchainAuditLog.builder()
                    .transactionHash(txHash)
                    .onChain(onChain)
                    .contractAddress(contractAddress)
                    .eventType(eventType)
                    .relatedParcelId(parcelId)
                    .relatedRequestId(requestId)
                    .initiatedBy(userId)
                    .gasUsed(gasUsed)
                    .blockNumber(blockNumber)
                    .payloadHash(payloadHash)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save audit log for tx {}: {}", txHash, e.getMessage());
        }
    }

    public void recordOwnershipChange(String parcelNumber, Integer parcelId,
                                      Integer requestId, Integer initiatedBy) {
        try {
                String txHash = "OWNERSHIP_" + UUID.randomUUID();
                saveAuditLog(txHash, BlockchainAuditLog.EventType.OWNERSHIP_RECORDED,
                    parcelId, requestId, initiatedBy,
                    sha256(parcelNumber), null, null, false);
        } catch (Exception e) {
            log.warn("Failed to save ownership audit log for parcel {}: {}", parcelNumber, e.getMessage());
        }
    }

    public void recordVerificationEvent(String parcelNumber, Integer parcelId,
                                        Integer initiatedBy, boolean verified) {
        try {
                String txHash = "VERIFY_" + UUID.randomUUID();
                saveAuditLog(txHash, BlockchainAuditLog.EventType.RECORD_VERIFIED,
                    parcelId, null, initiatedBy,
                    sha256(parcelNumber + verified), null, null, false);
        } catch (Exception e) {
            log.warn("Failed to save verification audit log for parcel {}: {}", parcelNumber, e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] hexToBytes(String value) {
        if (value == null || !value.matches("^0x[0-9a-fA-F]+$") || ((value.length() - 2) % 2 != 0)) {
            throw new IllegalArgumentException("Digital signature must be a hexadecimal 0x-prefixed value");
        }
        byte[] result = new byte[(value.length() - 2) / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(2 + i * 2, 4 + i * 2), 16);
        }
        return result;
    }
}
