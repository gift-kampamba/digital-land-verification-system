package com.landverification.repository;

import com.landverification.model.LandOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandOwnershipRepository extends JpaRepository<LandOwnership, Integer> {
    List<LandOwnership> findByParcel_ParcelIdOrderByCreatedAtAsc(Integer parcelId);
    Optional<LandOwnership> findByParcel_ParcelIdAndIsCurrentTrue(Integer parcelId);
    List<LandOwnership> findByOwner_UserId(Integer userId);

    @Query("SELECT o FROM LandOwnership o WHERE o.owner.userId = :userId AND o.isCurrent = true")
    List<LandOwnership> findCurrentByOwnerUserId(@Param("userId") Integer userId);

    List<LandOwnership> findByOwner_EmailAndIsCurrentTrue(String email);
    List<LandOwnership> findByOwner_NationalIdAndIsCurrentTrue(String nationalId);

    List<LandOwnership> findByOwner_FullNameContainingIgnoreCaseAndIsCurrentTrue(String fullName);
}
