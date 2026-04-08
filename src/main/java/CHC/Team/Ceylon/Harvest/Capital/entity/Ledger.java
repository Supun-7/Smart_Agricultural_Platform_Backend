package CHC.Team.Ceylon.Harvest.Capital.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AC-3 / AC-6 — Every deposit and withdrawal is permanently recorded here.
 * This table is append-only; records are never updated or deleted.
 */
@Entity
@Table(name = "ledger")
public class Ledger {

    public enum TransactionType { DEPOSIT, WITHDRAWAL, INVESTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ledgerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    /** Positive amount — meaning is determined by transactionType. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Wallet balance after this transaction was applied. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Gateway used: e.g. "MOCK" or "PAYHERE" (future). */
    @Column(nullable = false, length = 50)
    private String gateway;

    /** Reference ID returned by the gateway (or generated internally). */
    @Column(nullable = false, length = 100)
    private String gatewayReference;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public Ledger() {}

    public Ledger(Wallet wallet, TransactionType transactionType,
                  BigDecimal amount, BigDecimal balanceAfter,
                  String gateway, String gatewayReference) {
        this.wallet = wallet;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.gateway = gateway;
        this.gatewayReference = gatewayReference;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getLedgerId()                   { return ledgerId; }
    public Wallet getWallet()                   { return wallet; }
    public TransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount()               { return amount; }
    public BigDecimal getBalanceAfter()         { return balanceAfter; }
    public String getGateway()                  { return gateway; }
    public String getGatewayReference()         { return gatewayReference; }
    public LocalDateTime getCreatedAt()         { return createdAt; }

    public void setLedgerId(Long ledgerId)                             { this.ledgerId = ledgerId; }
    public void setWallet(Wallet wallet)                               { this.wallet = wallet; }
    public void setTransactionType(TransactionType transactionType)   { this.transactionType = transactionType; }
    public void setAmount(BigDecimal amount)                           { this.amount = amount; }
    public void setBalanceAfter(BigDecimal balanceAfter)               { this.balanceAfter = balanceAfter; }
    public void setGateway(String gateway)                             { this.gateway = gateway; }
    public void setGatewayReference(String gatewayReference)           { this.gatewayReference = gatewayReference; }
    public void setCreatedAt(LocalDateTime createdAt)                  { this.createdAt = createdAt; }
}
