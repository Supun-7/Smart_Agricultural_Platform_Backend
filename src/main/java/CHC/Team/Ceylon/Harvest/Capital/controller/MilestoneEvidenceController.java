package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.MilestoneDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.MilestoneService;
import CHC.Team.Ceylon.Harvest.Capital.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Handles milestone evidence file uploads by verified farmers.
 */
@RestController
@RequestMapping("/api/farmer/milestones")
public class MilestoneEvidenceController {

    private static final Logger log = LoggerFactory.getLogger(MilestoneEvidenceController.class);

    private final MilestoneService milestoneService;
    private final SupabaseStorageService supabaseStorageService;
    private final JwtUtil jwtUtil;

    public MilestoneEvidenceController(
            MilestoneService milestoneService,
            SupabaseStorageService supabaseStorageService,
            JwtUtil jwtUtil
    ) {
        this.milestoneService = milestoneService;
        this.supabaseStorageService = supabaseStorageService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * POST /api/farmer/milestones/{milestoneId}/evidence
     * Accepts one or more files as multipart/form-data (field name: "files").
     */
    @PostMapping(
            value = "/{milestoneId}/evidence",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @RequiredRole(Role.FARMER)
    public ResponseEntity<?> uploadEvidence(
            @PathVariable Long milestoneId,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Missing or invalid Authorization header.");
        }

        if (files == null || files.length == 0) {
            throw new BadRequestException("At least one file must be provided.");
        }

        Long farmerUserId = extractUserId(authHeader);

        log.info("Farmer userId={} uploading {} evidence file(s) for milestoneId={}",
                farmerUserId, files.length, milestoneId);

        List<String> uploadedUrls = Arrays.stream(files)
                .map(supabaseStorageService::upload)
                .toList();

        MilestoneDetailResponse updated =
                milestoneService.attachEvidenceFiles(milestoneId, farmerUserId, uploadedUrls);

        log.info("Successfully attached {} file(s) to milestoneId={}", uploadedUrls.size(), milestoneId);

        return ResponseEntity.ok(Map.of(
                "message", "Evidence uploaded successfully.",
                "uploadedCount", uploadedUrls.size(),
                "milestone", updated
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }
}