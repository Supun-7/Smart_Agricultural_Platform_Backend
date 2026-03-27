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
import CHC.Team.Ceylon.Harvest.Capital.service.BlockchainService;
import CHC.Team.Ceylon.Harvest.Capital.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1  A list of available (unfunded) lands is displayed to the investor — tested via InvestorDashboardControllerTest / InvestorDashboardServiceImplTest
// AC-2  The investor can select a land and enter an investment amount — POST /api/investment/fund accepts landId + amount
// AC-3  The system validates the investor's wallet balance before processing
// AC-4  If balance is insufficient, a clear error message is shown and the transaction is blocked
// AC-5  On success, a transaction record is created in the Transaction table
// AC-6  The investor's wallet balance is reduced by the invested amount
// AC-7  The land status is updated to FUNDED in the database
// AC-8  The investment appears on both the investor dashboard and the farmer's funded lands list
@ExtendWith(MockitoExtension.class)
class InvestmentControllerTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private WalletRepository walletRepository;
        @Mock
        private LandRepository landRepository;
        @Mock
        private InvestmentRepository investmentRepository;
        @Mock
        private FarmerApplicationRepository farmerApplicationRepository;
        @Mock
        private JwtUtil jwtUtil;
        @Mock
        private BlockchainService blockchainService;
        @Mock
        private TransactionService transactionService;

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;

        private User investor;
        private Wallet wallet;
        private Land activeLand;

        @BeforeEach
        void setUp() {
                InvestmentController investmentController = new InvestmentController(
                                userRepository,
                                walletRepository,
                                landRepository,
                                investmentRepository,
                                farmerApplicationRepository,
                                jwtUtil,
                                blockchainService,
                                transactionService);

                mockMvc = MockMvcBuilders.standaloneSetup(investmentController).build();
                objectMapper = new ObjectMapper();

                // ── Shared fixtures ───────────────────────────────────────────────────
                investor = new User();
                investor.setUserId(10L);
                investor.setFullName("Sachith Investor");
                investor.setEmail("sachith@invest.lk");
                investor.setRole(Role.INVESTOR);

                wallet = new Wallet();
                wallet.setWalletId(200L);
                wallet.setUser(investor);
                wallet.setBalance(BigDecimal.valueOf(500000.00));
                wallet.setCurrency("LKR");

                activeLand = new Land();
                activeLand.setLandId(101L);
                activeLand.setProjectName("Pepper Estate Phase 1");
                activeLand.setLocation("Kandy");
                activeLand.setTotalValue(BigDecimal.valueOf(800000.00));
                activeLand.setMinimumInvestment(BigDecimal.valueOf(25000.00));
                activeLand.setProgressPercentage(0);
                activeLand.setIsActive(true);
        }

        // ── AC-2 + AC-6 + AC-7: Successful investment reduces wallet and marks land
        // FUNDED ─────────
    @Test
    void fundLand_withSufficientBalance_shouldReturnSuccessAndDeductWallet() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
        when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
        when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));
        when(walletRepository.findByUserUserId(10L)).thenReturn(Optional.of(wallet));
        when(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(blockchainService.deployInvestmentContract(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenReturn("0xABCDEF1234567890");
        when(blockchainService.buildPolygonScanLink("0xABCDEF1234567890"))
                .thenReturn("https://amoy.polygonscan.com/address/0xABCDEF1234567890");

        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment inv = invocation.getArgument(0);
            inv.setInvestmentId(501L);
            return inv;
        });

        String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 100000));

        mockMvc.perform(post("/api/investment/fund")
                        .header("Authorization", "Bearer qa-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // AC-2: endpoint accepts the request and responds 200
                .andExpect(status().isOk())
                // AC-6: walletBalance field confirms deduction
                .andExpect(jsonPath("$.walletBalance").value(400000.00))
                // AC-7: landStatus is FUNDED
                .andExpect(jsonPath("$.landStatus").value("FUNDED"))
                // general success fields
                .andExpect(jsonPath("$.message").value("Investment successful"))
                .andExpect(jsonPath("$.investmentId").value(501))
                .andExpect(jsonPath("$.amountInvested").value(100000))
                .andExpect(jsonPath("$.contractAddress").value("0xABCDEF1234567890"))
                .andExpect(jsonPath("$.blockchainStatus").value("CONTRACT_DEPLOYED"));

        // AC-6: wallet save is called (balance persisted)
        verify(walletRepository).save(wallet);
        // AC-7: land save is called (isActive=false persisted)
        verify(landRepository).save(activeLand);
    }

        // ── AC-3 + AC-4: Insufficient wallet balance is rejected with a clear error
        // ──────────────
        @Test
        void fundLand_withInsufficientBalance_shouldReturn400WithClearErrorMessage() throws Exception {
                wallet.setBalance(BigDecimal.valueOf(10000.00)); // less than the 100 000 requested

                when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
                when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
                when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));
                when(walletRepository.findByUserUserId(10L)).thenReturn(Optional.of(wallet));

                String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 100000));

                mockMvc.perform(post("/api/investment/fund")
                                .header("Authorization", "Bearer qa-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                // AC-4: bad-request status returned
                                .andExpect(status().isBadRequest())
                                // AC-4: clear error message present
                                .andExpect(jsonPath("$.error").value("Insufficient wallet balance"))
                                // AC-4: both required and available amounts are shown
                                .andExpect(jsonPath("$.required").value(100000))
                                .andExpect(jsonPath("$.available").value(10000.00));

                // AC-4: transaction must NOT be created when balance is insufficient
                verify(transactionService, never()).createTransaction(anyLong(), anyLong(), anyString());
                verify(investmentRepository, never()).save(any());
                verify(walletRepository, never()).save(any());
        }

        // ── AC-3: Amount below minimum investment is rejected
        // ──────────────────────────────────
    @Test
    void fundLand_withAmountBelowMinimum_shouldReturn400WithMinimumError() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
        when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
        when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));
        when(walletRepository.findByUserUserId(10L)).thenReturn(Optional.of(wallet));

        // send 5 000 but minimum is 25 000
        String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 5000));

        mockMvc.perform(post("/api/investment/fund")
                        .header("Authorization", "Bearer qa-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Investment amount is below minimum"))
                .andExpect(jsonPath("$.minimum").value(25000.00));

        verify(investmentRepository, never()).save(any());
    }

        // ── AC-7: Attempting to fund an already-funded land is rejected
        // ─────────────────────────
        @Test
        void fundLand_whenLandAlreadyFunded_shouldReturn400WithAlreadyFundedError() throws Exception {
                activeLand.setIsActive(false); // land already funded

                when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
                when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
                when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));

                String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 100000));

                mockMvc.perform(post("/api/investment/fund")
                                .header("Authorization", "Bearer qa-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("This land has already been funded"));

                verify(walletRepository, never()).findByUserUserId(anyLong());
                verify(investmentRepository, never()).save(any());
        }

        // ── AC-5: Transaction record is created on successful investment
        // ────────────────────────
    @Test
    void fundLand_onSuccess_shouldCreateTransactionRecord() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
        when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
        when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));
        when(walletRepository.findByUserUserId(10L)).thenReturn(Optional.of(wallet));
        when(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(blockchainService.deployInvestmentContract(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenReturn("0xCONTRACT");
        when(blockchainService.buildPolygonScanLink("0xCONTRACT"))
                .thenReturn("https://amoy.polygonscan.com/address/0xCONTRACT");

        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment inv = invocation.getArgument(0);
            inv.setInvestmentId(502L);
            return inv;
        });

        String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 75000));

        mockMvc.perform(post("/api/investment/fund")
                        .header("Authorization", "Bearer qa-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // AC-5: TransactionService.createTransaction() must be called once with correct args
        verify(transactionService, times(1))
                .createTransaction(10L, 75000L, "INVESTMENT");
    }

        // ── AC-8: Contract link is retrievable after investment (investor dashboard
        // linkage) ─────
        @Test
        void getContractLink_forOwnInvestment_shouldReturnContractDetails() throws Exception {
                Investment investment = new Investment();
                investment.setInvestmentId(501L);
                investment.setInvestor(investor);
                investment.setLand(activeLand);
                investment.setAmountInvested(BigDecimal.valueOf(100000));
                investment.setStatus(Investment.InvestmentStatus.ACTIVE);
                investment.setInvestmentDate(LocalDateTime.now());
                investment.setContractAddress("0xABCDEF1234567890");
                investment.setContractLink("https://amoy.polygonscan.com/address/0xABCDEF1234567890");

                when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
                when(investmentRepository.findById(501L)).thenReturn(Optional.of(investment));

                mockMvc.perform(get("/api/investment/contract/501")
                                .header("Authorization", "Bearer qa-token"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.investmentId").value(501))
                                .andExpect(jsonPath("$.contractAddress").value("0xABCDEF1234567890"))
                                .andExpect(jsonPath("$.contractLink")
                                                .value("https://amoy.polygonscan.com/address/0xABCDEF1234567890"))
                                .andExpect(jsonPath("$.blockchainStatus").value("DEPLOYED"));
        }

        // ── AC-8: Investor cannot access another investor's contract link
        // ────────────────────────
        @Test
        void getContractLink_forAnotherInvestorsInvestment_shouldReturn403() throws Exception {
                User otherInvestor = new User();
                otherInvestor.setUserId(99L);
                otherInvestor.setFullName("Other Investor");
                otherInvestor.setEmail("other@invest.lk");

                Investment investment = new Investment();
                investment.setInvestmentId(999L);
                investment.setInvestor(otherInvestor); // belongs to a different investor
                investment.setLand(activeLand);
                investment.setAmountInvested(BigDecimal.valueOf(50000));
                investment.setStatus(Investment.InvestmentStatus.ACTIVE);
                investment.setInvestmentDate(LocalDateTime.now());

                when(jwtUtil.extractUserId("qa-token")).thenReturn("10"); // investor 10 requesting
                when(investmentRepository.findById(999L)).thenReturn(Optional.of(investment));

                mockMvc.perform(get("/api/investment/contract/999")
                                .header("Authorization", "Bearer qa-token"))
                                .andExpect(status().isForbidden());
        }

        // ── AC-2: Land not found returns 500 (RuntimeException)
        // ──────────────────────────────────
    @Test
    void fundLand_whenLandDoesNotExist_shouldThrowRuntimeException() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
        when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
        when(landRepository.findById(999L)).thenReturn(Optional.empty());

        String requestBody = objectMapper.writeValueAsString(Map.of("landId", 999, "amount", 100000));

        Exception exception = org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> mockMvc.perform(post("/api/investment/fund")
                                .header("Authorization", "Bearer qa-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
        );
        org.junit.jupiter.api.Assertions.assertTrue(exception.getCause() instanceof RuntimeException);
        org.junit.jupiter.api.Assertions.assertEquals("Land not found", exception.getCause().getMessage());

        verify(investmentRepository, never()).save(any());
    }

        // ── AC-6 + AC-7: Blockchain failure does NOT roll back the investment
        // ─────────────────────
    @Test
    void fundLand_whenBlockchainFails_shouldStillSaveInvestmentWithPendingStatus() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("10");
        when(userRepository.findById(10L)).thenReturn(Optional.of(investor));
        when(landRepository.findById(101L)).thenReturn(Optional.of(activeLand));
        when(walletRepository.findByUserUserId(10L)).thenReturn(Optional.of(wallet));
        when(farmerApplicationRepository.findTopByUserUserIdOrderBySubmittedAtDesc(101L))
                .thenReturn(Optional.empty());
        when(blockchainService.deployInvestmentContract(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Network timeout on Polygon Amoy"));

        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment inv = invocation.getArgument(0);
            inv.setInvestmentId(503L);
            return inv;
        });

        String requestBody = objectMapper.writeValueAsString(Map.of("landId", 101, "amount", 100000));

        mockMvc.perform(post("/api/investment/fund")
                        .header("Authorization", "Bearer qa-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // investment still succeeds — blockchain is non-blocking
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Investment successful"))
                // AC-6: wallet still deducted
                .andExpect(jsonPath("$.walletBalance").value(400000.00))
                // AC-7: land still marked FUNDED
                .andExpect(jsonPath("$.landStatus").value("FUNDED"))
                // blockchain status shows PENDING, not CONTRACT_DEPLOYED
                .andExpect(jsonPath("$.blockchainStatus").value("PENDING"))
                .andExpect(jsonPath("$.blockchainError").value(
                        "Contract deployment failed: Network timeout on Polygon Amoy"));

        // AC-6 + AC-7: core DB writes still happen
        verify(walletRepository).save(wallet);
        verify(landRepository).save(activeLand);
        verify(investmentRepository).save(any(Investment.class));
    }
}