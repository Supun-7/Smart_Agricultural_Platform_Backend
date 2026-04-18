package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface KycSubmissionRepository
        extends JpaRepository<KycSubmission, String> {

    Optional<KycSubmission> findTopByUserUserIdOrderBySubmittedAtDesc(Long userId);

    List<KycSubmission> findByStatus(VerificationStatus status);

    List<KycSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    @Query("select k from KycSubmission k join fetch k.user where k.status = :status")
    List<KycSubmission> findByStatusWithUser(@Param("status") VerificationStatus status);

    /**
     * Loads a single KYC submission with its user eagerly joined.
     * Used by the auditor detail and approve/reject endpoints to avoid
     * LazyInitializationException when accessing k.getUser() outside a session.
     */
    @Query("SELECT k FROM KycSubmission k JOIN FETCH k.user WHERE k.id = :id")
    Optional<KycSubmission> findByIdWithUser(@Param("id") String id);

    /**
     * All KYC submissions reviewed by a specific auditor, most-recent first.
     * Used for the auditor full-history endpoint.
     */
    @Query("SELECT k FROM KycSubmission k JOIN FETCH k.user WHERE k.reviewedBy.userId = :auditorId ORDER BY k.reviewedAt DESC")
    List<KycSubmission> findByReviewedByUserIdOrderByReviewedAtDesc(@Param("auditorId") Long auditorId);
}
