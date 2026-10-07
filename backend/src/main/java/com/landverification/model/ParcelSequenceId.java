package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParcelSequenceId implements Serializable {

    @Column(name = "province_code", length = 10, nullable = false)
    private String provinceCode;

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;
}
