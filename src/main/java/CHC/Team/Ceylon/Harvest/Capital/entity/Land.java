package CHC.Team.Ceylon.Harvest.Capital.entity;

import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lands")
public class Land {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "land_id")
    private Long landId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "minimum_investment", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumInvestment;

    @Column(name = "progress_percentage", nullable = false)
    private Integer progressPercentage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "size_acres", precision = 19, scale = 2)
    private BigDecimal sizeAcres;

    @Column(name = "crop_type")
    private String cropType;

    @Column(name = "description")
    private String description;

    @Column(name = "image_urls")
    private String imageUrls;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_id")
    private User farmerUser;

    /**
     * Auditor review status for this project submission.
     * Starts as PENDING when the farmer registers a new land/project.
     * The auditor sets it to VERIFIED (project goes live) or REJECTED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    private VerificationStatus reviewStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.progressPercentage == null) {
            this.progressPercentage = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getLandId() {
        return landId;
    }

    public void setLandId(Long landId) {
        this.landId = landId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getMinimumInvestment() {
        return minimumInvestment;
    }

    public void setMinimumInvestment(BigDecimal minimumInvestment) {
        this.minimumInvestment = minimumInvestment;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public BigDecimal getSizeAcres() {
        return sizeAcres;
    }

    public void setSizeAcres(BigDecimal sizeAcres) {
        this.sizeAcres = sizeAcres;
    }

    public String getCropType() {
        return cropType;
    }

    public void setCropType(String cropType) {
        this.cropType = cropType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(String imageUrls) {
        this.imageUrls = imageUrls;
    }

    public User getFarmerUser() {
        return farmerUser;
    }

    public void setFarmerUser(User farmerUser) {
        this.farmerUser = farmerUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public VerificationStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(VerificationStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
}
