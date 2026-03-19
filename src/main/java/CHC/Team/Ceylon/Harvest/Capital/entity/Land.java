package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lands")
public class Land {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long landId;

    @Column(nullable = false)
    private String projectName;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumInvestment;

    @Column(nullable = false)
    private Integer progressPercentage;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
        this.progressPercentage = 0;
    }

    public Land() {
    }

    public Land(Long landId, String projectName, String location,
            BigDecimal totalValue, BigDecimal minimumInvestment,
            Integer progressPercentage, Boolean isActive, LocalDateTime createdAt) {
        this.landId = landId;
        this.projectName = projectName;
        this.location = location;
        this.totalValue = totalValue;
        this.minimumInvestment = minimumInvestment;
        this.progressPercentage = progressPercentage;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public Long getLandId() {
        return landId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getLocation() {
        return location;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getMinimumInvestment() {
        return minimumInvestment;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setLandId(Long landId) {
        this.landId = landId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public void setMinimumInvestment(BigDecimal minimumInvestment) {
        this.minimumInvestment = minimumInvestment;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}