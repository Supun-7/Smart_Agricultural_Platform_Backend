package CHC.Team.Ceylon.Harvest.Capital.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.web3j.utils.Convert;
import org.web3j.protocol.core.DefaultBlockParameterName;
import java.math.BigDecimal;
import org.web3j.protocol.core.methods.response.EthGetBalance;

@Service
public class BlockchainService {

    // ── Polygon Amoy Testnet chain ID ───────────────────────
    private static final long CHAIN_ID = 80002L;

    // ── Contract bytecode from Remix compilation ──────────────
    private static final String BYTECODE = "608060405234801561000f575f5ffd5b50604051611c7b380380611c7b8339818101604052810190610031919061034b565b875f908161003f919061068f565b50866001908161004f919061068f565b50856002908161005f919061068f565b50846003908161006f919061068f565b50836004908161007f919061068f565b50826005819055508160068190555042600781905550806008819055506040518060400160405280601681526020017f4365796c6f6e2048617276657374204361706974616c00000000000000000000815250600990816100e0919061068f565b506040518060400160405280600681526020017f4143544956450000000000000000000000000000000000000000000000000000815250600a9081610125919061068f565b5033600b5f6101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055506001600b60146101000a81548160ff0219169083151502179055507ff3017d264fdce4819f7ab82954d312d607a635ad0fbc5efb530d087e77a3c2f3888785846040516101b694939291906107b5565b60405180910390a15050505050505050610806565b5f604051905090565b5f5ffd5b5f5ffd5b5f5ffd5b5f5ffd5b5f601f19601f8301169050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52604160045260245ffd5b61022a826101e4565b810181811067ffffffffffffffff82111715610249576102486101f4565b5b80604052505050565b5f61025b6101cb565b90506102678282610221565b919050565b5f67ffffffffffffffff821115610286576102856101f4565b5b61028f826101e4565b9050602081019050919050565b8281835e5f83830152505050565b5f6102bc6102b78461026c565b610252565b9050828152602081018484840111156102d8576102d76101e0565b5b6102e384828561029c565b509392505050565b5f82601f8301126102ff576102fe6101dc565b5b815161030f8482602086016102aa565b91505092915050565b5f819050919050565b61032a81610318565b8114610334575f5ffd5b50565b5f8151905061034581610321565b92915050565b5f5f5f5f5f5f5f5f610100898b031215610368576103676101d4565b5b5f89015167ffffffffffffffff811115610385576103846101d8565b5b6103918b828c016102eb565b985050602089015167ffffffffffffffff8111156103b2576103b16101d8565b5b6103be8b828c016102eb565b975050604089015167ffffffffffffffff8111156103df576103de6101d8565b5b6103eb8b828c016102eb565b965050606089015167ffffffffffffffff81111561040c5761040b6101d8565b5b6104188b828c016102eb565b955050608089015167ffffffffffffffff811115610439576104386101d8565b5b6104458b828c016102eb565b94505060a06104568b828c01610337565b93505060c06104678b828c01610337565b92505060e06104788b828c01610337565b9150509295985092959890939650565b5f81519050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602260045260245ffd5b5f60028204905060018216806104d657607f821691505b6020821081036104e9576104e8610492565b5b50919050565b5f819050815f5260205f209050919050565b5f6020601f8301049050919050565b5f82821b905092915050565b5f6008830261054b7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff82610510565b6105558683610510565b95508019841693508086168417925050509392505050565b5f819050919050565b5f61059061058b61058684610318565b61056d565b610318565b9050919050565b5f819050919050565b6105a983610576565b6105bd6105b582610597565b84845461051c565b825550505050565b5f5f905090565b6105d46105c5565b6105df8184846105a0565b505050565b5b81811015610602576105f75f826105cc565b6001810190506105e5565b5050565b601f82111561064757610618816104ef565b61062184610501565b81016020851015610630578190505b61064461063c85610501565b8301826105e4565b50505b505050565b5f82821c905092915050565b5f6106675f198460080261064c565b1980831691505092915050565b5f61067f8383610658565b9150826002028217905092915050565b61069882610488565b67ffffffffffffffff8111156106b1576106b06101f4565b5b6106bb82546104bf565b6106c6828285610606565b5f60209050601f8311600181146106f7575f84156106e5578287015190505b6106ef8582610674565b865550610756565b601f198416610705866104ef565b5f5b8281101561072c57848901518255600182019150602085019450602081019050610707565b868310156107495784890151610745601f891682610658565b8355505b6001600288020188555050505b505050505050565b5f82825260208201905092915050565b5f61077882610488565b610782818561075e565b935061079281856020860161029c565b61079b816101e4565b840191505092915050565b6107af81610318565b82525050565b5f6080820190508181035f8301526107cd818761076e565b905081810360208301526107e1818661076e565b90506107f060408301856107a6565b6107fd60608301846107a6565b95945050505050565b611468806108135f395ff3fe";

    @Value("${blockchain.rpc.url}")
    private String rpcUrl;

    @Value("${blockchain.wallet.privateKey}")
    private String privateKey;

    @Value("${blockchain.polygonscan.url}")
    private String polygonscanUrl;

    private Web3j web3j;
    private Credentials credentials;

    @PostConstruct
    public void init() {
        web3j = Web3j.build(new HttpService(rpcUrl));
        credentials = Credentials.create(privateKey);
        System.out.println("Blockchain service initialized");
        System.out.println("Platform wallet: " + credentials.getAddress());
    }

    // Before deploy the contract checking whether there is enough gas to create the
    // contract
    public BigDecimal getSystemWalletBalance() throws Exception {
        EthGetBalance balanceResponse = web3j.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send();
        BigDecimal balanceInEther = Convert.fromWei(new BigDecimal(balanceResponse.getBalance()), Convert.Unit.ETHER);
        System.out.println("System wallet balance: " + balanceInEther + " ETH");
        return balanceInEther;
    }

    // ── Deploy CHCInvestment contract ─────────────────────────
    // Called when investor successfully pays for a land
    // Returns the contract address on Polygon Amoy
    public String deployInvestmentContract(
            String investorName,
            String investorEmail,
            String farmerName,
            String farmLocation,
            String cropTypes,
            long investmentAmountLKR,
            long expectedReturnPercent,
            long harvestDateTimestamp) throws Exception {

        // Check system wallet balance
        BigDecimal balance = getSystemWalletBalance();
        BigDecimal minRequired = BigDecimal.valueOf(0.01); // 0.01 ETH minimum for gas
        if (balance.compareTo(minRequired) < 0) {
            throw new Exception("Insufficient funds in system wallet to deploy contract. Balance: " + balance + " ETH");
        }

        // Encode constructor parameters
        List<Type> constructorParams = Arrays.<Type>asList(
                new Utf8String(investorName),
                new Utf8String(investorEmail),
                new Utf8String(farmerName),
                new Utf8String(farmLocation),
                new Utf8String(cropTypes),
                new Uint256(BigInteger.valueOf(investmentAmountLKR)),
                new Uint256(BigInteger.valueOf(expectedReturnPercent)),
                new Uint256(BigInteger.valueOf(harvestDateTimestamp)));

        String encodedConstructor = FunctionEncoder.encodeConstructor(constructorParams);
        String deployData = BYTECODE + encodedConstructor;

        // 3️⃣ Deploy contract (your existing logic)
        RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);

        EthSendTransaction txResponse = txManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(3_000_000L),
                null,
                deployData,
                BigInteger.ZERO);

        if (txResponse.hasError()) {
            throw new Exception("Contract deployment failed: " + txResponse.getError().getMessage());
        }

        String txHash = txResponse.getTransactionHash();
        System.out.println("Contract deployment tx: " + txHash);

        // Wait for receipt to get contract address
        String contractAddress = waitForContractAddress(txHash);
        System.out.println("Contract deployed at: " + contractAddress);

        return contractAddress;
    }

    private String waitForContractAddress(String txHash) throws Exception {
        int attempts = 40;
        int sleepDuration = 3000;
        
        for (int i = 0; i < attempts; i++) {
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            Optional<TransactionReceipt> receiptOptional = receiptResponse.getTransactionReceipt();
            
            if (receiptOptional.isPresent()) {
                TransactionReceipt receipt = receiptOptional.get();
                if (receipt.getContractAddress() != null) {
                    return receipt.getContractAddress();
                }
            }
            Thread.sleep(sleepDuration);
        }
        
        throw new Exception("Transaction receipt not found after waiting for " + txHash);
    }

    public String buildPolygonScanLink(String contractAddress) {
        if (contractAddress == null || contractAddress.isEmpty()) {
            return null;
        }
        return "https://amoy.polygonscan.com/address/" + contractAddress;
    }
}
