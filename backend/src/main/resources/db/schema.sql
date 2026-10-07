-- ============================================================
-- Blockchain-Based Digital Land Verification System
-- Student: Gift Kampamba | CBU 22103754
-- Run this file once in MySQL Workbench before starting backend
-- ============================================================

CREATE DATABASE IF NOT EXISTS land_verification;
USE land_verification;

-- TABLE 1: users
CREATE TABLE IF NOT EXISTS users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(150) NOT NULL,
    national_id   VARCHAR(100)  NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone_number  VARCHAR(20)  NOT NULL,
    address       TEXT,
    photo_path    VARCHAR(255),
    gender        ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT NULL,
    invitation_code VARCHAR(255) UNIQUE,
    code_used     BOOLEAN NOT NULL DEFAULT FALSE,
    profile_complete BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM(
        'LAND_OWNER',
        'LAND_OFFICER',
        'SENIOR_OFFICER',
        'SYSTEM_ADMIN',
        'PUBLIC_VERIFIER'
    ) NOT NULL DEFAULT 'LAND_OWNER',
    is_active  BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- TABLE 2: land_parcel
CREATE TABLE IF NOT EXISTS land_parcel (
    parcel_id         INT AUTO_INCREMENT PRIMARY KEY,
    parcel_number     VARCHAR(50)   NOT NULL UNIQUE,
    title_deed_number VARCHAR(50)   UNIQUE,
    province          VARCHAR(100)  NOT NULL,
    district          VARCHAR(100)  NOT NULL,
    location_address  VARCHAR(255)  NOT NULL,
    gps_lat           VARCHAR(50)   NULL,
    gps_lng           VARCHAR(50)   NULL,
    area_sqm          DECIMAL(15,2) NOT NULL,
    land_use ENUM(
        'RESIDENTIAL', 'COMMERCIAL', 'AGRICULTURAL', 'INDUSTRIAL', 'MIXED_USE'
    ) NOT NULL,
    status ENUM(
        'ACTIVE', 'PENDING_TRANSFER', 'DISPUTED', 'ARCHIVED'
    ) NOT NULL DEFAULT 'ACTIVE',
    blockchain_hash VARCHAR(255) UNIQUE,
    document_hash VARCHAR(128),
    title_deed_file VARCHAR(255) UNIQUE,
    owner_photo_path VARCHAR(255),
    registered_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS parcel_sequence (
    province_code VARCHAR(10) NOT NULL,
    sequence_year INT NOT NULL,
    last_sequence INT NOT NULL DEFAULT 0,
    PRIMARY KEY (province_code, sequence_year)
);

-- TABLE 3: land_ownership
CREATE TABLE IF NOT EXISTS land_ownership (
    ownership_id     INT AUTO_INCREMENT PRIMARY KEY,
    parcel_id        INT NOT NULL,
    owner_user_id    INT NOT NULL,
    ownership_type ENUM(
        'SOLE', 'JOINT', 'LEASEHOLD', 'FREEHOLD'
    ) NOT NULL DEFAULT 'SOLE',
    acquisition_method ENUM(
        'ORIGINAL_REGISTRATION', 'PURCHASE', 'INHERITANCE', 'GIFT', 'COURT_ORDER'
    ) NOT NULL,
    start_date      DATE    NOT NULL,
    end_date        DATE    NULL,
    is_current      BOOLEAN NOT NULL DEFAULT TRUE,
    blockchain_hash VARCHAR(255) UNIQUE,
    owner_signature TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ownership_parcel FOREIGN KEY (parcel_id)     REFERENCES land_parcel(parcel_id),
    CONSTRAINT fk_ownership_user   FOREIGN KEY (owner_user_id) REFERENCES users(user_id)
);

-- TABLE 4: transfer_request
CREATE TABLE IF NOT EXISTS transfer_request (
    request_id         INT AUTO_INCREMENT PRIMARY KEY,
    parcel_id          INT NOT NULL,
    seller_user_id     INT NOT NULL,
    buyer_user_id      INT NULL,
    buyer_full_name    VARCHAR(255),
    buyer_national_id  VARCHAR(100),
    buyer_email        VARCHAR(150),
    buyer_phone_number VARCHAR(20),
    buyer_address      TEXT,
    buyer_nationality  VARCHAR(50),
    buyer_id_type      VARCHAR(50),
    transfer_date      VARCHAR(50),
    payment_terms      VARCHAR(255),
    transfer_reason    VARCHAR(255),
    agreed_price_zmw   DECIMAL(15,2),
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    supporting_docs       TEXT,
    owner_signature       TEXT,
    buyer_signature       TEXT,
    seller_photo_path     VARCHAR(255),
    buyer_photo_path      VARCHAR(255),
    blockchain_tx_hash    VARCHAR(255) UNIQUE,
    flagged               BOOLEAN NOT NULL DEFAULT FALSE,
    flag_reason           TEXT,
    flagged_at            DATETIME,
    flagged_by_user_id    INT NULL,
    submitted_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_transfer_parcel FOREIGN KEY (parcel_id)      REFERENCES land_parcel(parcel_id),
    CONSTRAINT fk_transfer_seller FOREIGN KEY (seller_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_transfer_buyer  FOREIGN KEY (buyer_user_id)  REFERENCES users(user_id),
    CONSTRAINT fk_transfer_flagged_by FOREIGN KEY (flagged_by_user_id) REFERENCES users(user_id)
);

ALTER TABLE transfer_request MODIFY buyer_user_id INT NULL;

ALTER TABLE transfer_request MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED';

-- TABLE 5: approval_workflow
CREATE TABLE IF NOT EXISTS approval_workflow (
    approval_id     INT AUTO_INCREMENT PRIMARY KEY,
    request_id      INT     NOT NULL,
    officer_user_id INT     NOT NULL,
    approval_level  TINYINT NOT NULL,
    action ENUM(
        'APPROVED', 'REJECTED', 'RETURNED_FOR_CORRECTION'
    ) NOT NULL,
    comments          TEXT,
    digital_signature VARCHAR(512),
    actioned_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_approval_request FOREIGN KEY (request_id)      REFERENCES transfer_request(request_id),
    CONSTRAINT fk_approval_officer FOREIGN KEY (officer_user_id) REFERENCES users(user_id)
);

-- TABLE 6: blockchain_audit_log
CREATE TABLE IF NOT EXISTS blockchain_audit_log (
    log_id           INT AUTO_INCREMENT PRIMARY KEY,
    transaction_hash VARCHAR(255) NOT NULL UNIQUE,
    block_number     BIGINT,
    contract_address VARCHAR(255),
    event_type ENUM(
        'PARCEL_REGISTERED', 'OWNERSHIP_RECORDED', 'TRANSFER_INITIATED',
        'TRANSFER_APPROVED', 'TRANSFER_REJECTED', 'RECORD_VERIFIED'
    ) NOT NULL,
    related_parcel_id  INT  NULL,
    related_request_id INT  NULL,
    initiated_by       INT  NULL,
    gas_used           BIGINT,
    payload_hash       VARCHAR(255),
    recorded_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_parcel  FOREIGN KEY (related_parcel_id)  REFERENCES land_parcel(parcel_id),
    CONSTRAINT fk_audit_request FOREIGN KEY (related_request_id) REFERENCES transfer_request(request_id),
    CONSTRAINT fk_audit_user    FOREIGN KEY (initiated_by)        REFERENCES users(user_id)
);

-- TABLE 7: documents
CREATE TABLE IF NOT EXISTS documents (
    document_id   INT AUTO_INCREMENT PRIMARY KEY,
    related_type  ENUM('PARCEL', 'TRANSFER_REQUEST') NOT NULL,
    related_id    INT NOT NULL,
    document_type ENUM(
        'TITLE_DEED', 'SURVEY_PLAN', 'ID_COPY',
        'PROOF_OF_PAYMENT', 'CONSENT_FORM', 'COURT_ORDER', 'OTHER'
    ) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_hash   VARCHAR(255),
    uploaded_by INT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_uploader FOREIGN KEY (uploaded_by) REFERENCES users(user_id)
);

-- TABLE 8: verification_requests
CREATE TABLE IF NOT EXISTS verification_requests (
    verification_id   INT AUTO_INCREMENT PRIMARY KEY,
    parcel_number     VARCHAR(50) NOT NULL,
    requested_by      INT NULL,
    verification_type ENUM(
        'OWNERSHIP_CHECK', 'HISTORY_CHECK', 'BLOCKCHAIN_INTEGRITY_CHECK'
    ) NOT NULL DEFAULT 'OWNERSHIP_CHECK',
    result ENUM(
        'VERIFIED', 'MISMATCH_DETECTED', 'PARCEL_NOT_FOUND', 'PENDING'
    ) NOT NULL DEFAULT 'PENDING',
    result_details TEXT,
    verified_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verification_user FOREIGN KEY (requested_by) REFERENCES users(user_id)
);

-- TABLE 9: notification
CREATE TABLE IF NOT EXISTS notification (
    notification_id   INT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id INT  NOT NULL,
    message           TEXT NOT NULL,
    notification_type ENUM(
        'TRANSFER_INITIATED', 'TRANSFER_APPROVED_L1', 'TRANSFER_APPROVED_L2',
        'TRANSFER_APPROVED_FINAL', 'TRANSFER_REJECTED',
        'REGISTRATION_COMPLETE', 'VERIFICATION_COMPLETE'
    ) NOT NULL,
    related_parcel_id  INT     NULL,
    related_request_id INT     NULL,
    is_read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user    FOREIGN KEY (recipient_user_id)  REFERENCES users(user_id),
    CONSTRAINT fk_notification_parcel  FOREIGN KEY (related_parcel_id)  REFERENCES land_parcel(parcel_id),
    CONSTRAINT fk_notification_request FOREIGN KEY (related_request_id) REFERENCES transfer_request(request_id)
);

-- TABLE 10: qr_code
CREATE TABLE IF NOT EXISTS qr_code (
    qr_id        INT AUTO_INCREMENT PRIMARY KEY,
    parcel_id    INT          NOT NULL UNIQUE,
    qr_data      VARCHAR(500) NOT NULL,
    generated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_valid     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_qr_parcel FOREIGN KEY (parcel_id) REFERENCES land_parcel(parcel_id)
);

-- TABLE 11: land_office
CREATE TABLE IF NOT EXISTS land_office (
    office_id      INT AUTO_INCREMENT PRIMARY KEY,
    office_name    VARCHAR(150) NOT NULL UNIQUE,
    office_code    VARCHAR(20)  NOT NULL UNIQUE,
    province       VARCHAR(100) NOT NULL,
    district       VARCHAR(100) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    phone_number   VARCHAR(20)  NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE,
    office_manager_id INT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_office_manager FOREIGN KEY (office_manager_id) REFERENCES users(user_id)
);

-- TABLE 12: pending_profile_changes
CREATE TABLE IF NOT EXISTS pending_profile_changes (
    change_id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    full_name       VARCHAR(150),
    phone_number    VARCHAR(20),
    address         TEXT,
    gender          ENUM('MALE', 'FEMALE', 'OTHER') DEFAULT NULL,
    photo_path      VARCHAR(255),
    password_hash   VARCHAR(255),
    change_type ENUM(
        'PROFILE_UPDATE', 'PASSWORD_CHANGE', 'PHOTO_UPDATE'
    ) NOT NULL,
    status ENUM(
        'PENDING', 'APPROVED', 'REJECTED'
    ) NOT NULL DEFAULT 'PENDING',
    admin_comments  TEXT,
    requested_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at     DATETIME NULL,
    approved_by     INT NULL,
    CONSTRAINT fk_profile_change_user     FOREIGN KEY (user_id)     REFERENCES users(user_id),
    CONSTRAINT fk_profile_change_approver FOREIGN KEY (approved_by) REFERENCES users(user_id)
);

-- Update notification table to include new types and related_user_id
ALTER TABLE notification ADD COLUMN related_user_id INT NULL;
ALTER TABLE notification ADD CONSTRAINT fk_notification_related_user FOREIGN KEY (related_user_id) REFERENCES users(user_id);
ALTER TABLE notification MODIFY COLUMN notification_type ENUM(
    'TRANSFER_INITIATED', 'TRANSFER_APPROVED_L1', 'TRANSFER_APPROVED_L2',
    'TRANSFER_APPROVED_FINAL', 'TRANSFER_REJECTED',
    'REGISTRATION_COMPLETE', 'VERIFICATION_COMPLETE',
    'PROFILE_CHANGE_REQUESTED', 'PROFILE_CHANGE_APPROVED', 'PROFILE_CHANGE_REJECTED'
) NOT NULL;

