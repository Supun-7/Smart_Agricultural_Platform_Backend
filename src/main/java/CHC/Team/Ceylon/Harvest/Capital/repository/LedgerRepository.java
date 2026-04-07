package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    /** All ledger entries for an investor, newest first. */
    @Query("SELECT l FROM Ledger l WHERE l.wallet.user.userId = :userId ORDER BY l.createdAt DESC")
    List<Ledger> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
