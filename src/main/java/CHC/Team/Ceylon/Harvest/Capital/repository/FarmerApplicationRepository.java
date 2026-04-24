package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FarmerApplicationRepository
        extends JpaRepository<FarmerApplication, String> {

    Optional<FarmerApplication> findTopByUserUserIdOrderBySubmittedAtDesc(Long userId);

    List<FarmerApplication> findByStatus(VerificationStatus status);

    List<FarmerApplication> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    @Query("select f from FarmerApplication f join fetch f.user where f.status = :status")
    List<FarmerApplication> findByStatusWithUser(@Param("status") VerificationStatus status);

    /**
     * Loads a single farmer application with its user eagerly joined.
     * Used by the auditor detail and approve/reject endpoints to avoid
     * LazyInitializationException when accessing f.getUser() outside a session.
     */
    @Query("SELECT f FROM FarmerApplication f JOIN FETCH f.user WHERE f.id = :id")
    Optional<FarmerApplication> findByIdWithUser(@Param("id") String id);

    /**
     * All farmer applications reviewed by a specific auditor, most-recent first.
     * Used for the auditor full-history endpoint.
     */
    @Query("SELECT f FROM FarmerApplication f JOIN FETCH f.user WHERE f.reviewedBy.userId = :auditorId ORDER BY f.reviewedAt DESC")
    List<FarmerApplication> findByReviewedByUserIdOrderByReviewedAtDesc(@Param("auditorId") Long auditorId);
}
