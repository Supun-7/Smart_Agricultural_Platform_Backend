package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.dto.wallet.WalletDtos.*;
import CHC.Team.Ceylon.Harvest.Capital.entity.Ledger;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.exception.BadRequestException;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.LedgerRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.WalletService;
import CHC.Team.Ceylon.Harvest.Capital.service.payment.PaymentGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final LedgerRepository ledgerRepository;
    private final PaymentGateway   paymentGateway;   // injected — mock or real

    public WalletServiceImpl(WalletRepository walletRepository,
                             LedgerRepository ledgerRepository,
                             PaymentGateway paymentGateway) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.paymentGateway   = paymentGateway;
    }

    // ── AC-1 / AC-2 / AC-3 / AC-7: Deposit ──────────────────────────────────

    @Override
    @Transactional
    public TransactionResponse deposit(Long userId, BigDecimal amount) {

        // AC-7 — reject zero or negative
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Deposit amount must be greater than zero.");
        }

        Wallet wallet = resolveWallet(userId);

        // Call gateway interface — currently MockPaymentGateway, swap for PayHere later
        PaymentGateway.GatewayResult result =
                paymentGateway.deposit(userId, amount, wallet.getCurrency());

        if (!result.success()) {
            throw new BadRequestException("Payment gateway rejected deposit: " + result.errorMessage());
        }

        // AC-2 — update balance immediately
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // AC-3 — write ledger record
        Ledger entry = new Ledger(wallet, Ledger.TransactionType.DEPOSIT,
                amount, newBalance,
                paymentGateway.gatewayName(), result.reference());
        ledgerRepository.save(entry);

        return new TransactionResponse(
                "DEPOSIT", amount, newBalance, wallet.getCurrency(),
                paymentGateway.gatewayName(), result.reference(), entry.getCreatedAt());
    }

    // ── AC-4 / AC-5 / AC-6 / AC-7: Withdrawal ───────────────────────────────

    @Override
    @Transactional
    public TransactionResponse withdraw(Long userId, BigDecimal amount) {

        // AC-7 — reject zero or negative
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Withdrawal amount must be greater than zero.");
        }

        Wallet wallet = resolveWallet(userId);

        // AC-5 — block if insufficient balance
        if (amount.compareTo(wallet.getBalance()) > 0) {
            throw new BadRequestException(
                    "Insufficient balance. Available: " + wallet.getCurrency()
                    + " " + wallet.getBalance() + ", requested: " + amount + ".");
        }

        // Call gateway interface
        PaymentGateway.GatewayResult result =
                paymentGateway.withdraw(userId, amount, wallet.getCurrency());

        if (!result.success()) {
            throw new BadRequestException("Payment gateway rejected withdrawal: " + result.errorMessage());
        }

        // AC-6 — reduce balance and write ledger
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Ledger entry = new Ledger(wallet, Ledger.TransactionType.WITHDRAWAL,
                amount, newBalance,
                paymentGateway.gatewayName(), result.reference());
        ledgerRepository.save(entry);

        return new TransactionResponse(
                "WITHDRAWAL", amount, newBalance, wallet.getCurrency(),
                paymentGateway.gatewayName(), result.reference(), entry.getCreatedAt());
    }

    // ── AC-8: Get wallet + history ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {

        Wallet wallet = resolveWallet(userId);

        List<LedgerEntryDto> history = ledgerRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(l -> new LedgerEntryDto(
                        l.getLedgerId(),
                        l.getTransactionType().name(),
                        l.getAmount(),
                        l.getBalanceAfter(),
                        l.getGateway(),
                        l.getGatewayReference(),
                        l.getCreatedAt()))
                .toList();

        return new WalletResponse(
                wallet.getWalletId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                history);
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private Wallet resolveWallet(Long userId) {
        return walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet not found for investor: " + userId));
    }
}
