package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    @Query("select coalesce(sum(i.amount), 0) from Investment i where i.project.id = :projectId")
    Double sumAmountByProjectId(@Param("projectId") Long projectId);
}
