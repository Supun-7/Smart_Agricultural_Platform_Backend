package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for the {@code ledger} table.
 *
 * <p>
 * Column mapping (schema → entity field):
 * 
 * <pre>
 *   ledger_id          → ledgerId          (PK, BIGSERIAL)
 *   wallet_id          → wallet.walletId   (FK → wallets.wallet_id)
 *   transaction_type   → transactionType   (VARCHAR NOT NULL)
 *   amount             → amount            (NUMERIC NOT NULL)
 *   balance_after      → balanceAfter      (NUMERIC NOT NULL)
 *   gateway            → gateway           (VARCHAR)
 *   gateway_reference  → gatewayReference  (VARCHAR)
 *   created_at         → createdAt         (TIMESTAMP DEFAULT now())
 * </pre>
 *
 * <p>
 * The wallet → user join path used in JPQL:
 * {@code l.wallet.user.userId} maps to
 * {@code ledger.wallet_id → wallets.user_id → users.user_id}.
 */
@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    // ── General user ledger ───────────────────────────────────────────────────

    /**
     * All ledger entries for any user (investor or farmer), newest first.
     *
     * Maps to:
     * SELECT l.* FROM ledger l
     * JOIN wallets w ON l.wallet_id = w.wallet_id
     * WHERE w.user_id = :userId
     * ORDER BY l.created_at DESC
     */
    @Query("SELECT l FROM Ledger l WHERE l.wallet.user.userId = :userId " +
            "ORDER BY l.createdAt DESC")
    List<Ledger> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // ── AC-5: Farmer financial-report ledger queries ──────────────────────────

    /**
     * AC-5 — All INVESTMENT-type ledger credits for a farmer's wallet.
     * These entries represent actual funding received — the canonical
     * source of truth for the "total funding received" figure when the
     * Ledger is used as the authoritative source instead of the
     * Investment table.
     *
     * Maps to:
     * SELECT l.* FROM ledger l
     * JOIN wallets w ON l.wallet_id = w.wallet_id
     * WHERE w.user_id = :farmerUserId
     * AND l.transaction_type = 'INVESTMENT'
     * ORDER BY l.created_at DESC
     */
    @Query("SELECT l FROM Ledger l " +
            "WHERE l.wallet.user.userId = :farmerUserId " +
            "  AND l.transactionType = CHC.Team.Ceylon.Harvest.Capital.entity.Ledger.TransactionType.INVESTMENT " +
            "ORDER BY l.createdAt DESC")
    List<Ledger> findInvestmentCreditsByFarmerUserId(@Param("farmerUserId") Long farmerUserId);

    /**
     * AC-5 — Sum of all INVESTMENT-type ledger credits for a farmer's wallet.
     * Cross-verified against Investment.sumTotalFundingByFarmerUserId to ensure
     * the two sources agree (they should — both are written in the same
     * transaction by InvestmentServiceImpl).
     *
     * Maps to:
     * SELECT COALESCE(SUM(l.amount), 0)
     * FROM ledger l
     * JOIN wallets w ON l.wallet_id = w.wallet_id
     * WHERE w.user_id = :farmerUserId
     * AND l.transaction_type = 'INVESTMENT'
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM Ledger l " +
            "WHERE l.wallet.user.userId = :farmerUserId " +
            "  AND l.transactionType = CHC.Team.Ceylon.Harvest.Capital.entity.Ledger.TransactionType.INVESTMENT")
    BigDecimal sumInvestmentCreditsByFarmerUserId(@Param("farmerUserId") Long farmerUserId);

    /**
     * Ledger entries for a farmer within a date range, newest first.
     * Supports period-based reconciliation in the financial report.
     *
     * Maps to:
     * SELECT l.* FROM ledger l
     * JOIN wallets w ON l.wallet_id = w.wallet_id
     * WHERE w.user_id = :farmerUserId
     * AND l.created_at BETWEEN :from AND :to
     * ORDER BY l.created_at DESC
     */
    @Query("SELECT l FROM Ledger l " +
            "WHERE l.wallet.user.userId = :farmerUserId " +
            "  AND l.createdAt BETWEEN :from AND :to " +
            "ORDER BY l.createdAt DESC")
    List<Ledger> findByFarmerUserIdAndDateRange(
            @Param("farmerUserId") Long farmerUserId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}