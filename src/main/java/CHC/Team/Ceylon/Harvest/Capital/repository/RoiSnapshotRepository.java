package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.RoiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoiSnapshotRepository extends JpaRepository<RoiSnapshot, Long> {

    Optional<RoiSnapshot> findByInvestmentInvestmentIdAndSnapshotDate(Long investmentId, LocalDate snapshotDate);

    @Query("""
            select rs
            from RoiSnapshot rs
            join fetch rs.investment i
            join fetch i.land l
            where i.investor.userId = :userId
            order by rs.snapshotDate asc, i.investmentId asc
            """)
    List<RoiSnapshot> findAllByInvestorUserIdOrderBySnapshotDateAsc(@Param("userId") Long userId);

    @Query("""
            select rs
            from RoiSnapshot rs
            join fetch rs.investment i
            join fetch i.land l
            where i.investmentId in :investmentIds
            order by rs.snapshotDate asc
            """)
    List<RoiSnapshot> findAllByInvestmentIds(@Param("investmentIds") List<Long> investmentIds);
}
