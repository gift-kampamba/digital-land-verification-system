package com.landverification.repository;

import com.landverification.model.LandParcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandParcelRepository extends JpaRepository<LandParcel, Integer> {
    Optional<LandParcel> findByParcelNumber(String parcelNumber);
    boolean existsByParcelNumber(String parcelNumber);
    List<LandParcel> findByStatus(LandParcel.ParcelStatus status);

    @Query("SELECT p FROM LandParcel p WHERE " +
           "LOWER(p.parcelNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.titleDeedNumber) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.district) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.province) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.locationAddress) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<LandParcel> search(@Param("q") String query);

    @Query(value = "SELECT MAX(CAST(SUBSTRING_INDEX(parcel_number, '-', -1) AS UNSIGNED)) " +
           "FROM land_parcel WHERE parcel_number LIKE CONCAT('ZM-', :provinceCode, '-', :year, '-%')",
           nativeQuery = true)
    Long getMaxParcelSequenceNumberByProvinceAndYear(@Param("provinceCode") String provinceCode,
                                                     @Param("year") String year);
}

