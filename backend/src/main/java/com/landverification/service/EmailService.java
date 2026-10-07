package com.landverification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    // REMOVED: private final ApplicationContext applicationContext; - This causes circular dependency

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.mail.from-name:Land Verification System}")
    private String fromName;

    @Retryable(
        retryFor = {MailException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        try {
            log.info("Attempting to send email to {} using sender {}", to, fromEmail);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            return "Email sent successfully to: " + to;
        } catch (MailException e) {
            log.error("Failed to send email to {}. SMTP configuration or credentials may be invalid. Root cause: {}", to, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to prepare or send email to {}. Root cause: {}", to, e.getMessage(), e);
            throw new MailException("Unable to send email", e) {};
        }
    }

    @Async
    public void sendEmailAsync(String to, String subject, String body) {
        try {
            sendEmail(to, subject, body);
        } catch (Exception e) {
            log.warn("Asynchronous email delivery failed for {}: {}", to, e.getMessage());
        }
    }

    public String sendOfficerInvitation(String email, String invitationCode) {
        String subject = "Land Verification System - Officer Invitation";
        String body = String.format(
            "Dear Future Land Officer,\n\n" +
            "You have been officially invited to join the Land Verification System as a Land Officer.\n\n" +
            "Your unique invitation code is: %s\n\n" +
            "To complete your registration, please follow these steps:\n" +
            "1. Visit: http://localhost:8080/officer-register.html\n" +
            "2. Enter your invitation code: %s\n" +
            "3. Provide your personal details (full name, national ID, phone number)\n" +
            "4. Create a secure password\n" +
            "5. Complete your profile with photo and address\n\n" +
            "This invitation is valid and will allow you to access the official Land Verification System.\n\n" +
            "If you have any questions, please contact the System Administrator.\n\n" +
            "Best regards,\n" +
            "Land Verification System Administration\n" +
            "Republic of Zambia\n" +
            "Email: admin@gov.zm",
            invitationCode, invitationCode
        );

        try {
            // Direct call - no proxy needed
            String result = sendEmail(email, subject, body);
            log.info("Officer invitation sent successfully to: {} ({})", email, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to send officer invitation to {}: {}", email, e.getMessage());
            throw new RuntimeException("Unable to send invitation email. Please check email configuration and try again.");
        }
    }

    public String sendOfficerCredentials(String email, String username, String tempPassword, String fullName) {
        String subject = "Land Verification System - Officer Account Created";
        String body = String.format(
            "Dear %s,\n\n" +
            "Your Land Officer account has been successfully created in the Land Verification System.\n\n" +
            "Account Details:\n" +
            "Username: %s\n" +
            "Temporary Password: %s\n\n" +
            "Please log in using these credentials and change your password immediately.\n\n" +
            "Login URL: http://localhost:8080/login.html\n\n" +
            "If you have any questions, please contact the System Administrator.\n\n" +
            "Best regards,\n" +
            "Land Verification System Administration\n" +
            "Republic of Zambia\n" +
            "Email: admin@gov.zm",
            fullName, username, tempPassword
        );

        try {
            // Direct call - no proxy needed
            String result = sendEmail(email, subject, body);
            log.info("Officer credentials sent successfully to: {} ({})", email, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to send officer credentials to {}: {}", email, e.getMessage());
            throw new RuntimeException("Unable to send credentials email. Please check email configuration and try again.");
        }
    }

    public String sendFlaggedParcelNotification(String to, String recipientName, String parcelNumber,
                                               String reason, String parcelLocation, String nextSteps) {
        String subject = "Land Verification System — Parcel Flagged for Review";
        StringBuilder body = new StringBuilder();
        body.append(String.format("Dear %s,\n\n", recipientName != null && !recipientName.isBlank() ? recipientName : "User"));
        body.append(String.format("The parcel %s has been flagged for review in the Land Verification System.\n\n", parcelNumber != null ? parcelNumber : "N/A"));
        body.append(String.format("Reason for flagging:\n- %s\n\n", reason != null && !reason.isBlank() ? reason : "No reason provided."));
        body.append("Actions required to resolve the issue:\n");
        body.append("- Review the flagged issue carefully.\n");
        body.append("- Provide any missing documents or clarification requested by the reviewing officer.\n");
        body.append("- Correct the issue before the parcel can continue through the approval workflow.\n\n");
        body.append("Next steps:\n");
        body.append("- Contact the responsible land officer for guidance if needed.\n");
        body.append("- Address the issue and submit the parcel again for verification.\n");
        body.append("- The parcel will remain flagged until the issue is resolved and verified by the appropriate authority.\n\n");
        if (parcelLocation != null && !parcelLocation.isBlank()) {
            body.append(String.format("Parcel location: %s\n", parcelLocation));
        }
        body.append("\nPlease log in to the system to view the latest status and next actions.\n\n");
        body.append("Best regards,\nLand Verification System Administration\nRepublic of Zambia\nEmail: admin@gov.zm");

        try {
            String result = sendEmail(to, subject, body.toString());
            log.info("Flagged parcel notification sent successfully to: {} ({})", to, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to send flagged parcel notification to {}: {}", to, e.getMessage());
            throw new RuntimeException("Unable to send flagged parcel notification email. Please check email configuration and try again.");
        }
    }

    public String sendTransferRejectedNotification(String to, String recipientName, String parcelNumber,
                                                   String reason, String parcelLocation) {
        String subject = "Land Verification System — Transfer Request Rejected";
        String body = String.format(
                "Dear %s,\n\n" +
                "Your transfer request for parcel %s has been rejected by the reviewing authority.\n\n" +
                "Reason for rejection:\n- %s\n\n" +
                "Parcel location: %s\n\n" +
                "Please contact the responsible land officer if you need clarification or wish to correct and resubmit the request.\n\n" +
                "Best regards,\nLand Verification System Administration\nRepublic of Zambia\nEmail: admin@gov.zm",
                recipientName != null && !recipientName.isBlank() ? recipientName : "Landowner",
                parcelNumber != null ? parcelNumber : "N/A",
                reason != null && !reason.isBlank() ? reason : "No reason provided.",
                parcelLocation != null && !parcelLocation.isBlank() ? parcelLocation : "N/A");

        try {
            String result = sendEmail(to, subject, body);
            log.info("Transfer rejection email sent successfully to: {} ({})", to, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to send transfer rejection email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Unable to send transfer rejection email. Please check email configuration and try again.");
        }
    }

    public String sendLandownerCredentials(String email, String username, String tempPassword,
                                         String fullName, String parcelNumber,
                                         String locationAddress, java.math.BigDecimal areaSqm,
                                         String ownershipType, String deedUrl) {
        String subject = "Land Verification System — Your Landowner Login Details";
        StringBuilder body = new StringBuilder();
        body.append(String.format("Dear %s,\n\n", fullName != null && !fullName.isBlank() ? fullName : "Landowner"));
        body.append("Your land parcel has been registered successfully in the Land Verification System.\n\n");
        body.append("Use the account details below to sign in and view your parcel information:\n\n");
        body.append(String.format("Username: %s\n", username));
        body.append(String.format("Temporary password: %s\n\n", tempPassword));
        body.append("Login URL: http://localhost:8080/login.html\n\n");
        body.append("Parcel details:\n");
        body.append(String.format("- Parcel Number: %s\n", parcelNumber != null ? parcelNumber : "N/A"));
        body.append(String.format("- Location: %s\n", locationAddress != null ? locationAddress : "N/A"));
        body.append(String.format("- Land size: %s sqm\n", areaSqm != null ? areaSqm.toPlainString() : "N/A"));
        body.append(String.format("- Ownership type: %s\n\n", ownershipType != null ? ownershipType : "N/A"));
        if (deedUrl != null) {
            body.append(String.format("Your title deed is available here: %s\n\n", deedUrl));
        }
        body.append("Important: After signing in, please change your password immediately to keep your account secure.\n");
        body.append("If you do not recognize this message or if you need help, contact the system administrator.\n\n");
        body.append("Best regards,\nLand Verification System Administration\nRepublic of Zambia\nEmail: admin@gov.zm");

        try {
            String result = sendEmail(email, subject, body.toString());
            log.info("Landowner credentials sent successfully to: {} ({})", email, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to send landowner credentials to {}: {}", email, e.getMessage());
            throw new RuntimeException("Unable to send landowner credential email. Please check email configuration and try again.");
        }
    }

    @Async
    public void sendLandownerCredentialsAsync(String email, String username, String tempPassword,
                                               String fullName, String parcelNumber, String location,
                                               java.math.BigDecimal areaSqm, String ownershipType,
                                               String deedUrl) {
        try {
            sendLandownerCredentials(email, username, tempPassword, fullName, parcelNumber,
                    location, areaSqm, ownershipType, deedUrl);
        } catch (Exception e) {
            log.warn("Asynchronous landowner email delivery failed for {}: {}", email, e.getMessage());
        }
    }
}