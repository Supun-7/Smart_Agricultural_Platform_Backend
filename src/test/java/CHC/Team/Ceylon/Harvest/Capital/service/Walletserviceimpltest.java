package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.*;
import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.LedgerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.WalletServiceImpl;
import CHC.Team.Ceylon.Harvest.Capital.service.payment.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// AC-1:  Investor can deposit any positive amount into their wallet.
// AC-2:  On successful deposit, wallet balance updates immediately in the DB.
// AC-3:  A deposit transaction record is created in the Ledger table.
// AC-4:  Investor can submit a withdrawal request up to their current balance.
// AC-5:  Withdrawal exceeding current balance is blocked with a clear error.
// AC-6:  On successful withdrawal, balance is reduced and a ledger record is created.
// AC-7:  Zero or negative deposit/withdrawal amounts are rejected.
// AC-8:  getWallet() returns current balance and full ledger history for QA validation.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet wallet;
    private static final Long   USER_ID  = 1L;
    private static final String CURRENCY = "LKR";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUserId(USER_ID);

        wallet = new Wallet(10L, user, BigDecimal.valueOf(1000.00), CURRENCY, LocalDateTime.now());

        // Default mock stubs — individual tests may override
        given(walletRepository.findByUserUserId(USER_ID)).willReturn(Optional.of(wallet));
        given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));

        given(ledgerRepository.save(any(Ledger.class))).willAnswer(inv -> {
            Ledger ledger = inv.getArgument(0);
            ledger.setCreatedAt(LocalDateTime.now());
            return ledger;
        });

        given(paymentGateway.gatewayName()).willReturn("MOCK");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-1 + AC-2 + AC-3  —  Deposit happy path
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-1: A standard positive deposit is accepted without errors.
    @Test
    void deposit_shouldAcceptPositiveAmount() {
        given(paymentGateway.deposit(USER_ID, BigDecimal.valueOf(500.00), CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-DEP-001"));

        TransactionResponse response = walletService.deposit(USER_ID, BigDecimal.valueOf(500.00));

        assertThat(response).isNotNull();
        assertThat(response.transactionType()).isEqualTo("DEPOSIT");
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    // AC-2: After deposit, the returned newBalance equals previous balance + deposited amount.
    @Test
    void deposit_shouldUpdateWalletBalanceImmediately() {
        BigDecimal depositAmount  = BigDecimal.valueOf(500.00);
        BigDecimal expectedBalance = BigDecimal.valueOf(1500.00);   // 1000 + 500

        given(paymentGateway.deposit(USER_ID, depositAmount, CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-DEP-002"));

        TransactionResponse response = walletService.deposit(USER_ID, depositAmount);

        assertThat(response.newBalance()).isEqualByComparingTo(expectedBalance);

        // Verify the wallet entity was persisted with the updated balance
        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo(expectedBalance);
    }

    // AC-3: A DEPOSIT ledger record is saved after every successful deposit.
    @Test
    void deposit_shouldCreateDepositLedgerRecord() {
        given(paymentGateway.deposit(USER_ID, BigDecimal.valueOf(300.00), CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-DEP-003"));

        walletService.deposit(USER_ID, BigDecimal.valueOf(300.00));

        ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());

        Ledger saved = ledgerCaptor.getValue();
        assertThat(saved.getTransactionType()).isEqualTo(Ledger.TransactionType.DEPOSIT);
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(1300.00)); // 1000 + 300
    }

    // AC-3: Ledger record contains the gateway name and gateway reference returned by the gateway.
    @Test
    void deposit_shouldRecordGatewayNameAndReferenceInLedger() {
        given(paymentGateway.deposit(USER_ID, BigDecimal.valueOf(100.00), CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-DEP-REF-XYZ"));

        walletService.deposit(USER_ID, BigDecimal.valueOf(100.00));

        ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());

        assertThat(ledgerCaptor.getValue().getGateway()).isEqualTo("MOCK");
        assertThat(ledgerCaptor.getValue().getGatewayReference()).isEqualTo("MOCK-DEP-REF-XYZ");
    }

    // AC-2 + AC-3: TransactionResponse returns correct currency, gateway, and reference.
    @Test
    void deposit_shouldReturnCompleteTransactionResponse() {
        given(paymentGateway.deposit(USER_ID, BigDecimal.valueOf(200.00), CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-DEP-FULL"));

        TransactionResponse response = walletService.deposit(USER_ID, BigDecimal.valueOf(200.00));

        assertThat(response.currency()).isEqualTo(CURRENCY);
        assertThat(response.gateway()).isEqualTo("MOCK");
        assertThat(response.gatewayReference()).isEqualTo("MOCK-DEP-FULL");
        assertThat(response.createdAt()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-7  —  Deposit validation: zero and negative amounts
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-7: Depositing exactly zero is rejected with BadRequestException.
    @Test
    void deposit_shouldRejectZeroAmount() {
        assertThatThrownBy(() -> walletService.deposit(USER_ID, BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");

        verifyNoInteractions(paymentGateway);
        verify(walletRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    // AC-7: A negative deposit amount is rejected immediately.
    @Test
    void deposit_shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> walletService.deposit(USER_ID, BigDecimal.valueOf(-50.00)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");

        verifyNoInteractions(paymentGateway);
        verify(walletRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    // AC-7: A null deposit amount is also rejected as invalid input.
    @Test
    void deposit_shouldRejectNullAmount() {
        assertThatThrownBy(() -> walletService.deposit(USER_ID, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-4 + AC-6  —  Withdrawal happy path
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-4: Withdrawing an amount equal to the exact wallet balance is permitted.
    @Test
    void withdraw_shouldAllowWithdrawalUpToFullBalance() {
        BigDecimal fullBalance = BigDecimal.valueOf(1000.00);

        given(paymentGateway.withdraw(USER_ID, fullBalance, CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-WDR-001"));

        TransactionResponse response = walletService.withdraw(USER_ID, fullBalance);

        assertThat(response.transactionType()).isEqualTo("WITHDRAWAL");
        assertThat(response.newBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // AC-4: Withdrawing a partial amount within the available balance is accepted.
    @Test
    void withdraw_shouldAcceptPartialWithdrawal() {
        BigDecimal withdrawAmount   = BigDecimal.valueOf(400.00);
        BigDecimal expectedBalance  = BigDecimal.valueOf(600.00);  // 1000 - 400

        given(paymentGateway.withdraw(USER_ID, withdrawAmount, CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-WDR-002"));

        TransactionResponse response = walletService.withdraw(USER_ID, withdrawAmount);

        assertThat(response.newBalance()).isEqualByComparingTo(expectedBalance);
    }

    // AC-6: After withdrawal, the wallet balance in the DB is correctly reduced.
    @Test
    void withdraw_shouldReduceWalletBalanceInDatabase() {
        BigDecimal withdrawAmount  = BigDecimal.valueOf(250.00);
        BigDecimal expectedBalance = BigDecimal.valueOf(750.00);  // 1000 - 250

        given(paymentGateway.withdraw(USER_ID, withdrawAmount, CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-WDR-003"));

        walletService.withdraw(USER_ID, withdrawAmount);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo(expectedBalance);
    }

    // AC-6: A WITHDRAWAL ledger record is saved after every successful withdrawal.
    @Test
    void withdraw_shouldCreateWithdrawalLedgerRecord() {
        BigDecimal withdrawAmount  = BigDecimal.valueOf(200.00);
        BigDecimal expectedBalance = BigDecimal.valueOf(800.00);  // 1000 - 200

        given(paymentGateway.withdraw(USER_ID, withdrawAmount, CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-WDR-004"));

        walletService.withdraw(USER_ID, withdrawAmount);

        ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());

        Ledger saved = ledgerCaptor.getValue();
        assertThat(saved.getTransactionType()).isEqualTo(Ledger.TransactionType.WITHDRAWAL);
        assertThat(saved.getAmount()).isEqualByComparingTo(withdrawAmount);
        assertThat(saved.getBalanceAfter()).isEqualByComparingTo(expectedBalance);
    }

    // AC-6: Withdrawal ledger record contains the correct gateway name and reference.
    @Test
    void withdraw_shouldRecordGatewayDetailsInLedger() {
        given(paymentGateway.withdraw(USER_ID, BigDecimal.valueOf(100.00), CURRENCY))
                .willReturn(PaymentGateway.GatewayResult.ok("MOCK-WDR-REF-ABC"));

        walletService.withdraw(USER_ID, BigDecimal.valueOf(100.00));

        ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());

        assertThat(ledgerCaptor.getValue().getGateway()).isEqualTo("MOCK");
        assertThat(ledgerCaptor.getValue().getGatewayReference()).isEqualTo("MOCK-WDR-REF-ABC");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-5  —  Insufficient balance check
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-5: Withdrawal exceeding balance by one unit is rejected with a descriptive error.
    @Test
    void withdraw_shouldBlockWithdrawalExceedingBalance() {
        BigDecimal overAmount = BigDecimal.valueOf(1000.01);   // balance is 1000.00

        assertThatThrownBy(() -> walletService.withdraw(USER_ID, overAmount))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient balance");

        verifyNoInteractions(paymentGateway);
        verify(walletRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    // AC-5: Error message includes the available balance and the requested amount.
    @Test
    void withdraw_insufficientBalance_errorShouldContainAvailableAndRequestedAmounts() {
        BigDecimal overAmount = BigDecimal.valueOf(5000.00);

        BadRequestException ex = catchThrowableOfType(
                () -> walletService.withdraw(USER_ID, overAmount),
                BadRequestException.class);

        assertThat(ex.getMessage())
                .contains("1000")    // available balance
                .contains("5000");   // requested amount
    }

    // AC-5: Withdrawal is not processed when balance is zero.
    @Test
    void withdraw_shouldBlockWithdrawalWhenBalanceIsZero() {
        wallet.setBalance(BigDecimal.ZERO);

        assertThatThrownBy(() -> walletService.withdraw(USER_ID, BigDecimal.valueOf(1.00)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-7  —  Withdrawal validation: zero and negative amounts
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-7: Withdrawing exactly zero is rejected before balance check or gateway call.
    @Test
    void withdraw_shouldRejectZeroAmount() {
        assertThatThrownBy(() -> walletService.withdraw(USER_ID, BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");

        verifyNoInteractions(paymentGateway);
        verify(walletRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    // AC-7: Negative withdrawal amount is rejected.
    @Test
    void withdraw_shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> walletService.withdraw(USER_ID, BigDecimal.valueOf(-100.00)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    // AC-7: Null withdrawal amount is treated as invalid and rejected.
    @Test
    void withdraw_shouldRejectNullAmount() {
        assertThatThrownBy(() -> walletService.withdraw(USER_ID, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC-8  —  getWallet: balance + ledger history for QA
    // ═══════════════════════════════════════════════════════════════════════════

    // AC-8: getWallet returns the current balance and currency of the investor's wallet.
    @Test
    void getWallet_shouldReturnCurrentBalanceAndCurrency() {
        given(ledgerRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of());

        WalletResponse response = walletService.getWallet(USER_ID);

        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(response.currency()).isEqualTo(CURRENCY);
        assertThat(response.walletId()).isEqualTo(10L);
    }

    // AC-8: getWallet returns the full ledger history ordered newest-first.
    @Test
    void getWallet_shouldReturnLedgerHistoryWithAllFields() {
        Ledger depositEntry    = buildLedgerEntry(1L, Ledger.TransactionType.DEPOSIT,
                BigDecimal.valueOf(500.00), BigDecimal.valueOf(1500.00),
                LocalDateTime.now().minusMinutes(10));

        Ledger withdrawalEntry = buildLedgerEntry(2L, Ledger.TransactionType.WITHDRAWAL,
                BigDecimal.valueOf(200.00), BigDecimal.valueOf(1300.00),
                LocalDateTime.now().minusMinutes(5));

        // Simulate ordering: newest first (withdrawalEntry before depositEntry)
        given(ledgerRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of(withdrawalEntry, depositEntry));

        WalletResponse response = walletService.getWallet(USER_ID);

        assertThat(response.history()).hasSize(2);

        LedgerEntryDto first  = response.history().get(0);
        LedgerEntryDto second = response.history().get(1);

        assertThat(first.transactionType()).isEqualTo("WITHDRAWAL");
        assertThat(first.amount()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
        assertThat(first.balanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));

        assertThat(second.transactionType()).isEqualTo("DEPOSIT");
        assertThat(second.amount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(second.balanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
    }

    // AC-8: getWallet returns an empty history list when no transactions have been made.
    @Test
    void getWallet_shouldReturnEmptyHistoryWhenNoTransactions() {
        given(ledgerRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of());

        WalletResponse response = walletService.getWallet(USER_ID);

        assertThat(response.history()).isEmpty();
    }

    // AC-8: Each ledger entry in the history contains a non-null timestamp.
    @Test
    void getWallet_ledgerEntries_shouldHaveNonNullTimestamps() {
        Ledger entry = buildLedgerEntry(3L, Ledger.TransactionType.DEPOSIT,
                BigDecimal.valueOf(100.00), BigDecimal.valueOf(1100.00),
                LocalDateTime.now());

        given(ledgerRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of(entry));

        WalletResponse response = walletService.getWallet(USER_ID);

        assertThat(response.history().get(0).createdAt()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Wallet not found — common guard
    // ═══════════════════════════════════════════════════════════════════════════

    // All operations throw ResourceNotFoundException when no wallet exists for the user.
    @Test
    void allOperations_shouldThrowResourceNotFoundException_whenWalletNotFound() {
        given(walletRepository.findByUserUserId(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deposit(USER_ID, BigDecimal.valueOf(100.00)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> walletService.withdraw(USER_ID, BigDecimal.valueOf(100.00)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> walletService.getWallet(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper builders
    // ═══════════════════════════════════════════════════════════════════════════

    private Ledger buildLedgerEntry(Long id,
                                    Ledger.TransactionType type,
                                    BigDecimal amount,
                                    BigDecimal balanceAfter,
                                    LocalDateTime createdAt) {
        Ledger l = new Ledger(wallet, type, amount, balanceAfter, "MOCK", "MOCK-REF-" + id);
        l.setLedgerId(id);
        l.setCreatedAt(createdAt);
        return l;
    }
}