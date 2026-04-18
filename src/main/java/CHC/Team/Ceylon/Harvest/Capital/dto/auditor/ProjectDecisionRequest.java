package CHC.Team.Ceylon.Harvest.Capital.dto.auditor;

/**
 * Request body for approving or rejecting a land/project submission.
 * {@code reason} is required only on rejection.
 */
public record ProjectDecisionRequest(String reason) {}
