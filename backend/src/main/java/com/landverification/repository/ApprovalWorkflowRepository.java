package com.landverification.repository;

import com.landverification.model.ApprovalWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Integer> {
    List<ApprovalWorkflow> findByRequest_RequestId(Integer requestId);
    List<ApprovalWorkflow> findByOfficer_UserId(Integer officerId);
}
