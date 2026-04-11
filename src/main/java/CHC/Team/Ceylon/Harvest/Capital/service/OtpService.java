package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.OtpVerification;
import CHC.Team.Ceylon.Harvest.Capital.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OtpService {

    private final OtpVerificationRepository otpRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${otp.expiry.minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.max.resend.count:3}")
    private int maxResendCount;

    @Value("${otp.resend.window.minutes:10}")
    private int resendWindowMinutes;

    public OtpService(OtpVerificationRepository otpRepo,
                      BCryptPasswordEncoder passwordEncoder,
                      EmailService emailService) {
        this.otpRepo         = otpRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService    = emailService;
    }

    /**
     * AC-1, AC-2, AC-6:
     * Generates a secure 6-digit OTP, hashes it, persists it, and emails it.
     * Any previous OTPs for this email are deleted first (one active OTP at a time).
     */
    public void generateAndSendOtp(String email) {
        // Remove all previous OTPs for this email
        otpRepo.deleteAllByEmail(email);

        // Generate a cryptographically secure 6-digit OTP
        String otp = generateSecureOtp();

        // AC-6: Store the BCrypt hash — never the raw OTP
        OtpVerification record = new OtpVerification();
        record.setEmail(email);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        otpRepo.save(record);

        // AC-1: Send the plain OTP to the user's email
        emailService.sendOtpEmail(email, otp);
    }

    /**
     * AC-3, AC-4:
     * Verifies the submitted OTP against the stored hash.
     * Returns true only if the OTP exists, is not expired, and is not already used.
     */
    public boolean verifyOtp(String email, String submittedOtp) {
        Optional<OtpVerification> optRecord =
            otpRepo.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email);

        if (optRecord.isEmpty()) {
            return false; // No OTP found for this email
        }

        OtpVerification record = optRecord.get();

        // AC-2: Check expiry
        if (LocalDateTime.now().isAfter(record.getExpiresAt())) {
            return false; // OTP has expired
        }

        // AC-3: Check hash match
        if (!passwordEncoder.matches(submittedOtp, record.getOtpHash())) {
            return false; // Wrong OTP
        }

        // Mark as verified so it cannot be reused
        record.setVerified(true);
        otpRepo.save(record);
        return true;
    }

    /**
     * AC-5: Resend OTP with rate limiting.
     * Allows up to maxResendCount resends within the resendWindowMinutes window.
     * Throws RateLimitExceededException if the limit is hit.
     */
    public void resendOtp(String email) {
        Optional<OtpVerification> optRecord =
            otpRepo.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email);

        if (optRecord.isPresent()) {
            OtpVerification record = optRecord.get();
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(resendWindowMinutes);

            // If the last OTP was created within the window, check resend count
            if (record.getCreatedAt().isAfter(windowStart)) {
                if (record.getResendCount() >= maxResendCount) {
                    throw new RateLimitExceededException(
                        "Too many OTP requests. Please wait before trying again.");
                }
                // Increment resend count on the existing record before deleting
                // (we track it on the new record below)
                int currentCount = record.getResendCount();
                // Delete old OTPs and issue a fresh one with incremented count
                otpRepo.deleteAllByEmail(email);

                String otp = generateSecureOtp();
                OtpVerification newRecord = new OtpVerification();
                newRecord.setEmail(email);
                newRecord.setOtpHash(passwordEncoder.encode(otp));
                newRecord.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
                newRecord.setResendCount(currentCount + 1);
                newRecord.setLastResentAt(LocalDateTime.now());
                otpRepo.save(newRecord);

                emailService.sendOtpEmail(email, otp);
                return;
            }
        }

        // No existing OTP in the window — treat as a fresh send
        generateAndSendOtp(email);
    }

    // Generates a cryptographically secure 6-digit OTP
    private String generateSecureOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // Always 6 digits
        return String.valueOf(otp);
    }

    // AC-5: Rate limit exception
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    // AC-4: OTP invalid or expired exception
    public static class InvalidOtpException extends RuntimeException {
        public InvalidOtpException(String message) {
            super(message);
        }
    }
}