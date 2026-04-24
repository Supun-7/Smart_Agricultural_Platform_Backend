package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.FarmerDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.KycDetailResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.FarmerApplication;
import CHC.Team.Ceylon.Harvest.Capital.entity.KycSubmission;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Auditor detail-view endpoints.
 * The auditor opens these before deciding to approve or reject.
 *
 * <pre>
 *   GET /api/auditor/kyc/{id}     – full KYC detail with all document URLs
 *   GET /api/auditor/farmer/{id}  – full farmer application detail
 * </pre>
 */
@RestController
@RequestMapping("/api/auditor")
public class AuditorReviewDetailController {

    private final KycSubmissionRepository      kycRepo;
    private final FarmerApplicationRepository  farmerRepo;

    public AuditorReviewDetailController(KycSubmissionRepository kycRepo,
                                          FarmerApplicationRepository farmerRepo) {
        this.kycRepo    = kycRepo;
        this.farmerRepo = farmerRepo;
    }

    // ── KYC detail ────────────────────────────────────────────────────────────

    /**
     * Returns the full KYC record for a single investor submission.
     * Includes all personal info and document URLs so the auditor can
     * review them before approving or rejecting.
     */
    @GetMapping("/kyc/{id}")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<KycDetailResponse> getKycDetail(@PathVariable String id) {
        KycSubmission k = kycRepo.findByIdWithUser(id)
                .orElseThrow(() -> new RuntimeException("KYC submission not found: " + id));

        return ResponseEntity.ok(new KycDetailResponse(
                k.getId(),
                k.getUser().getUserId(),
                k.getUser().getEmail(),
                k.getTitle(),
                k.getFirstName(),
                k.getLastName(),
                k.getAge(),
                k.getNationality(),
                k.getCurrentOccupation(),
                k.getAddress(),
                k.getIdType(),
                k.getIdNumber(),
                k.getIdFrontUrl(),
                k.getIdBackUrl(),
                k.getUtilityBillUrl(),
                k.getBankStmtUrl(),
                k.getStatus().name(),
                k.getRejectionReason(),
                k.getSubmittedAt(),
                k.getReviewedAt()
        ));
    }

    // ── Farmer application detail ─────────────────────────────────────────────

    /**
     * Returns the full farmer application record.
     * Includes NIC photos, land photos, and all farm details for auditor review.
     */
    @GetMapping("/farmer/{id}")
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<FarmerDetailResponse> getFarmerDetail(@PathVariable String id) {
        FarmerApplication f = farmerRepo.findByIdWithUser(id)
                .orElseThrow(() -> new RuntimeException("Farmer application not found: " + id));

        return ResponseEntity.ok(new FarmerDetailResponse(
                f.getId(),
                f.getUser().getUserId(),
                f.getUser().getEmail(),
                f.getFarmerName(),
                f.getSurname(),
                f.getFamilyName(),
                f.getAddress(),
                f.getFarmAddress(),
                f.getYearStarted(),
                f.getNicNumber(),
                f.getFarmLocation(),
                f.getLandSizeAcres(),
                f.getCropTypes(),
                f.getLandMeasurements(),
                f.getNicFrontUrl(),
                f.getNicBackUrl(),
                f.getLandPhotoUrls(),
                f.getStatus().name(),
                f.getRejectionReason(),
                f.getSubmittedAt(),
                f.getReviewedAt()
        ));
    }
}
