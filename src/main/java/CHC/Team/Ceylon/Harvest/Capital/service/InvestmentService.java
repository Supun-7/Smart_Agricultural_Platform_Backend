package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.investment.InvestRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.investment.InvestResponse;

/**
 * Handles wallet-funded land investments.
 * The investor's wallet balance is debited atomically with the Investment record creation.
 */
public interface InvestmentService {

    /**
     * Debit {@code request.amount()} from the investor's wallet and create
     * an Investment record for the given land. A LEDGER entry is written
     * so the debit is permanently traceable.
     *
     * Validations:
     *  - amount must be > 0
     *  - amount must be >= land's minimumInvestment
     *  - investor's wallet balance must be >= amount
     *  - land must exist and be active
     *  - investor must be KYC-verified
     */
    InvestResponse invest(Long investorId, Long landId, InvestRequest request);
}
