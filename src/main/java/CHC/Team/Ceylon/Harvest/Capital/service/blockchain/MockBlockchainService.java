package CHC.Team.Ceylon.Harvest.Capital.service.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock blockchain service for local development and testing.
 * Active on all profiles EXCEPT "production".
 *
 * Generates deterministic fake tx hashes — no network calls, no MATIC needed.
 * Swap to real blockchain by running with: --spring.profiles.active=production
 */
@Service
@Profile("!production")
public class MockBlockchainService implements BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(MockBlockchainService.class);

    @Override
    public String networkName() {
        return "MOCK";
    }

    @Override
    public ContractResult createInvestmentContract(
            Long       investorId,
            Long       farmerId,
            Long       landId,
            BigDecimal amountLkr) {

        // Produce a fake but realistic-looking tx hash
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "")
                              + UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        log.info("[MOCK] Investment contract recorded | investorId={} farmerId={} landId={} amount={} tx={}",
                investorId, farmerId, landId, amountLkr, txHash);

        return ContractResult.deployed(txHash, txHash);
    }

    @Override
    public ContractResult recordMilestoneApproval(
            String contractAddress,
            Long   milestoneId,
            int    progressPct) {

        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "")
                              + UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        log.info("[MOCK] Milestone approval recorded | milestoneId={} progress={}% ref={} tx={}",
                milestoneId, progressPct, contractAddress, txHash);

        return ContractResult.ok(txHash);
    }
}
