package CHC.Team.Ceylon.Harvest.Capital.service.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock payment gateway — costs £0, requires no external account, and works
 * in any environment (local, CI, Sri Lanka network restrictions, etc.).
 *
 * How it works:
 *  - Every transaction is auto-approved instantly.
 *  - A unique UUID reference is generated so the Ledger has a real reference.
 *  - Logs the transaction so QA can verify in console output (AC-8).
 *
 * To add a real gateway later (e.g. PayHere):
 *  1. Create PayHerePaymentGateway implements PaymentGateway.
 *  2. Annotate with @Component @Profile("production").
 *  3. Annotate this class with @Profile("!production").
 *  No other code changes are required — the service depends on the interface.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public String gatewayName() {
        return "MOCK";
    }

    @Override
    public GatewayResult deposit(Long userId, BigDecimal amount, String currency) {
        String ref = "MOCK-DEP-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 12);
        log.info("[MockGateway] DEPOSIT approved | user={} amount={} {} ref={}", userId, amount, currency, ref);
        return GatewayResult.ok(ref);
    }

    @Override
    public GatewayResult withdraw(Long userId, BigDecimal amount, String currency) {
        String ref = "MOCK-WDR-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 12);
        log.info("[MockGateway] WITHDRAWAL approved | user={} amount={} {} ref={}", userId, amount, currency, ref);
        return GatewayResult.ok(ref);
    }
}
