package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.DashboardSummaryDto;
import CHC.Team.Ceylon.Harvest.Capital.dto.FarmerDashboardResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.FundedLandDto;
import CHC.Team.Ceylon.Harvest.Capital.entity.Farmer;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.exception.FarmerDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FarmerDashboardServiceImpl implements FarmerDashboardService {

    private final ProjectRepository projectRepository;
    private final InvestmentRepository investmentRepository;
    private final FarmerRepository farmerRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;

    public FarmerDashboardServiceImpl(
            ProjectRepository projectRepository,
            InvestmentRepository investmentRepository,
            FarmerRepository farmerRepository,
            FarmerApplicationRepository farmerApplicationRepository) {
        this.projectRepository = projectRepository;
        this.investmentRepository = investmentRepository;
        this.farmerRepository = farmerRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FarmerDashboardResponse getFarmerDashboard(Long userId) {
        try {
            List<Project> projects = projectRepository.findByFarmerUserUserIdOrderByIdAsc(userId);
            Optional<Farmer> farmerOpt = farmerRepository.findByUserUserId(userId);
            Optional<FarmerApplication> latestApplication = farmerApplicationRepository
                    .findTopByUserUserIdOrderBySubmittedAtDesc(userId);

            List<FundedLandDto> fundedLands = new ArrayList<>();
            BigDecimal totalInvestmentAmount = BigDecimal.ZERO;

            for (Project project : projects) {
                BigDecimal investmentAmount = resolveInvestmentAmount(project);
                if (investmentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                totalInvestmentAmount = totalInvestmentAmount.add(investmentAmount);
                fundedLands.add(new FundedLandDto(
                        project.getId(),
                        project.getProjectName(),
                        resolveLandName(project, farmerOpt, latestApplication),
                        resolveFarmLocation(farmerOpt, latestApplication),
                        investmentAmount,
                        toBigDecimal(project.getProgress())));
            }

            return new FarmerDashboardResponse(
                    new DashboardSummaryDto(fundedLands.size(), totalInvestmentAmount),
                    fundedLands);
        } catch (Exception ex) {
            throw new FarmerDashboardException(
                    "Unable to load farmer dashboard at the moment. Please try again later.",
                    ex);
        }
    }

    private BigDecimal resolveInvestmentAmount(Project project) {
        Double summedInvestment = investmentRepository.sumAmountByProjectId(project.getId());
        if (summedInvestment != null && summedInvestment > 0) {
            return toBigDecimal(summedInvestment);
        }

        return toBigDecimal(project.getCurrentAmount());
    }

    private String resolveLandName(
            Project project,
            Optional<Farmer> farmerOpt,
            Optional<FarmerApplication> latestApplication) {
        if (farmerOpt.isPresent() && hasText(farmerOpt.get().getLandName())) {
            return farmerOpt.get().getLandName();
        }

        if (latestApplication.isPresent() && hasText(latestApplication.get().getFarmAddress())) {
            return latestApplication.get().getFarmAddress();
        }

        if (latestApplication.isPresent() && hasText(latestApplication.get().getFarmLocation())) {
            return latestApplication.get().getFarmLocation();
        }

        return project.getProjectName();
    }

    private String resolveFarmLocation(
            Optional<Farmer> farmerOpt,
            Optional<FarmerApplication> latestApplication) {
        if (farmerOpt.isPresent() && hasText(farmerOpt.get().getLandLocation())) {
            return farmerOpt.get().getLandLocation();
        }

        if (latestApplication.isPresent() && hasText(latestApplication.get().getFarmLocation())) {
            return latestApplication.get().getFarmLocation();
        }

        if (latestApplication.isPresent() && hasText(latestApplication.get().getFarmAddress())) {
            return latestApplication.get().getFarmAddress();
        }

        return "Not available";
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
