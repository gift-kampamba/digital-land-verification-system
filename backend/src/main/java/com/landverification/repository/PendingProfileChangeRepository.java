package com.landverification.repository;

import com.landverification.model.PendingProfileChange;
import com.landverification.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingProfileChangeRepository extends JpaRepository<PendingProfileChange, Integer> {
    List<PendingProfileChange> findByStatus(PendingProfileChange.Status status);
    List<PendingProfileChange> findByUser(User user);
    List<PendingProfileChange> findByUserAndStatus(User user, PendingProfileChange.Status status);
    
}