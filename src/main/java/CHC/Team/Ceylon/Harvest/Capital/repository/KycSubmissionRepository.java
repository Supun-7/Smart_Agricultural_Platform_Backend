package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KycSubmissionRepository
        extends JpaRepository<KycSubmission, String> {

    // Gets the most recent submission for a user
    // find + Top + By + UserId + OrderBy + SubmittedAt + Desc
    Optional<KycSubmission> findTopByUserUserIdOrderBySubmittedAtDesc(Long userId);

    // Gets all submissions with a specific status (for admin queue)
    List<KycSubmission> findByStatus(VerificationStatus status);

    // Gets all submissions for one user (full history)
    List<KycSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);
}