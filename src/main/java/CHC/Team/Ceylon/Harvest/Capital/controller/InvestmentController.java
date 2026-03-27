package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import CHC.Team.Ceylon.Harvest.Capital.service.BlockchainService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/investment")
@CrossOrigin(origins = "*")
public class InvestmentController {

    private final UserRepository             userRepository;
    private final WalletRepository           walletRepository;
    private final LandRepository             landRepository;
    private final InvestmentRepository       investmentRepository;
    private final FarmerApplicationRepository farmerApplicationRepository;
    private final JwtUtil                    jwtUtil;
    private final BlockchainService          blockchainService;

    public InvestmentController(
            UserRepository userRepository,
            WalletRepository walletRepository,
            LandRepository landRepository,
            InvestmentRepository investmentRepository,
            FarmerApplicationRepository farmerApplicationRepository,
            JwtUtil jwtUtil,
            BlockchainService blockchainService) {
        this.userRepository              = userRepository;
        this.walletRepository            = walletRepository;
        this.landRepository              = landRepository;
        this.investmentRepository        = investmentRepository;
        this.farmerApplicationRepository = farmerApplicationRepository;
        this.jwtUtil                     = jwtUtil;
        this.blockchainService           = blockchainService;
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        return Long.parseLong(jwtUtil.extractUserId(token));
    }

    // ── POST /api/investment/fund ─────────────────────────────
    // AC-2: Investor selects a land and enters investment amount
    // AC-3: System validates wallet balance before processing
    // AC-4: Returns error if balance insufficient
    // AC-5: Creates transaction record
    // AC-6: Deducts wallet balance
    // AC-7: Updates land status to FUNDED
    // AC-8: Investment appears on both dashboards
    @PostMapping("/fund")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> fundLand(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FundRequest request) {

        Long userId = extractUserId(authHeader);

        // Get investor
        User investor = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Investor not found"));

        // Get land
        Land land = landRepository.findById(request.landId())
                .orElseThrow(() -> new RuntimeException("Land not found"));

        // Check land is still available
        if (!land.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "This land has already been funded"));
        }

        // AC-3: Get investor wallet and validate balance
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        BigDecimal amount = BigDecimal.valueOf(request.amount());

        // AC-4: Insufficient balance check
        if (wallet.getBalance().compareTo(amount) < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Insufficient wallet balance",
                    "required", amount,
                    "available", wallet.getBalance()
            ));
        }

        // Minimum investment check
        if (land.getMinimumInvestment() != null &&
                amount.compareTo(land.getMinimumInvestment()) < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Investment amount is below minimum",
                    "minimum", land.getMinimumInvestment()
            ));
        }

        // ── Get farmer details for the contract ──────────────
        String farmerName    = "Ceylon Harvest Farmer";
        String farmLocation  = land.getLocation() != null ? land.getLocation() : "Sri Lanka";
        String cropTypes     = "Mixed Agriculture";
        long   returnPercent = 18L;

        // Try to get real farmer details from their application
        var farmerApp = farmerApplicationRepository
                .findTopByUserUserIdOrderBySubmittedAtDesc(
                        land.getLandId());
        if (farmerApp.isPresent()) {
            var app = farmerApp.get();
            if (app.getFarmerName() != null) farmerName = app.getFarmerName();
            if (app.getCropTypes()  != null) cropTypes  = app.getCropTypes();
        }

        // Harvest date = 6 months from now
        long harvestTimestamp = LocalDate.now()
                .plusMonths(6)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .getEpochSecond();

        // ── Deploy smart contract on Polygon Mumbai ───────────
        String contractAddress  = null;
        String polygonScanLink  = null;
        String contractError    = null;

        try {
            contractAddress = blockchainService.deployInvestmentContract(
                    investor.getFullName(),
                    investor.getEmail(),
                    farmerName,
                    farmLocation,
                    cropTypes,
                    request.amount(),
                    returnPercent,
                    harvestTimestamp
            );
            polygonScanLink = blockchainService.buildPolygonScanLink(contractAddress);
        } catch (Exception e) {
            // Contract deployment failed — still record investment
            // but note the error
            contractError = "Contract deployment failed: " + e.getMessage();
            System.err.println(contractError);
        }

        // ── AC-6: Deduct wallet balance ───────────────────────
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        // ── AC-5: Create investment record ────────────────────
        Investment investment = new Investment();
        investment.setInvestor(investor);
        investment.setLand(land);
        investment.setAmountInvested(amount);
        investment.setStatus(Investment.InvestmentStatus.ACTIVE);
        investment.setInvestmentDate(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime());
        investment.setContractAddress(contractAddress);
        investment.setContractLink(polygonScanLink);
        investmentRepository.save(investment);

        // ── AC-7: Update land status to FUNDED ────────────────
        land.setIsActive(false);
        land.setProgressPercentage(100);
        landRepository.save(land);

        // ── Build response ────────────────────────────────────
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message",         "Investment successful");
        response.put("investmentId",    investment.getInvestmentId());
        response.put("amountInvested",  amount);
        response.put("walletBalance",   wallet.getBalance());
        response.put("landId",          land.getLandId());
        response.put("landStatus",      "FUNDED");

        if (contractAddress != null) {
            response.put("contractAddress",  contractAddress);
            response.put("contractLink",     polygonScanLink);
            response.put("blockchainStatus", "CONTRACT_DEPLOYED");
        } else {
            response.put("blockchainStatus", "PENDING");
            response.put("blockchainError",  contractError);
        }

        return ResponseEntity.ok(response);
    }

    // ── GET /api/investment/contract/{investmentId} ───────────
    // Returns the blockchain contract link for an investment
    @GetMapping("/contract/{investmentId}")
    @RequiredRole(Role.INVESTOR)
    public ResponseEntity<?> getContractLink(
            @PathVariable Long investmentId,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = extractUserId(authHeader);

        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));

        // Make sure this investment belongs to this investor
        if (!investment.getInvestor().getUserId().equals(userId)) {
            return ResponseEntity.status(403).body("Access denied");
        }

        return ResponseEntity.ok(Map.of(
                "investmentId",   investment.getInvestmentId(),
                "contractAddress", investment.getContractAddress() != null
                        ? investment.getContractAddress() : "Pending",
                "contractLink",   investment.getContractLink()    != null
                        ? investment.getContractLink()    : "Pending",
                "blockchainStatus", investment.getContractAddress() != null
                        ? "DEPLOYED" : "PENDING"
        ));
    }

    // ── Fund request DTO ──────────────────────────────────────
    public record FundRequest(
            Long landId,
            long amount
    ) {}
}
