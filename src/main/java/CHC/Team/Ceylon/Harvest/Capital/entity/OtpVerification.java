package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    // AC-6: OTP is stored as a BCrypt hash — never plain text
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // AC-3: Marks this OTP as consumed after successful verification
    @Column(nullable = false)
    private boolean verified = false;

    // AC-5: Tracks how many times the user has re-requested OTP
    @Column(name = "resend_count", nullable = false)
    private int resendCount = 0;

    @Column(name = "last_resent_at")
    private LocalDateTime lastResentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public int getResendCount() { return resendCount; }
    public void setResendCount(int resendCount) { this.resendCount = resendCount; }

    public LocalDateTime getLastResentAt() { return lastResentAt; }
    public void setLastResentAt(LocalDateTime lastResentAt) { this.lastResentAt = lastResentAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}