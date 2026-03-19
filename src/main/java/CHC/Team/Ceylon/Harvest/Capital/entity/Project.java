package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "current_amount")
    private Double currentAmount;

    @Column(name = "progress")
    private Double progress;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "target_amount")
    private Double targetAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_user_id")
    private User farmerUser;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(Double currentAmount) {
        this.currentAmount = currentAmount;
    }

    public Double getProgress() {
        return progress;
    }

    public void setProgress(Double progress) {
        this.progress = progress;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Double getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(Double targetAmount) {
        this.targetAmount = targetAmount;
    }

    public User getFarmerUser() {
        return farmerUser;
    }

    public void setFarmerUser(User farmerUser) {
        this.farmerUser = farmerUser;
    }
}
