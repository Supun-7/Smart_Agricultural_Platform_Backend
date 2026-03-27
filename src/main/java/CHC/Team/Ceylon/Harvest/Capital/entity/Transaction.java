package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private float amount;

    @Column(nullable = false)
    private String type;

    @Column(name = "investor_id", nullable = false)
    private Long investorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ── Getters and Setters ─────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getInvestorId() { return investorId; }
    public void setInvestorId(Long investorId) { this.investorId = investorId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}