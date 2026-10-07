package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parcel_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelSequence {

    @EmbeddedId
    private ParcelSequenceId id;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;
}
