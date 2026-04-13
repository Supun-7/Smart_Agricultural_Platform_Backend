package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.investment.InvestRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.investment.InvestResponse;
import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LedgerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestmentService;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorRoiService;
import CHC.Team.Ceylon.Harvest.Capital.service.blockchain.BlockchainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InvestmentServiceImpl implements InvestmentService {

    private final UserRepository       userRepository;
    private final LandRepository       landRepository;
    private final WalletRepository     walletRepository;
    private final LedgerRepository     ledgerRepository;
    private final InvestmentRepository investmentRepository;
    private final BlockchainService    blockchainService;   // injected: mock or real
    private final InvestorRoiService   investorRoiService;

    public InvestmentServiceImpl(UserRepository userRepository,
                                 LandRepository landRepository,
                                 WalletRepository walletRepository,
                                 LedgerRepository ledgerRepository,
                                 InvestmentRepository investmentRepository,
                                 BlockchainService blockchainService,
                                 InvestorRoiService investorRoiService) {
        this.userRepository       = userRepository;
        this.landRepository       = landRepository;
        this.walletRepository     = walletRepository;
        this.ledgerRepository     = ledgerRepository;
        this.investmentRepository = investmentRepository;
        this.blockchainService    = blockchainService;
        this.investorRoiService   = investorRoiService;
    }

    @Override
    @Transactional
    public InvestResponse invest(Long investorId, Long landId, InvestRequest request) {

        BigDecimal amount = request.amount();

        // ── Validate amount ───────────────────────────────────────────────────
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Investment amount must be greater than zero.");
        }

        // ── Resolve investor ──────────────────────────────────────────────────
        User investor = userRepository.findById(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found: " + investorId));

        // ── KYC check ─────────────────────────────────────────────────────────
        if (investor.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new BadRequestException(
                    "KYC verification required before investing. Please complete your KYC first.");
        }

        // ── Resolve land ──────────────────────────────────────────────────────
        Land land = landRepository.findById(landId)
                .orElseThrow(() -> new ResourceNotFoundException("Land not found: " + landId));

        if (!Boolean.TRUE.equals(land.getIsActive())) {
            throw new BadRequestException("This land project is no longer accepting investments.");
        }

        // ── Minimum investment check ──────────────────────────────────────────
        if (amount.compareTo(land.getMinimumInvestment()) < 0) {
            throw new BadRequestException(
                    "Minimum investment for this project is LKR " +
                    land.getMinimumInvestment() + ".");
        }

        // ── Resolve CHC wallet ────────────────────────────────────────────────
        Wallet wallet = walletRepository.findByUserUserId(investorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for investor: " + investorId));

        // ── Sufficient balance check ──────────────────────────────────────────
        if (amount.compareTo(wallet.getBalance()) > 0) {
            throw new BadRequestException(
                    "Insufficient wallet balance. Available: " +
                    wallet.getCurrency() + " " + wallet.getBalance() +
                    ". Please deposit funds first.");
        }

        // ── Debit CHC wallet ──────────────────────────────────────────────────
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // ── Write ledger entry ────────────────────────────────────────────────
        String ref = "INV-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 12);
        Ledger ledgerEntry = new Ledger(
                wallet,
                Ledger.TransactionType.INVESTMENT,
                amount,
                newBalance,
                blockchainService.networkName(),
                ref);
        ledgerRepository.save(ledgerEntry);

        // ── Deploy investment contract on Polygon Amoy ────────────────────────
        // The CHC system wallet pays all gas — investors & farmers need NO crypto wallet.
        // We pass CHC platform user IDs; no Ethereum addresses are needed from users.
        Long farmerId = land.getFarmerUser() != null ? land.getFarmerUser().getUserId() : 0L;

        BlockchainService.ContractResult chain =
                blockchainService.createInvestmentContract(investorId, farmerId, land.getLandId(), amount);

        // If blockchain call fails, the investment is still recorded in DB.
        // The error is visible in the response so it can be retried later.
        String txHash       = chain.success() ? chain.txHash()         : "BLOCKCHAIN_ERROR:" + chain.errorMessage();
        String contractAddr = chain.success() ? chain.contractAddress() : "PENDING";

        // ── Persist investment with on-chain references ───────────────────────
        Investment investment = new Investment();
        investment.setInvestor(investor);
        investment.setLand(land);
        investment.setAmountInvested(amount);
        investment.setStatus(Investment.InvestmentStatus.ACTIVE);
        investment.setInvestmentDate(LocalDateTime.now());
        investment.setBlockchainTxHash(txHash);
        investment.setContractAddress(contractAddr);
        investmentRepository.save(investment);
        investorRoiService.recordSnapshot(investment, investment.getInvestmentDate().toLocalDate());

        return new InvestResponse(
                investment.getInvestmentId(),
                land.getLandId(),
                land.getProjectName(),
                land.getLocation(),
                land.getCropType(),
                land.getSizeAcres(),
                land.getFarmerUser() != null ? land.getFarmerUser().getFullName() : null,
                amount,
                newBalance,
                wallet.getCurrency(),
                ref,
                txHash,
                contractAddr,
                blockchainService.networkName(),
                investment.getInvestmentDate());
    }
}
