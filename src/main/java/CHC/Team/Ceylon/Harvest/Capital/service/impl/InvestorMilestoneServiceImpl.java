package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDto;
import CHC.Team.Ceylon.Harvest.Capital.dto.ProjectMilestoneResponseDto;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.MilestoneRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorMilestoneService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of InvestorMilestoneService.
 *
 * Only adds new logic — does not touch any existing service class.
 */
@Service
@Transactional(readOnly = true)
public class InvestorMilestoneServiceImpl implements InvestorMilestoneService {

    private final LandRepository       landRepository;
    private final InvestmentRepository investmentRepository;
    private final MilestoneRepository  milestoneRepository;

    public InvestorMilestoneServiceImpl(
            LandRepository       landRepository,
            InvestmentRepository investmentRepository,
            MilestoneRepository  milestoneRepository) {
        this.landRepository       = landRepository;
        this.investmentRepository = investmentRepository;
        this.milestoneRepository  = milestoneRepository;
    }

    /**
     * AC-1: investor navigates to a project detail page.
     * AC-2, AC-5: only APPROVED milestones returned (PENDING/REJECTED excluded by query).
     * AC-3: each milestone has progressPercentage, notes, date, approvalStatus.
     * AC-4: milestones sorted latest-first (ORDER BY milestoneDate DESC in repository).
     */
    @Override
    public ProjectMilestoneResponseDto getApprovedMilestones(Long investorUserId, Long landId) {

        // 1. Verify the land exists
        Land land = landRepository.findById(landId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + landId));

        // 2. Verify the investor has funded this land
        //    (prevents an investor peeking at projects they haven't invested in)
        boolean hasFunded = milestoneRepository.existsByInvestorAndLand(investorUserId, landId);
        if (!hasFunded) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You have not invested in this project");
        }

        // 3. Fetch this investor's investment amount for this land
        BigDecimal amountInvested = investmentRepository
                .findAllByUserIdWithLand(investorUserId)
                .stream()
                .filter(inv -> inv.getLand().getLandId().equals(landId))
                .map(Investment::getAmountInvested)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Fetch APPROVED milestones only — repository enforces the filter
        List<Milestone> approved = milestoneRepository
                .findByLandIdAndStatus(landId, MilestoneStatus.APPROVED);

        // 5. Map each Milestone entity → MilestoneDto (AC-3 fields)
        List<MilestoneDto> milestoneDtos = approved.stream()
                .map(m -> new MilestoneDto(
                        m.getMilestoneId(),
                        m.getProgressPercentage(),
                        m.getNotes(),
                        m.getMilestoneDate(),
                        m.getStatus().name()   // always "APPROVED" here
                ))
                .collect(Collectors.toList());

        // 6. Build and return the full project detail response
        return new ProjectMilestoneResponseDto(
                land.getLandId(),
                land.getProjectName(),
                land.getLocation(),
                land.getTotalValue(),
                amountInvested,
                land.getProgressPercentage(),  // overall project progress %
                milestoneDtos                  // AC-4: visual timeline data
        );
    }
}
