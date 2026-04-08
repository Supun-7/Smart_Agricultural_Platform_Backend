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

    /**
     * On-chain transaction hash from BlockchainService.createInvestmentContract().
     * Format: "0x" + 64 hex chars.
     * On MOCK profile: UUID-based fake hash.
     * On POLYGON_AMOY profile: real Amoy testnet tx hash.
     *
     * Gas is always paid by the CHC system wallet — no user wallet needed.
     * Viewable at: https://amoy.polygonscan.com/tx/{blockchainTxHash}
     */
    @Column(name = "blockchain_tx_hash", length = 66)
    private String blockchainTxHash;

    /**
     * On-chain contract reference for this investment.
     * On the data-tx pattern this equals blockchainTxHash.
     * Used by MilestoneServiceImpl to link milestone approval events back to this investment.
     */
    @Column(name = "contract_address", length = 66)
    private String contractAddress;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getInvestmentId() { return investmentId; }
    public void setInvestmentId(Long investmentId) { this.investmentId = investmentId; }

    public BigDecimal getAmountInvested() { return amountInvested; }
    public void setAmountInvested(BigDecimal amountInvested) { this.amountInvested = amountInvested; }

    public InvestmentStatus getStatus() { return status; }
    public void setStatus(InvestmentStatus status) { this.status = status; }

    public LocalDateTime getInvestmentDate() { return investmentDate; }
    public void setInvestmentDate(LocalDateTime investmentDate) { this.investmentDate = investmentDate; }

    public User getInvestor() { return investor; }
    public void setInvestor(User investor) { this.investor = investor; }

    public Land getLand() { return land; }
    public void setLand(Land land) { this.land = land; }

    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(String blockchainTxHash) { this.blockchainTxHash = blockchainTxHash; }

    public String getContractAddress() { return contractAddress; }
    public void setContractAddress(String contractAddress) { this.contractAddress = contractAddress; }
}
