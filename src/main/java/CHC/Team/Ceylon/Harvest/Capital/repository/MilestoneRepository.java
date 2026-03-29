package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    @Query("""
            select m
            from Milestone m
            join fetch m.farmer f
            left join fetch m.reviewedBy rb
            where m.status = :status
            order by m.milestoneDate desc, m.createdAt desc
            """)
    List<Milestone> findAllByStatusWithRelations(@Param("status") MilestoneStatus status);

    @Query("""
            select m
            from Milestone m
            join fetch m.farmer f
            left join fetch m.reviewedBy rb
            where m.id = :id
            """)
    Optional<Milestone> findByIdWithRelations(@Param("id") Long id);

    @Query("""
            select m
            from Milestone m
            join fetch m.farmer f
            left join fetch m.reviewedBy rb
            where f.userId = :farmerUserId
            order by m.createdAt desc
            """)
    List<Milestone> findAllByFarmerUserId(@Param("farmerUserId") Long farmerUserId);
}
