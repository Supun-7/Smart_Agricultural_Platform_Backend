package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ComplianceScoreRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ComplianceScoreResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.FarmerComplianceListItem;
import CHC.Team.Ceylon.Harvest.Capital.entity.Farmer;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AC-3 / AC-5: Business logic for assigning and retrieving compliance scores.
 *
 * Scoring criteria (AC-1):
 *   - Milestone update frequency  (0–40 pts)
 *   - Evidence quality            (0–40 pts)
 *   - Timeliness of submissions   (0–20 pts)
 *   Total: 0–100 pts
 */
@Service
public class ComplianceScoringService {

    private final FarmerRepository farmerRepository;
    private final UserRepository   userRepository;

    public ComplianceScoringService(
            FarmerRepository farmerRepository,
            UserRepository   userRepository
    ) {
        this.farmerRepository = farmerRepository;
        this.userRepository   = userRepository;
    }

    /**
     * AC-3: Assign or update a compliance score for a given farmer.
     * The score is persisted immediately (AC-5).
     *
     * @param farmerId   the target farmer's primary key
     * @param auditorId  the logged-in auditor's user ID (from JWT)
     * @param request    validated score + optional notes
     * @return a fully populated {@link ComplianceScoreResponse}
     */
    @Transactional
    public ComplianceScoreResponse assignScore(
            Long farmerId,
            Long auditorId,
            ComplianceScoreRequest request
    ) {
        Farmer farmer = farmerRepository.findByIdWithUserAndScoredBy(farmerId)
            .orElseThrow(() -> new IllegalArgumentException("Farmer not found: " + farmerId));

        User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new IllegalArgumentException("Auditor not found: " + auditorId));

        // AC-5: update and save immediately — no extra round-trip needed
        farmer.setComplianceScore(request.score());
        farmer.setComplianceNotes(request.notes());
        farmer.setComplianceScoredAt(LocalDateTime.now());
        farmer.setComplianceScoredBy(auditor);

        farmerRepository.save(farmer);

        return toResponse(farmer);
    }

    /**
     * AC-4: Retrieve the current compliance score for a single farmer.
     */
    @Transactional(readOnly = true)
    public ComplianceScoreResponse getScore(Long farmerId) {
        Farmer farmer = farmerRepository.findByIdWithUserAndScoredBy(farmerId)
            .orElseThrow(() -> new IllegalArgumentException("Farmer not found: " + farmerId));
        return toResponse(farmer);
    }

    /**
     * AC-4: List all farmers with their compliance scores for dashboard views.
     */
    @Transactional(readOnly = true)
    public List<FarmerComplianceListItem> listAll() {
        return farmerRepository.findAllWithUserAndScoredBy()
            .stream()
            .map(this::toListItem)
            .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private ComplianceScoreResponse toResponse(Farmer f) {
        User u       = f.getUser();
        User scoredBy = f.getComplianceScoredBy();
        return new ComplianceScoreResponse(
            f.getFarmerId(),
            u  != null ? u.getUserId()  : null,
            u  != null ? u.getFullName() : "—",
            u  != null ? u.getEmail()    : "—",
            f.getComplianceScore(),
            f.getComplianceNotes(),
            f.getComplianceScoredAt(),
            scoredBy != null ? scoredBy.getFullName() : null
        );
    }

    private FarmerComplianceListItem toListItem(Farmer f) {
        User u       = f.getUser();
        User scoredBy = f.getComplianceScoredBy();
        return new FarmerComplianceListItem(
            f.getFarmerId(),
            u  != null ? u.getUserId()              : null,
            u  != null ? u.getFullName()             : "—",
            u  != null ? u.getEmail()                : "—",
            u  != null ? u.getVerificationStatus().name() : "—",
            f.getComplianceScore(),
            f.getComplianceNotes(),
            f.getComplianceScoredAt(),
            scoredBy != null ? scoredBy.getFullName() : null
        );
    }
}
