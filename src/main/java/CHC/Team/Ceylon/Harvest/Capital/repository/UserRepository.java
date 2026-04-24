package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    // ── AC-3: Active user counts by role ─────────────────────────────────────

    /**
     * AC-3 – Counts users that have a specific role AND accountStatus == ACTIVE.
     * Drives the per-role breakdown in the analytics endpoint.
     */
    long countByRoleAndAccountStatus(Role role, AccountStatus accountStatus);

}