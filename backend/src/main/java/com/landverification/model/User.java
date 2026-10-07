package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    // Optional at creation — user completes profile from their own dashboard
    @Column(name = "full_name", nullable = true, length = 150)
    private String fullName;

    @Column(name = "national_id", nullable = true, unique = true, length = 100)
    private String nationalId;

    @Column(name = "phone_number", nullable = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "username", unique = true, length = 50)
    private String username;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "photo_path")
    private String photoPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = true, length = 10)
    private Gender gender;

    @Column(name = "invitation_code", unique = true)
    private String invitationCode;

    @Column(name = "code_used", nullable = false)
    @Builder.Default
    private Boolean codeUsed = false;

    @Column(name = "profile_complete", nullable = false)
    @Builder.Default
    private Boolean profileComplete = false;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.LAND_OWNER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        passwordChangedAt = passwordChangedAt != null ? passwordChangedAt : createdAt;
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Role {
        LAND_OWNER, LAND_OFFICER, SENIOR_OFFICER,
        SYSTEM_ADMIN, PUBLIC_VERIFIER
    }

    public enum Gender {
        MALE, FEMALE, OTHER
    }
}