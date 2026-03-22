package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.UserDTO;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregates platform-wide data required by the Admin Dashboard.
 *
 * <p>All database reads are wrapped in a single read-only transaction so that
 * Hibernate does not issue redundant flushes and the data is consistent within
 * a single request snapshot.
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final InvestmentRepository investmentRepository;

    public AdminDashboardServiceImpl(
            UserRepository userRepository,
            InvestmentRepository investmentRepository) {
        this.userRepository       = userRepository;
        this.investmentRepository = investmentRepository;
    }

    /**
     * Fetches all farmers, all investors, and the platform-wide investment sum,
     * then assembles them into a single {@link AdminDashboardResponseDTO}.
     *
     * @return populated dashboard response
     * @throws AdminDashboardException if any repository call fails
     */
    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponseDTO getDashboardData() {
        try {
            // ── 1. Fetch users by role ────────────────────────────────────────
            List<User> farmerUsers   = userRepository.findByRole(Role.FARMER);
            List<User> investorUsers = userRepository.findByRole(Role.INVESTOR);

            // ── 2. Map entities → DTOs ────────────────────────────────────────
            List<UserDTO> farmerDTOs   = mapUsersToDTO(farmerUsers);
            List<UserDTO> investorDTOs = mapUsersToDTO(investorUsers);

            // ── 3. Compute platform-wide total investment ─────────────────────
            BigDecimal totalInvestment = investmentRepository.sumTotalInvestmentPlatformWide();

            // Guard against a null return even though COALESCE should prevent it
            if (totalInvestment == null) {
                totalInvestment = BigDecimal.ZERO;
            }

            // ── 4. Assemble and return the response ───────────────────────────
            return new AdminDashboardResponseDTO(
                    farmerDTOs.size(),
                    investorDTOs.size(),
                    totalInvestment,
                    farmerDTOs,
                    investorDTOs
            );

        } catch (Exception ex) {
            // Wrap any unexpected exception so the global handler maps it to HTTP 500
            throw new AdminDashboardException(
                    "Unable to load admin dashboard at the moment. Please try again later.",
                    ex
            );
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Converts a list of {@link User} entities into a list of {@link UserDTO}s.
     * Only the fields needed by the dashboard are extracted.
     */
    private List<UserDTO> mapUsersToDTO(List<User> users) {
        return users.stream()
                .map(this::toUserDTO)
                .toList();
    }

    /**
     * Maps a single {@link User} to a {@link UserDTO}.
     *
     * <p>The {@code status} field is populated from {@code verificationStatus}
     * because the database has no separate "account status" column — the
     * verification status effectively reflects whether the account is active,
     * pending review, or rejected.
     */
    private UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getVerificationStatus().name()
        );
    }
}
