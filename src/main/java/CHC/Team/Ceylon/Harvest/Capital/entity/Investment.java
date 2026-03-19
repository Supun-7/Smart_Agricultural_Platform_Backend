package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "investments")
public class Investment {

    public enum InvestmentStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "investment_id")
    private Long investmentId;

    @Column(name = "amount_invested")
    private BigDecimal amountInvested;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private InvestmentStatus status;

    @Column(name = "investment_date")
    private LocalDateTime investmentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id")
    private User investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "land_id")
    private Land land;

    public Long getInvestmentId() {
        return investmentId;
    }

    public void setInvestmentId(Long investmentId) {
        this.investmentId = investmentId;
    }

    public BigDecimal getAmountInvested() {
        return amountInvested;
    }

    public void setAmountInvested(BigDecimal amountInvested) {
        this.amountInvested = amountInvested;
    }

    public InvestmentStatus getStatus() {
        return status;
    }

    public void setStatus(InvestmentStatus status) {
        this.status = status;
    }

    public LocalDateTime getInvestmentDate() {
        return investmentDate;
    }

    public void setInvestmentDate(LocalDateTime investmentDate) {
        this.investmentDate = investmentDate;
    }

    public User getInvestor() {
        return investor;
    }

    public void setInvestor(User investor) {
        this.investor = investor;
    }

    public Land getLand() {
        return land;
    }

    public void setLand(Land land) {
        this.land = land;
    }
}
