package CHC.Team.Ceylon.Harvest.Capital.service.payment;

import java.math.BigDecimal;

/**
 * Gateway interface for wallet funding operations.
 *
 * Design goals:
 *  - Zero coupling between business logic and a specific payment provider.
 *  - New gateways (PayHere, Stripe, etc.) are added by implementing this
 *    interface and registering the bean — no other code changes needed.
 *  - Each method returns a {@link GatewayResult} that the service layer
 *    inspects; the service never catches gateway-internal exceptions directly.
 */
public interface PaymentGateway {

    /**
     * Human-readable name of this gateway, e.g. "MOCK" or "PAYHERE".
     * Stored in the Ledger so we know which gateway processed each tx.
     */
    String gatewayName();

    /**
     * Initiate a deposit (money IN to the platform wallet).
     *
     * @param userId    the investor's internal user ID
     * @param amount    positive amount to credit
     * @param currency  ISO-4217 currency code, e.g. "LKR"
     * @return result containing success flag and gateway reference number
     */
    GatewayResult deposit(Long userId, BigDecimal amount, String currency);

    /**
     * Initiate a withdrawal (money OUT from the platform wallet).
     *
     * @param userId    the investor's internal user ID
     * @param amount    positive amount to debit
     * @param currency  ISO-4217 currency code
     * @return result containing success flag and gateway reference number
     */
    GatewayResult withdraw(Long userId, BigDecimal amount, String currency);

    // ── Result value object ──────────────────────────────────────────────────

    record GatewayResult(boolean success, String reference, String errorMessage) {

        /** Convenience factory — successful transaction. */
        public static GatewayResult ok(String reference) {
            return new GatewayResult(true, reference, null);
        }

        /** Convenience factory — failed transaction. */
        public static GatewayResult fail(String errorMessage) {
            return new GatewayResult(false, null, errorMessage);
        }
    }
}
