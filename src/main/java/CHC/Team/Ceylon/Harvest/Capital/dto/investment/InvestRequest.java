package CHC.Team.Ceylon.Harvest.Capital.dto.investment;

import java.math.BigDecimal;

/**
 * Body sent by the investor when purchasing a stake in a land project.
 * The amount is debited from the investor's wallet — no external payment needed.
 */
public record InvestRequest(BigDecimal amount) {}
