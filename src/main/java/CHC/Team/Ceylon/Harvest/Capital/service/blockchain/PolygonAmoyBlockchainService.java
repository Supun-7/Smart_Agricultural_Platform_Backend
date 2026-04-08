package CHC.Team.Ceylon.Harvest.Capital.service.blockchain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Real Polygon Amoy testnet implementation.
 * Active ONLY when: --spring.profiles.active=production
 *
 * ── How it works ─────────────────────────────────────────────────────────────
 * The CHC system wallet pays ALL gas fees using free Polygon Amoy testnet MATIC.
 * Investors and farmers do NOT need any crypto wallet or MetaMask.
 *
 * When an investor pays, this service sends a self-transaction from the CHC wallet
 * to itself, with the investment metadata embedded in the transaction data field.
 * The resulting tx hash is permanently viewable at:
 *   https://amoy.polygonscan.com/tx/{txHash}
 *
 * ── Getting free MATIC for the CHC system wallet ─────────────────────────────
 * 1. Go to: https://faucet.polygon.technology/
 * 2. Select "Polygon Amoy"
 * 3. Enter the address from blockchain.wallet.address in application.properties
 * 4. Click Submit — you get free testnet MATIC immediately
 *
 * ── Required application.properties entries ──────────────────────────────────
 *   blockchain.rpc.url=https://rpc-amoy.polygon.technology
 *   blockchain.wallet.privateKey=YOUR_CHC_SYSTEM_WALLET_PRIVATE_KEY
 *   blockchain.wallet.address=0xYOUR_CHC_SYSTEM_WALLET_ADDRESS
 *   blockchain.polygonscan.url=https://amoy.polygonscan.com/
 */
@Service
@Profile("production")
public class PolygonAmoyBlockchainService implements BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(PolygonAmoyBlockchainService.class);

    // Polygon Amoy chain ID
    private static final long CHAIN_ID = 80002L;

    // Conservative gas settings — Amoy is very cheap
    private static final BigInteger GAS_LIMIT     = BigInteger.valueOf(100_000L);
    private static final BigInteger GAS_PRICE_WEI = BigInteger.valueOf(30_000_000_000L); // 30 Gwei

    @Value("${blockchain.rpc.url}")
    private String rpcUrl;

    @Value("${blockchain.wallet.privateKey}")
    private String privateKey;

    @Value("${blockchain.wallet.address}")
    private String systemWalletAddress;

    private Web3j       web3j;
    private Credentials credentials;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        log.info("[PolygonAmoy] Connecting to RPC: {}", rpcUrl);
        web3j       = Web3j.build(new HttpService(rpcUrl));
        credentials = Credentials.create(privateKey);
        log.info("[PolygonAmoy] CHC system wallet: {}", credentials.getAddress());
        log.info("[PolygonAmoy] Gas is paid by CHC system wallet — investors/farmers need NO crypto wallet");
    }

    @PreDestroy
    public void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
            log.info("[PolygonAmoy] Web3j connection closed.");
        }
    }

    // ── BlockchainService implementation ──────────────────────────────────────

    @Override
    public String networkName() {
        return "POLYGON_AMOY";
    }

    /**
     * Records an investment agreement on-chain using the CHC system wallet to pay gas.
     *
     * The investor and farmer are identified by their CHC platform user IDs — no
     * crypto wallet addresses needed from either party.
     *
     * The tx is viewable at: https://amoy.polygonscan.com/tx/{txHash}
     */
    @Override
    public ContractResult createInvestmentContract(
            Long       investorId,
            Long       farmerId,
            Long       landId,
            BigDecimal amountLkr) {

        try {
            // Embed CHC platform IDs — no crypto addresses needed from users
            String payload = String.format(
                    "CHC:INVEST:landId=%d:investorId=%d:farmerId=%d:amountLkr=%s:network=POLYGON_AMOY",
                    landId, investorId, farmerId, amountLkr.toPlainString());

            String txHash = sendSystemTransaction(payload);

            log.info("[PolygonAmoy] Investment contract on-chain | landId={} investorId={} farmerId={} tx={}",
                    landId, investorId, farmerId, txHash);

            // On the data-tx pattern, txHash serves as both the tx hash and the contract reference
            return ContractResult.deployed(txHash, txHash);

        } catch (Exception e) {
            log.error("[PolygonAmoy] Failed to record investment | landId={} investorId={}", landId, investorId, e);
            return ContractResult.fail("Blockchain error: " + e.getMessage());
        }
    }

    /**
     * Records a milestone approval on-chain, linked to the original investment tx.
     */
    @Override
    public ContractResult recordMilestoneApproval(
            String contractAddress,
            Long   milestoneId,
            int    progressPct) {

        try {
            String payload = String.format(
                    "CHC:MILESTONE:milestoneId=%d:progress=%d:investmentRef=%s",
                    milestoneId, progressPct, contractAddress);

            String txHash = sendSystemTransaction(payload);

            log.info("[PolygonAmoy] Milestone on-chain | milestoneId={} progress={}% tx={}",
                    milestoneId, progressPct, txHash);

            return ContractResult.ok(txHash);

        } catch (Exception e) {
            log.error("[PolygonAmoy] Failed to record milestone | milestoneId={}", milestoneId, e);
            return ContractResult.fail("Blockchain error: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a signed transaction FROM the CHC system wallet TO itself,
     * with the provided UTF-8 string embedded as transaction data.
     *
     * This is the "data-storage" pattern:
     *  - No MATIC is transferred (value = 0)
     *  - Only gas is consumed (paid by the CHC system wallet)
     *  - The payload is permanently stored on-chain in the tx data field
     *
     * @param data  metadata to embed (e.g. "CHC:INVEST:landId=5:investorId=12:...")
     * @return      the transaction hash
     */
    private String sendSystemTransaction(String data) throws Exception {

        // 1. Get nonce for the CHC system wallet
        EthGetTransactionCount nonceResponse = web3j
                .ethGetTransactionCount(systemWalletAddress, DefaultBlockParameterName.LATEST)
                .send();
        BigInteger nonce = nonceResponse.getTransactionCount();

        // 2. Encode data as hex
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        String dataHex   = Numeric.toHexString(dataBytes);

        // 3. Build transaction — value=0, from system wallet to itself
        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce,
                GAS_PRICE_WEI,
                GAS_LIMIT,
                systemWalletAddress,   // to = self (data-storage pattern)
                BigInteger.ZERO,       // value = 0 MATIC transferred
                dataHex
        );

        // 4. Sign with CHC system wallet private key
        byte[] signedMessage = TransactionEncoder.signMessage(rawTx, CHAIN_ID, credentials);
        String hexValue      = Numeric.toHexString(signedMessage);

        // 5. Broadcast to Polygon Amoy
        EthSendTransaction sendResponse = web3j.ethSendRawTransaction(hexValue).send();

        if (sendResponse.hasError()) {
            throw new RuntimeException(sendResponse.getError().getMessage());
        }

        String txHash = sendResponse.getTransactionHash();

        // 6. Wait for mining (up to 30 seconds)
        waitForReceipt(txHash);

        return txHash;
    }

    /**
     * Polls until the transaction is mined, up to ~30 seconds.
     */
    private void waitForReceipt(String txHash) throws Exception {
        int attempts = 30;
        while (attempts-- > 0) {
            Thread.sleep(1_000);
            EthGetTransactionReceipt receiptResponse =
                    web3j.ethGetTransactionReceipt(txHash).send();

            if (receiptResponse.getTransactionReceipt().isPresent()) {
                var receipt = receiptResponse.getTransactionReceipt().get();
                if ("0x0".equals(receipt.getStatus())) {
                    throw new RuntimeException("Transaction reverted on-chain: " + txHash);
                }
                log.debug("[PolygonAmoy] Tx mined in block {} | hash={}", receipt.getBlockNumber(), txHash);
                return;
            }
        }
        // Submitted but not yet mined — still valid, will appear eventually
        log.warn("[PolygonAmoy] Tx not yet mined after 30s (still valid) | hash={}", txHash);
    }
}
