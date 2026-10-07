// TransferRequestRepository.java - Add these methods
package com.landverification.repository;

import com.landverification.model.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Integer> {
    
    List<TransferRequest> findByStatus(TransferRequest.TransferStatus status);
    
    List<TransferRequest> findBySeller_UserId(Integer userId);
    
    List<TransferRequest> findByBuyer_UserId(Integer userId);

    List<TransferRequest> findByParcel_ParcelIdOrderBySubmittedAtDesc(Integer parcelId);
    
    // New methods for dashboard
    List<TransferRequest> findByStatusIn(List<TransferRequest.TransferStatus> statuses);
    
    long countByStatusIn(List<TransferRequest.TransferStatus> statuses);
    
    long countByStatus(TransferRequest.TransferStatus status);

    List<TransferRequest> findByFlaggedTrue();
    
    List<TransferRequest> findByFlaggedTrueOrStatusIn(List<TransferRequest.TransferStatus> statuses);
    
    @Query("SELECT COUNT(t) FROM TransferRequest t WHERE t.status = :status AND t.updatedAt >= :date")
    long countByStatusAndUpdatedAtAfter(@Param("status") TransferRequest.TransferStatus status, 
                                         @Param("date") LocalDateTime date);

    @Query("SELECT t FROM TransferRequest t JOIN FETCH t.parcel WHERE t.status = :status")
    List<TransferRequest> findByStatusWithParcel(@Param("status") TransferRequest.TransferStatus status);
}