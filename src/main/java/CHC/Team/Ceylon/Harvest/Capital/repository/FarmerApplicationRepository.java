package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FarmerApplicationRepository
        extends JpaRepository<FarmerApplication, String> {

    // Gets the most recent application for a farmer
    Optional<FarmerApplication> findTopByUserUserIdOrderBySubmittedAtDesc(Long userId);

    // Gets all applications with a specific status (for admin queue)
    List<FarmerApplication> findByStatus(VerificationStatus status);

    // Gets all applications for one user (full history)
    List<FarmerApplication> findByUserUserIdOrderBySubmittedAtDesc(Long userId);
}