package com.landverification.config;

import com.landverification.model.User;
import com.landverification.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        ensureTransferBuyerColumnIsNullable();
        ensureTransferStatusSupportsFlagged();
        ensureDefaultSystemAdmin();
    }

    private void ensureDefaultSystemAdmin() {
        User admin = userRepository.findByEmail("admin@gov.zm")
                .orElseGet(() -> User.builder()
                        .fullName("System Admin")
                        .nationalId("000000/00/0")
                        .email("admin@gov.zm")
                        .role(User.Role.SYSTEM_ADMIN)
                        .isActive(true)
                        .build());
        admin.setPasswordHash(passwordEncoder.encode("gift123"));
        admin.setRole(User.Role.SYSTEM_ADMIN);
        admin.setIsActive(true);
        userRepository.save(admin);
        log.info("Default system admin account ready for {}", admin.getEmail());
    }

    private void ensureTransferBuyerColumnIsNullable() {
        try {
            jdbcTemplate.execute("ALTER TABLE transfer_request MODIFY buyer_user_id INT NULL");
            log.info("Updated transfer_request.buyer_user_id to allow null values");
        } catch (Exception ex) {
            log.warn("Transfer buyer column migration skipped: {}", ex.getMessage());
        }
    }

    private void ensureTransferStatusSupportsFlagged() {
        try {
            jdbcTemplate.execute("ALTER TABLE transfer_request MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED'");
            log.info("Updated transfer_request.status to support all transfer statuses");
        } catch (Exception ex) {
            log.warn("Transfer status column migration skipped: {}", ex.getMessage());
        }
    }
}