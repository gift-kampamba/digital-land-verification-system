package com.landverification.repository;

import com.landverification.model.ParcelSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParcelSequenceRepository extends JpaRepository<ParcelSequence, com.landverification.model.ParcelSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ParcelSequence s WHERE s.id.provinceCode = :provinceCode AND s.id.sequenceYear = :sequenceYear")
    Optional<ParcelSequence> findByProvinceCodeAndSequenceYearForUpdate(@Param("provinceCode") String provinceCode,
                                                                       @Param("sequenceYear") int sequenceYear);
}
