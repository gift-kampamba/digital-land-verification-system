package com.landverification.repository;

import com.landverification.model.BlockchainAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainAuditLogRepository extends JpaRepository<BlockchainAuditLog, Integer> {
    Optional<BlockchainAuditLog> findByTransactionHash(String hash);
    List<BlockchainAuditLog> findAllByOrderByRecordedAtDesc();
    List<BlockchainAuditLog> findByRelatedParcelId(Integer parcelId);
    List<BlockchainAuditLog> findByEventType(BlockchainAuditLog.EventType type);
}
