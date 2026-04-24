package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.auditor.ReviewHistoryEntry;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Returns the authenticated auditor's complete decision history across all
 * three review domains: KYC submissions, farmer applications, and project approvals.
 *
 * <pre>
 *   GET /api/auditor/full-history
 * </pre>
 *
 * Entries are sorted newest-first and cover every approve/reject action the
 * calling auditor has ever made.  The existing milestone-only history remains
 * at GET /api/auditor/history (unchanged).
 */
@RestController
@RequestMapping("/api/auditor/full-history")
public class AuditorFullHistoryController {

    private final KycSubmissionRepository     kycRepo;
    private final FarmerApplicationRepository farmerRepo;
    private final LandRepository              landRepo;

    public AuditorFullHistoryController(KycSubmissionRepository kycRepo,
                                         FarmerApplicationRepository farmerRepo,
                                         LandRepository landRepo) {
        this.kycRepo    = kycRepo;
        this.farmerRepo = farmerRepo;
        this.landRepo   = landRepo;
    }

    @GetMapping
    @RequiredRole(Role.AUDITOR)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getFullHistory(HttpServletRequest request) {
        Long auditorId = (Long) request.getAttribute("userId");

        List<ReviewHistoryEntry> entries = new ArrayList<>();

        // ── KYC decisions ─────────────────────────────────────────────────────
        kycRepo.findByReviewedByUserIdOrderByReviewedAtDesc(auditorId)
               .forEach(k -> entries.add(new ReviewHistoryEntry(
                       k.getId(),
                       "KYC",
                       k.getFirstName() + " " + k.getLastName(),
                       k.getStatus() == VerificationStatus.VERIFIED ? "APPROVED" : "REJECTED",
                       k.getRejectionReason(),
                       k.getReviewedAt()
               )));

        // ── Farmer application decisions ──────────────────────────────────────
        farmerRepo.findByReviewedByUserIdOrderByReviewedAtDesc(auditorId)
                  .forEach(f -> entries.add(new ReviewHistoryEntry(
                          f.getId(),
                          "FARMER_APPLICATION",
                          (f.getFarmerName() != null ? f.getFarmerName() : "")
                              + " " + (f.getSurname() != null ? f.getSurname() : ""),
                          f.getStatus() == VerificationStatus.VERIFIED ? "APPROVED" : "REJECTED",
                          f.getRejectionReason(),
                          f.getReviewedAt()
                  )));

        // ── Project (land) decisions ──────────────────────────────────────────
        landRepo.findByReviewedByUserIdOrderByReviewedAtDesc(auditorId)
                .forEach(l -> entries.add(new ReviewHistoryEntry(
                        String.valueOf(l.getLandId()),
                        "PROJECT",
                        l.getProjectName(),
                        l.getReviewStatus() == VerificationStatus.VERIFIED ? "APPROVED" : "REJECTED",
                        l.getRejectionReason(),
                        l.getReviewedAt()
                )));

        // Sort all three streams together, newest first
        entries.sort(Comparator.comparing(ReviewHistoryEntry::reviewedAt).reversed());

        return ResponseEntity.ok(Map.of(
                "count", entries.size(),
                "items", entries
        ));
    }
}
