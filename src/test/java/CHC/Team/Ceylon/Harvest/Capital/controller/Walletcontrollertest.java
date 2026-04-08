package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.*;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// AC-1:  POST /api/investor/wallet/deposit accepts a positive amount and returns 200.
// AC-2:  Response body reflects updated balance after deposit.
// AC-3:  Response contains ledger details (gateway, reference, createdAt).
// AC-4:  POST /api/investor/wallet/withdraw accepts an amount within balance.
// AC-5:  Withdrawal exceeding balance returns 400 with a clear error.
// AC-6:  Successful withdrawal response contains reduced balance and ledger data.
// AC-7:  Zero or negative deposit/withdrawal amounts return 400.
// AC-8:  GET /api/investor/wallet returns current balance and full ledger history.
@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private JwtUtil jwtUtil;

    private MockMvc       mockMvc;
    private ObjectMapper  objectMapper;

    private static final Long   USER_ID       = 42L;
    private static final String VALID_TOKEN   = "valid-jwt-token";
    private static final String AUTH_HEADER   = "Bearer " + VALID_TOKEN;
    private static final String CURRENCY      = "LKR";

    @BeforeEach
    void setUp() {
        WalletController walletController = new WalletController(walletService, jwtUtil);
        mockMvc      = MockMvcBuilders.standaloneSetup(walletController)
                                      .setControllerAdvice(new GlobalExceptionHandler())
                                      .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Default JWT stub — extract user ID from every token
        given(jwtUtil.extractUserId(VALID_TOKEN)).willReturn(String.valueOf(USER_ID));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/investor/wallet  —  AC-8
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-8: GET /api/investor/wallet returns 200 with balance, currency, and history.
    @Test
    void getWallet_shouldReturn200WithBalanceAndLedgerHistory() throws Exception {
        LedgerEntryDto depositEntry = new LedgerEntryDto(
                1L, "DEPOSIT", BigDecimal.valueOf(500.00), BigDecimal.valueOf(1500.00),
                "MOCK", "MOCK-DEP-001", LocalDateTime.now());

        LedgerEntryDto withdrawEntry = new LedgerEntryDto(
                2L, "WITHDRAWAL", BigDecimal.valueOf(200.00), BigDecimal.valueOf(1300.00),
                "MOCK", "MOCK-WDR-001", LocalDateTime.now());

        WalletResponse walletResponse = new WalletResponse(
                10L, BigDecimal.valueOf(1300.00), CURRENCY,
                List.of(withdrawEntry, depositEntry));

        given(walletService.getWallet(USER_ID)).willReturn(walletResponse);

        mockMvc.perform(get("/api/investor/wallet")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())

                // AC-8: balance and currency present
                .andExpect(jsonPath("$.walletId").value(10))
                .andExpect(jsonPath("$.balance").value(1300.00))
                .andExpect(jsonPath("$.currency").value(CURRENCY))

                // AC-8: history list contains both entries
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.history[0].amount").value(200.00))
                .andExpect(jsonPath("$.history[0].balanceAfter").value(1300.00))
                .andExpect(jsonPath("$.history[0].gatewayReference").value("MOCK-WDR-001"))
                .andExpect(jsonPath("$.history[1].transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.history[1].amount").value(500.00));

        verify(walletService).getWallet(USER_ID);
    }

    // AC-8: GET /api/investor/wallet returns an empty history array when no transactions exist.
    @Test
    void getWallet_shouldReturnEmptyHistoryWhenNoTransactions() throws Exception {
        WalletResponse emptyWallet = new WalletResponse(
                10L, BigDecimal.ZERO, CURRENCY, List.of());

        given(walletService.getWallet(USER_ID)).willReturn(emptyWallet);

        mockMvc.perform(get("/api/investor/wallet")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.history.length()").value(0));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/investor/wallet/deposit  —  AC-1, AC-2, AC-3, AC-7
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-1: POST /deposit with a positive amount returns HTTP 200.
    @Test
    void deposit_shouldReturn200ForPositiveAmount() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "DEPOSIT", BigDecimal.valueOf(500.00), BigDecimal.valueOf(1500.00));

        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionType").value("DEPOSIT"));

        verify(walletService).deposit(eq(USER_ID), any(BigDecimal.class));
    }

    // AC-2: Response body contains the updated balance after deposit.
    @Test
    void deposit_responseShouldContainUpdatedBalance() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "DEPOSIT", BigDecimal.valueOf(300.00), BigDecimal.valueOf(1300.00));

        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(1300.00))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.currency").value(CURRENCY));
    }

    // AC-3: Deposit response includes gateway name, reference, and a timestamp.
    @Test
    void deposit_responseShouldContainLedgerDetails() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "DEPOSIT", BigDecimal.valueOf(100.00), BigDecimal.valueOf(1100.00));

        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway").value("MOCK"))
                .andExpect(jsonPath("$.gatewayReference").value("MOCK-DEP-TEST"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    // AC-7: Depositing zero returns HTTP 400 (BadRequestException from service).
    @Test
    void deposit_shouldReturn400ForZeroAmount() throws Exception {
        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new BadRequestException("Deposit amount must be greater than zero."));

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    // AC-7: Depositing a negative value returns HTTP 400.
    @Test
    void deposit_shouldReturn400ForNegativeAmount() throws Exception {
        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new BadRequestException("Deposit amount must be greater than zero."));

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -50.00}"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/investor/wallet/withdraw  —  AC-4, AC-5, AC-6, AC-7
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-4: POST /withdraw with an amount within balance returns HTTP 200.
    @Test
    void withdraw_shouldReturn200ForValidAmount() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "WITHDRAWAL", BigDecimal.valueOf(400.00), BigDecimal.valueOf(600.00));

        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 400.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionType").value("WITHDRAWAL"));

        verify(walletService).withdraw(eq(USER_ID), any(BigDecimal.class));
    }

    // AC-6: Successful withdrawal response contains the reduced new balance.
    @Test
    void withdraw_responseShouldContainReducedBalance() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "WITHDRAWAL", BigDecimal.valueOf(250.00), BigDecimal.valueOf(750.00));

        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(750.00))
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    // AC-6: Withdrawal response includes gateway name, reference, and timestamp.
    @Test
    void withdraw_responseShouldContainLedgerDetails() throws Exception {
        TransactionResponse txResponse = buildTxResponse(
                "WITHDRAWAL", BigDecimal.valueOf(100.00), BigDecimal.valueOf(900.00));

        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willReturn(txResponse);

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateway").value("MOCK"))
                .andExpect(jsonPath("$.gatewayReference").value("MOCK-DEP-TEST"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    // AC-5: Withdrawal exceeding balance returns HTTP 400 with a clear error message.
    @Test
    void withdraw_shouldReturn400WhenExceedingBalance() throws Exception {
        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new BadRequestException(
                        "Insufficient balance. Available: LKR 1000.00, requested: 5000.00."));

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000.00}"))
                .andExpect(status().isBadRequest());
    }

    // AC-7: Withdrawing zero returns HTTP 400.
    @Test
    void withdraw_shouldReturn400ForZeroAmount() throws Exception {
        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new BadRequestException("Withdrawal amount must be greater than zero."));

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    // AC-7: Withdrawing a negative value returns HTTP 400.
    @Test
    void withdraw_shouldReturn400ForNegativeAmount() throws Exception {
        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new BadRequestException("Withdrawal amount must be greater than zero."));

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -100.00}"))
                .andExpect(status().isBadRequest());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Wallet not found guard — all three endpoints
    // ═══════════════════════════════════════════════════════════════════════════

    // getWallet returns 404 when no wallet exists for the authenticated investor.
    @Test
    void getWallet_shouldReturn404WhenWalletNotFound() throws Exception {
        given(walletService.getWallet(USER_ID))
                .willThrow(new ResourceNotFoundException("Wallet not found for investor: " + USER_ID));

        mockMvc.perform(get("/api/investor/wallet")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // deposit returns 404 when no wallet exists for the authenticated investor.
    @Test
    void deposit_shouldReturn404WhenWalletNotFound() throws Exception {
        given(walletService.deposit(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new ResourceNotFoundException("Wallet not found for investor: " + USER_ID));

        mockMvc.perform(post("/api/investor/wallet/deposit")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isNotFound());
    }

    // withdraw returns 404 when no wallet exists for the authenticated investor.
    @Test
    void withdraw_shouldReturn404WhenWalletNotFound() throws Exception {
        given(walletService.withdraw(eq(USER_ID), any(BigDecimal.class)))
                .willThrow(new ResourceNotFoundException("Wallet not found for investor: " + USER_ID));

        mockMvc.perform(post("/api/investor/wallet/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isNotFound());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper builder
    // ═══════════════════════════════════════════════════════════════════════════

    private TransactionResponse buildTxResponse(String type,
                                                BigDecimal amount,
                                                BigDecimal newBalance) {
        return new TransactionResponse(
                type, amount, newBalance, CURRENCY,
                "MOCK", "MOCK-DEP-TEST", LocalDateTime.now());
    }
}