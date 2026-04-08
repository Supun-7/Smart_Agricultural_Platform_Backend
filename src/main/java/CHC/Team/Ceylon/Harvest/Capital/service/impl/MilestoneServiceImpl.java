package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.EvidenceFileResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AuditActionType;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.MilestoneRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.AuditLogService;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import CHC.Team.Ceylon.Harvest.Capital.service.blockchain.BlockchainService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class MilestoneServiceImpl implements MilestoneService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneServiceImpl.class);

    private final MilestoneRepository  milestoneRepository;
    private final UserRepository       userRepository;
    private final ProjectRepository    projectRepository;
    private final LandRepository       landRepository;
    private final InvestmentRepository investmentRepository;
    private final ObjectMapper         objectMapper;
    private final AuditLogService      auditLogService;
    private final BlockchainService    blockchainService;

    public MilestoneServiceImpl(
            MilestoneRepository  milestoneRepository,
            UserRepository       userRepository,
            ProjectRepository    projectRepository,
            LandRepository       landRepository,
            InvestmentRepository investmentRepository,
            ObjectMapper         objectMapper,
            AuditLogService      auditLogService,
            BlockchainService    blockchainService) {
        this.milestoneRepository  = milestoneRepository;
        this.userRepository       = userRepository;
        this.projectRepository    = projectRepository;
        this.landRepository       = landRepository;
        this.investmentRepository = investmentRepository;
        this.objectMapper         = objectMapper;
        this.auditLogService      = auditLogService;
        this.blockchainService    = blockchainService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneSummaryResponse> getPendingMilestones() {
        return milestoneRepository.findAllByStatusWithRelations(MilestoneStatus.PENDING)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneDetailResponse getMilestoneDetail(Long milestoneId) {
        return toDetailResponse(loadMilestone(milestoneId));
    }

    @Override
    public MilestoneDetailResponse approveMilestone(Long milestoneId, Long auditorId) {
        log.info("Approving milestoneId={} by auditorId={}", milestoneId, auditorId);

        Milestone milestone = loadMilestone(milestoneId);
        ensurePending(milestone);

        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditor not found: " + auditorId));

        milestone.setStatus(MilestoneStatus.APPROVED);
        milestone.setReviewedBy(auditor);
        milestone.setReviewedAt(LocalDateTime.now());
        milestone.setRejectionReason(null);

        syncProgress(milestone);
        milestoneRepository.save(milestone);

        // ── Record milestone approval on Polygon Amoy ─────────────────────────
        // For each investment in this farmer's land, post a milestone approval
        // event on-chain linked back to that investment's contract reference.
        // Gas is paid by the CHC system wallet — no crypto wallet needed by anyone.
        recordMilestoneOnChain(milestone);

        auditLogService.log(
                AuditActionType.APPROVED,
                milestoneId,
                milestone.getFarmer().getFullName(),
                auditorId
        );

        log.info("Milestone approved milestoneId={}", milestoneId);
        return toDetailResponse(milestone);
    }

    @Override
    public MilestoneDetailResponse rejectMilestone(Long milestoneId, Long auditorId, String reason) {
        Milestone milestone = loadMilestone(milestoneId);
        ensurePending(milestone);

        String cleanedReason = reason == null ? "" : reason.trim();
        if (cleanedReason.isBlank()) {
            throw new BadRequestException("Rejection reason is required");
        }

        User auditor = userRepository.findById(auditorId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditor not found: " + auditorId));

        milestone.setStatus(MilestoneStatus.REJECTED);
        milestone.setReviewedBy(auditor);
        milestone.setReviewedAt(LocalDateTime.now());
        milestone.setRejectionReason(cleanedReason);
        milestoneRepository.save(milestone);

        auditLogService.log(
                AuditActionType.REJECTED,
                milestoneId,
                milestone.getFarmer().getFullName(),
                auditorId
        );

        return toDetailResponse(milestone);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneDetailResponse> getFarmerMilestones(Long farmerUserId) {
        return milestoneRepository.findAllByFarmerUserId(farmerUserId)
                .stream()
                .map(this::toDetailResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneDetailResponse> getApprovedMilestonesForProjects(List<String> projectNames) {
        if (projectNames == null || projectNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedProjectNames = projectNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        if (normalizedProjectNames.isEmpty()) {
            return Collections.emptyList();
        }

        return milestoneRepository.findAllByStatusWithRelations(MilestoneStatus.APPROVED)
                .stream()
                .filter(milestone -> {
                    Project project = findProjectForFarmer(milestone.getFarmer().getUserId());
                    return normalizedProjectNames.contains(project.getProjectName().toLowerCase(Locale.ROOT));
                })
                .map(this::toDetailResponse)
                .toList();
    }

    @Override
    public MilestoneDetailResponse attachEvidenceFiles(Long milestoneId, Long farmerUserId, List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            throw new BadRequestException("At least one file URL must be provided.");
        }

        Milestone milestone = loadMilestone(milestoneId);

        if (!milestone.getFarmer().getUserId().equals(farmerUserId)) {
            throw new ResourceNotFoundException("Milestone not found for the current farmer: " + milestoneId);
        }

        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            throw new ConflictException("Evidence can only be uploaded to a PENDING milestone.");
        }

        List<String> existingUrls = parseUrlList(milestone.getEvidenceFilesJson());
        List<String> mergedUrls   = new ArrayList<>(existingUrls);
        mergedUrls.addAll(fileUrls);

        try {
            milestone.setEvidenceFilesJson(objectMapper.writeValueAsString(mergedUrls));
        } catch (Exception e) {
            log.error("Failed to serialize evidence file URLs for milestoneId={}", milestoneId, e);
            throw new BadRequestException("Failed to save evidence file references. Please try again.");
        }

        milestoneRepository.save(milestone);
        log.info("Attached {} evidence file(s) to milestoneId={}", fileUrls.size(), milestoneId);
        return toDetailResponse(milestone);
    }

    // ── Blockchain helper ─────────────────────────────────────────────────────

    /**
     * After a milestone is approved, records the event on-chain for every
     * investment linked to this farmer's land.
     *
     * Each on-chain event references the original investment's contractAddress
     * so the full history (invest → milestone1 → milestone2 → ...) is traceable
     * on PolygonScan.
     *
     * Blockchain failures are logged but do NOT roll back the milestone approval —
     * the approval is already committed to the DB.
     */
    private void recordMilestoneOnChain(Milestone milestone) {
        Long farmerUserId = milestone.getFarmer().getUserId();
        int  progressPct  = milestone.getProgressPercentage();

        List<Investment> investments =
                investmentRepository.findAllByFarmerUserIdWithInvestor(farmerUserId);

        if (investments.isEmpty()) {
            log.info("[Blockchain] No investments found for farmerUserId={}, skipping on-chain milestone record",
                    farmerUserId);
            return;
        }

        for (Investment inv : investments) {
            String contractRef = inv.getContractAddress();

            // Skip investments that were created before blockchain integration
            // (they have no contractAddress yet)
            if (contractRef == null || contractRef.isBlank()
                    || contractRef.equals("PENDING")
                    || contractRef.startsWith("BLOCKCHAIN_ERROR")) {
                log.warn("[Blockchain] Skipping on-chain milestone for investmentId={} — no valid contractAddress",
                        inv.getInvestmentId());
                continue;
            }

            BlockchainService.ContractResult result =
                    blockchainService.recordMilestoneApproval(
                            contractRef,
                            milestone.getId(),
                            progressPct);

            if (result.success()) {
                log.info("[Blockchain] Milestone on-chain | milestoneId={} investmentId={} tx={}",
                        milestone.getId(), inv.getInvestmentId(), result.txHash());
            } else {
                log.error("[Blockchain] Failed to record milestone on-chain | milestoneId={} investmentId={} error={}",
                        milestone.getId(), inv.getInvestmentId(), result.errorMessage());
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Milestone loadMilestone(Long milestoneId) {
        return milestoneRepository.findByIdWithRelations(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
    }

    private void ensurePending(Milestone milestone) {
        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            throw new ConflictException("Milestone has already been actioned");
        }
    }

    private Project findProjectForFarmer(Long farmerUserId) {
        return projectRepository.findByFarmerUserUserIdOrderByIdAsc(farmerUserId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found for farmer user id: " + farmerUserId));
    }

    private void syncProgress(Milestone milestone) {
        log.info("syncProgress start milestoneId={}, farmerId={}",
                milestone.getId(), milestone.getFarmer().getUserId());

        Project project = findProjectForFarmer(milestone.getFarmer().getUserId());
        project.setProgress(milestone.getProgressPercentage().doubleValue());
        projectRepository.save(project);

        List<Land> lands = landRepository.findByProjectNameIgnoreCase(project.getProjectName());
        for (Land land : lands) {
            land.setProgressPercentage(milestone.getProgressPercentage());
        }
        if (!lands.isEmpty()) {
            landRepository.saveAll(lands);
        }

        log.info("syncProgress complete milestoneId={}", milestone.getId());
    }

    private MilestoneSummaryResponse toSummaryResponse(Milestone milestone) {
        Project project = findProjectForFarmer(milestone.getFarmer().getUserId());
        return new MilestoneSummaryResponse(
                milestone.getId(),
                milestone.getFarmer().getFullName(),
                project.getProjectName(),
                milestone.getProgressPercentage(),
                milestone.getNotes(),
                milestone.getMilestoneDate(),
                milestone.getStatus()
        );
    }

    private MilestoneDetailResponse toDetailResponse(Milestone milestone) {
        Project project = findProjectForFarmer(milestone.getFarmer().getUserId());
        return new MilestoneDetailResponse(
                milestone.getId(),
                milestone.getFarmer().getFullName(),
                milestone.getFarmer().getEmail(),
                project.getProjectName(),
                milestone.getProgressPercentage(),
                milestone.getNotes(),
                milestone.getMilestoneDate(),
                milestone.getStatus(),
                milestone.getRejectionReason(),
                milestone.getReviewedAt(),
                milestone.getReviewedBy() != null ? milestone.getReviewedBy().getFullName() : null,
                milestone.getCreatedAt(),
                parseEvidenceFiles(milestone.getEvidenceFilesJson())
        );
    }

    private List<EvidenceFileResponse> parseEvidenceFiles(String evidenceFilesJson) {
        return parseUrlList(evidenceFilesJson).stream()
                .map(this::toEvidenceFile)
                .toList();
    }

    private List<String> parseUrlList(String evidenceFilesJson) {
        if (evidenceFilesJson == null || evidenceFilesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> urls = objectMapper.readValue(evidenceFilesJson, new TypeReference<List<String>>() {});
            return urls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .toList();
        } catch (Exception ignored) {
            List<String> files = new ArrayList<>();
            for (String url : evidenceFilesJson.split(",")) {
                String trimmed = url.trim();
                if (!trimmed.isBlank()) files.add(trimmed);
            }
            return files;
        }
    }

    private EvidenceFileResponse toEvidenceFile(String url) {
        String fileName = url;
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isBlank()) {
                int slash = path.lastIndexOf('/');
                fileName = slash >= 0 ? path.substring(slash + 1) : path;
            }
        } catch (Exception ignored) {
            int slash = url.lastIndexOf('/');
            if (slash >= 0 && slash < url.length() - 1) {
                fileName = url.substring(slash + 1);
            }
        }
        return new EvidenceFileResponse(fileName, url);
    }
}
