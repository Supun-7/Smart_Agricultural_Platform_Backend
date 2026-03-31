package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.EvidenceFileResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneSummaryResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.Milestone;
import CHC.Team.Ceylon.Harvest.Capital.entity.Project;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.MilestoneStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ConflictException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.MilestoneRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.ProjectRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final LandRepository landRepository;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(MilestoneServiceImpl.class);


    public MilestoneServiceImpl(
            MilestoneRepository milestoneRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            LandRepository landRepository,
            ObjectMapper objectMapper
    ) {
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.landRepository = landRepository;
        this.objectMapper = objectMapper;
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
    log.info("Loaded milestone id={}, status={}, farmerId={}",
            milestone.getId(),
            milestone.getStatus(),
            milestone.getFarmer() != null ? milestone.getFarmer().getUserId() : null);

    ensurePending(milestone);

    User auditor = userRepository.findById(auditorId)
            .orElseThrow(() -> new ResourceNotFoundException("Auditor not found: " + auditorId));

    milestone.setStatus(MilestoneStatus.APPROVED);
    milestone.setReviewedBy(auditor);
    milestone.setReviewedAt(LocalDateTime.now());
    milestone.setRejectionReason(null);

    log.info("Before syncProgress for milestoneId={}", milestoneId);
    syncProgress(milestone);

    log.info("Before milestone save for milestoneId={}", milestoneId);
    milestoneRepository.save(milestone);

    log.info("Milestone approved successfully milestoneId={}", milestoneId);
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
                        "Project not found for farmer user id: " + farmerUserId
                ));
    }

   private void syncProgress(Milestone milestone) {
    log.info("syncProgress start milestoneId={}, farmerId={}",
            milestone.getId(),
            milestone.getFarmer().getUserId());

    Project project = findProjectForFarmer(milestone.getFarmer().getUserId());
    log.info("Resolved project id={}, name={}", project.getId(), project.getProjectName());

    project.setProgress(milestone.getProgressPercentage().doubleValue());
    projectRepository.save(project);

    List<Land> lands = landRepository.findByProjectNameIgnoreCase(project.getProjectName());
    log.info("Found {} land rows for projectName={}", lands.size(), project.getProjectName());

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
        if (evidenceFilesJson == null || evidenceFilesJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<String> urls = objectMapper.readValue(evidenceFilesJson, new TypeReference<List<String>>() {});
            return urls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .map(this::toEvidenceFile)
                    .toList();
        } catch (Exception ignored) {
            List<EvidenceFileResponse> files = new ArrayList<>();
            for (String url : evidenceFilesJson.split(",")) {
                if (!url.isBlank()) {
                    files.add(toEvidenceFile(url.trim()));
                }
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
