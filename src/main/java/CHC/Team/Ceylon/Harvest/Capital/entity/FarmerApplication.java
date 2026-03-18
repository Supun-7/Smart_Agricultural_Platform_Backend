package CHC.Team.Ceylon.Harvest.Capital.entity;

import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "farmer_applications")
public class FarmerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ── Personal details ──────────────────────────────────────
    @Column(name = "farmer_name")
    private String farmerName;

    @Column(name = "surname")
    private String surname;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "address")
    private String address;

    @Column(name = "farm_address")
    private String farmAddress;

    @Column(name = "year_started")
    private Integer yearStarted;

    // ── Farm details (existing + enhanced) ───────────────────
    @Column(name = "nic_number")
    private String nicNumber;

    @Column(name = "farm_location")
    private String farmLocation;

    @Column(name = "land_size_acres")
    private BigDecimal landSizeAcres;

    @Column(name = "crop_types")
    private String cropTypes;

    @Column(name = "land_measurements")
    private String landMeasurements;    // text description of measurements

    // ── Document URLs ─────────────────────────────────────────
    @Column(name = "document_url")
    private String documentUrl;         // kept for backward compatibility

    @Column(name = "nic_front_url")
    private String nicFrontUrl;

    @Column(name = "nic_back_url")
    private String nicBackUrl;

    // Stored as comma-separated URLs
    // e.g. "https://...photo1.jpg,https://...photo2.jpg"
    @Column(name = "land_photo_urls")
    private String landPhotoUrls;

    // ── Status ────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    // ── Getters and Setters ───────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFarmerName() { return farmerName; }
    public void setFarmerName(String farmerName) { this.farmerName = farmerName; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getFarmAddress() { return farmAddress; }
    public void setFarmAddress(String farmAddress) { this.farmAddress = farmAddress; }

    public Integer getYearStarted() { return yearStarted; }
    public void setYearStarted(Integer yearStarted) { this.yearStarted = yearStarted; }

    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }

    public String getFarmLocation() { return farmLocation; }
    public void setFarmLocation(String farmLocation) { this.farmLocation = farmLocation; }

    public BigDecimal getLandSizeAcres() { return landSizeAcres; }
    public void setLandSizeAcres(BigDecimal landSizeAcres) { this.landSizeAcres = landSizeAcres; }

    public String getCropTypes() { return cropTypes; }
    public void setCropTypes(String cropTypes) { this.cropTypes = cropTypes; }

    public String getLandMeasurements() { return landMeasurements; }
    public void setLandMeasurements(String landMeasurements) { this.landMeasurements = landMeasurements; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getNicFrontUrl() { return nicFrontUrl; }
    public void setNicFrontUrl(String nicFrontUrl) { this.nicFrontUrl = nicFrontUrl; }

    public String getNicBackUrl() { return nicBackUrl; }
    public void setNicBackUrl(String nicBackUrl) { this.nicBackUrl = nicBackUrl; }

    public String getLandPhotoUrls() { return landPhotoUrls; }
    public void setLandPhotoUrls(String landPhotoUrls) { this.landPhotoUrls = landPhotoUrls; }

    public VerificationStatus getStatus() { return status; }
    public void setStatus(VerificationStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
}