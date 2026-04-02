package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminAuditLogDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.UserDTO;
import CHC.Team.Ceylon.Harvest.Capital.entity.AdminAuditLog;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.AdminAuditLogRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final InvestmentRepository investmentRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminDashboardServiceImpl(
            UserRepository userRepository,
            InvestmentRepository investmentRepository,
            AdminAuditLogRepository adminAuditLogRepository) {
        this.userRepository = userRepository;
        this.investmentRepository = investmentRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponseDTO getDashboardData() {
        try {
            List<UserDTO> farmerDTOs = mapUsersToDTO(userRepository.findByRole(Role.FARMER));
            List<UserDTO> investorDTOs = mapUsersToDTO(userRepository.findByRole(Role.INVESTOR));
            List<UserDTO> auditorDTOs = mapUsersToDTO(userRepository.findByRole(Role.AUDITOR));
            List<UserDTO> adminDTOs = mapUsersToDTO(userRepository.findByRole(Role.ADMIN));
            List<UserDTO> systemAdminDTOs = mapUsersToDTO(userRepository.findByRole(Role.SYSTEM_ADMIN));

            List<AdminAuditLogDTO> auditLogs = adminAuditLogRepository.findTop20ByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toAuditLogDTO)
                    .toList();

            BigDecimal totalInvestment = investmentRepository.sumTotalInvestmentPlatformWide();
            if (totalInvestment == null) {
                totalInvestment = BigDecimal.ZERO;
            }

            return new AdminDashboardResponseDTO(
                    farmerDTOs.size(),
                    investorDTOs.size(),
                    totalInvestment,
                    farmerDTOs,
                    investorDTOs,
                    auditorDTOs,
                    adminDTOs,
                    systemAdminDTOs,
                    auditLogs
            );
        } catch (Exception ex) {
            throw new AdminDashboardException(
                    "Unable to load admin dashboard at the moment. Please try again later.",
                    ex
            );
        }
    }

    private List<UserDTO> mapUsersToDTO(List<User> users) {
        return users.stream().map(this::toUserDTO).toList();
    }

    private UserDTO toUserDTO(User user) {
        String accountStatus = user.getAccountStatus() != null
                ? user.getAccountStatus().name()
                : "ACTIVE";
        return new UserDTO(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getVerificationStatus().name(),
                accountStatus
        );
    }

    private AdminAuditLogDTO toAuditLogDTO(AdminAuditLog log) {
        return new AdminAuditLogDTO(
                log.getId(),
                log.getActionType(),
                log.getAdminUser().getUserId(),
                log.getAdminUser().getFullName(),
                log.getTargetUser().getUserId(),
                log.getTargetUser().getFullName(),
                log.getTargetUser().getEmail(),
                log.getDetails(),
                log.getCreatedAt()
        );
    }
}
