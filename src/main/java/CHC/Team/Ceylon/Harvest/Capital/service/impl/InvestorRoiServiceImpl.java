package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.RoiSnapshot;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.RoiSnapshotRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorRoiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class InvestorRoiServiceImpl implements InvestorRoiService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 4;

    private final InvestmentRepository investmentRepository;
    private final RoiSnapshotRepository roiSnapshotRepository;

    public InvestorRoiServiceImpl(
            InvestmentRepository investmentRepository,
            RoiSnapshotRepository roiSnapshotRepository) {
        this.investmentRepository = investmentRepository;
        this.roiSnapshotRepository = roiSnapshotRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> buildInvestmentRoiMetrics(Investment investment) {
        Land land = investment.getLand();
        BigDecimal amountInvested = safeMoney(investment.getAmountInvested());
        BigDecimal landTotalValue = safeMoney(land.getTotalValue());
        BigDecimal totalRaised = safeMoney(investmentRepository.sumAmountByLandId(land.getLandId()));
        int progressPercentage = clampProgress(land.getProgressPercentage());

        BigDecimal investorOwnershipPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedInvestorReturn = amountInvested;

        if (totalRaised.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ownershipRatio = amountInvested.divide(totalRaised, 8, RoundingMode.HALF_UP);
            investorOwnershipPercentage = ownershipRatio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            expectedInvestorReturn = landTotalValue.multiply(ownershipRatio).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal projectedProfit = expectedInvestorReturn.subtract(amountInvested).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal projectedRoiPercentage = normalizedPercentage(projectedProfit, expectedInvestorReturn);
        BigDecimal currentEstimatedValue = amountInvested.add(
                projectedProfit.multiply(BigDecimal.valueOf(progressPercentage))
                        .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP)
        ).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal liveRoiPercentage = projectedRoiPercentage
                .multiply(BigDecimal.valueOf(progressPercentage))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        int statusRiskScore = statusRisk(investment);
        int executionRiskScore = executionRisk(progressPercentage);
        int returnRiskScore = returnRisk(projectedRoiPercentage);
        int concentrationRiskScore = concentrationRisk(investorOwnershipPercentage);
        int riskScore = deriveRiskScore(statusRiskScore, executionRiskScore, returnRiskScore, concentrationRiskScore);
        String riskLevel = classifyRisk(riskScore);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("investorOwnershipPercentage", investorOwnershipPercentage);
        metrics.put("expectedInvestorReturn", expectedInvestorReturn);
        metrics.put("projectedProfit", projectedProfit);
        metrics.put("projectedRoiPercentage", projectedRoiPercentage);
        metrics.put("currentEstimatedValue", currentEstimatedValue);
        metrics.put("liveRoiPercentage", liveRoiPercentage);
        metrics.put("progressPercentage", progressPercentage);
        metrics.put("riskLevel", riskLevel);
        metrics.put("riskScore", riskScore);
        metrics.put("statusRiskScore", statusRiskScore);
        metrics.put("executionRiskScore", executionRiskScore);
        metrics.put("returnRiskScore", returnRiskScore);
        metrics.put("concentrationRiskScore", concentrationRiskScore);
        metrics.put("returnModel", "NORMALIZED_EXPECTED_RETURN");
        metrics.put("riskModel", "STATUS_PROGRESS_RETURN_CONCENTRATION");
        return metrics;
    }

    @Override
    public void syncSnapshotsForInvestor(Long userId) {
        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);
        LocalDate today = LocalDate.now();
        for (Investment investment : investments) {
            recordSnapshot(investment, today);
        }
    }

    @Override
    public void syncSnapshotsForLand(Long landId) {
        syncSnapshotsForLand(landId, LocalDate.now());
    }

    @Override
    public void syncSnapshotsForLand(Long landId, LocalDate snapshotDate) {
        List<Investment> investments = investmentRepository.findAllByLandIdWithLand(landId);
        for (Investment investment : investments) {
            recordSnapshot(investment, snapshotDate);
        }
    }

    @Override
    public void recordSnapshot(Investment investment, LocalDate snapshotDate) {
        upsertSnapshot(investment, snapshotDate == null ? LocalDate.now() : snapshotDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getRoiHistory(Long userId) {
        // IMPORTANT:
        // Do NOT call syncSnapshotsForInvestor(userId) here.
        // This is a read-only endpoint and must not try to INSERT snapshots.

        List<Investment> investments = investmentRepository.findAllByUserIdWithLand(userId);
        List<Long> investmentIds = investments.stream().map(Investment::getInvestmentId).toList();
        List<RoiSnapshot> snapshots = investmentIds.isEmpty()
                ? List.of()
                : roiSnapshotRepository.findAllByInvestmentIds(investmentIds);

        Map<LocalDate, List<RoiSnapshot>> byDate = new LinkedHashMap<>();
        Map<Long, List<RoiSnapshot>> byInvestment = new LinkedHashMap<>();

        for (RoiSnapshot snapshot : snapshots) {
            byDate.computeIfAbsent(snapshot.getSnapshotDate(), ignored -> new ArrayList<>()).add(snapshot);
            byInvestment.computeIfAbsent(snapshot.getInvestment().getInvestmentId(), ignored -> new ArrayList<>()).add(snapshot);
        }

        List<Map<String, Object>> portfolioTrend = byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    BigDecimal totalCurrentValue = entry.getValue().stream()
                            .map(RoiSnapshot::getCurrentValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalInvested = entry.getValue().stream()
                            .map(RoiSnapshot::getInvestedAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal normalizedPortfolioRoi =
                            normalizedPercentage(totalCurrentValue.subtract(totalInvested), totalCurrentValue);

                    Map<String, Object> point = new HashMap<>();
                    point.put("date", entry.getKey().toString());
                    point.put("totalCurrentValue", totalCurrentValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    point.put("totalInvested", totalInvested.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    point.put("portfolioRoiPercentage", normalizedPortfolioRoi);
                    return point;
                })
                .toList();

        List<Map<String, Object>> projectHistories = investments.stream()
                .sorted(Comparator.comparing(inv -> inv.getLand().getProjectName(), String.CASE_INSENSITIVE_ORDER))
                .map(investment -> {
                    Map<String, Object> metrics = buildInvestmentRoiMetrics(investment);

                    List<Map<String, Object>> history = byInvestment
                            .getOrDefault(investment.getInvestmentId(), List.of())
                            .stream()
                            .sorted(Comparator.comparing(RoiSnapshot::getSnapshotDate))
                            .map(snapshot -> {
                                Map<String, Object> row = new HashMap<>();
                                row.put("date", snapshot.getSnapshotDate().toString());
                                row.put("currentValue", snapshot.getCurrentValue());
                                row.put("investedAmount", snapshot.getInvestedAmount());
                                row.put("roiPercentage", snapshot.getRoiPercentage());
                                return row;
                            })
                            .toList();

                    Map<String, Object> project = new HashMap<>();
                    project.put("investmentId", investment.getInvestmentId());
                    project.put("landId", investment.getLand().getLandId());
                    project.put("projectName", investment.getLand().getProjectName());
                    project.put("location", investment.getLand().getLocation());
                    project.put("cropType", investment.getLand().getCropType());
                    project.put("status", investment.getStatus().name());
                    project.putAll(metrics);
                    project.put("history", history);
                    return project;
                })
                .toList();

        List<Map<String, Object>> comparison = projectHistories.stream()
                .map(project -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("investmentId", project.get("investmentId"));
                    item.put("projectName", project.get("projectName"));
                    item.put("location", project.get("location"));
                    item.put("cropType", project.get("cropType"));
                    item.put("status", project.get("status"));
                    item.put("liveRoiPercentage", project.get("liveRoiPercentage"));
                    item.put("projectedRoiPercentage", project.get("projectedRoiPercentage"));
                    item.put("currentEstimatedValue", project.get("currentEstimatedValue"));
                    item.put("expectedInvestorReturn", project.get("expectedInvestorReturn"));
                    item.put("riskLevel", project.get("riskLevel"));
                    item.put("riskScore", project.get("riskScore"));
                    return item;
                })
                .sorted(Comparator
                        .comparing(
                                (Map<String, Object> item) -> (BigDecimal) item.get("projectedRoiPercentage"),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(item -> item.get("projectName").toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("asOfDate", LocalDate.now().toString());
        result.put("portfolioTrend", portfolioTrend);
        result.put("projectHistories", projectHistories);
        result.put("comparison", comparison);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> buildPortfolioRoiSummary(List<Investment> investments) {
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal weightedLiveAccumulator = BigDecimal.ZERO;
        BigDecimal weightedProjectedAccumulator = BigDecimal.ZERO;
        BigDecimal weightedRiskAccumulator = BigDecimal.ZERO;
        Map<String, Object> bestPerforming = null;
        Map<String, Object> lowestRisk = null;

        for (Investment investment : investments) {
            Map<String, Object> metrics = buildInvestmentRoiMetrics(investment);
            BigDecimal amountInvested = safeMoney(investment.getAmountInvested());
            BigDecimal liveRoi = (BigDecimal) metrics.get("liveRoiPercentage");
            BigDecimal projectedRoi = (BigDecimal) metrics.get("projectedRoiPercentage");
            BigDecimal riskScore = BigDecimal.valueOf(((Number) metrics.get("riskScore")).intValue());

            totalInvested = totalInvested.add(amountInvested);
            weightedLiveAccumulator = weightedLiveAccumulator.add(amountInvested.multiply(liveRoi));
            weightedProjectedAccumulator = weightedProjectedAccumulator.add(amountInvested.multiply(projectedRoi));
            weightedRiskAccumulator = weightedRiskAccumulator.add(amountInvested.multiply(riskScore));

            if (bestPerforming == null || liveRoi.compareTo((BigDecimal) bestPerforming.get("liveRoiPercentage")) > 0) {
                bestPerforming = new HashMap<>();
                bestPerforming.put("projectName", investment.getLand().getProjectName());
                bestPerforming.put("investmentId", investment.getInvestmentId());
                bestPerforming.put("liveRoiPercentage", liveRoi);
            }

            if (lowestRisk == null || riskScore.compareTo(BigDecimal.valueOf(((Number) lowestRisk.get("riskScore")).intValue())) < 0) {
                lowestRisk = new HashMap<>();
                lowestRisk.put("projectName", investment.getLand().getProjectName());
                lowestRisk.put("investmentId", investment.getInvestmentId());
                lowestRisk.put("riskScore", riskScore.intValue());
            }
        }

        BigDecimal weightedLive = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? weightedLiveAccumulator.divide(totalInvested, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal weightedProjected = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? weightedProjectedAccumulator.divide(totalInvested, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        int weightedRiskScore = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? weightedRiskAccumulator.divide(totalInvested, 0, RoundingMode.HALF_UP).intValue()
                : 0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("weightedLiveRoiPercentage", weightedLive);
        summary.put("weightedProjectedRoiPercentage", weightedProjected);
        summary.put("weightedRiskScore", weightedRiskScore);
        summary.put("weightedRiskLevel", classifyRisk(weightedRiskScore));
        summary.put("bestPerformingProject", bestPerforming != null ? bestPerforming.get("projectName") : null);
        summary.put("lowestRiskProject", lowestRisk != null ? lowestRisk.get("projectName") : null);
        return summary;
    }

    private void upsertSnapshot(Investment investment, LocalDate snapshotDate) {
        Map<String, Object> metrics = buildInvestmentRoiMetrics(investment);
        LocalDate effectiveDate = snapshotDate == null ? LocalDate.now() : snapshotDate;

        RoiSnapshot snapshot = roiSnapshotRepository
                .findByInvestmentInvestmentIdAndSnapshotDate(investment.getInvestmentId(), effectiveDate)
                .orElseGet(RoiSnapshot::new);

        snapshot.setInvestment(investment);
        snapshot.setSnapshotDate(effectiveDate);
        snapshot.setInvestedAmount(safeMoney(investment.getAmountInvested()));
        snapshot.setCurrentValue((BigDecimal) metrics.get("currentEstimatedValue"));
        snapshot.setRoiPercentage((BigDecimal) metrics.get("liveRoiPercentage"));
        roiSnapshotRepository.save(snapshot);
    }

    private BigDecimal normalizedPercentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal raw = numerator.multiply(HUNDRED)
                .divide(denominator, PCT_SCALE, RoundingMode.HALF_UP);

        if (raw.compareTo(HUNDRED) > 0) {
            raw = HUNDRED;
        }
        if (raw.compareTo(HUNDRED.negate()) < 0) {
            raw = HUNDRED.negate();
        }

        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private int clampProgress(Integer progressPercentage) {
        int raw = progressPercentage == null ? 0 : progressPercentage;
        return Math.max(0, Math.min(100, raw));
    }

    private int deriveRiskScore(int statusRiskScore, int executionRiskScore, int returnRiskScore, int concentrationRiskScore) {
        double weighted = (statusRiskScore * 0.35)
                + (executionRiskScore * 0.35)
                + (returnRiskScore * 0.20)
                + (concentrationRiskScore * 0.10);
        return Math.max(0, Math.min(100, (int) Math.round(weighted)));
    }

    private int statusRisk(Investment investment) {
        return switch (investment.getStatus()) {
            case COMPLETED -> 10;
            case ACTIVE -> 35;
            case PENDING -> 65;
            case CANCELLED -> 95;
        };
    }

    private int executionRisk(int progressPercentage) {
        return 100 - Math.max(0, Math.min(100, progressPercentage));
    }

    private int returnRisk(BigDecimal projectedRoiPercentage) {
        if (projectedRoiPercentage == null) {
            return 55;
        }
        if (projectedRoiPercentage.compareTo(BigDecimal.valueOf(25)) >= 0) {
            return 10;
        }
        if (projectedRoiPercentage.compareTo(BigDecimal.valueOf(15)) >= 0) {
            return 20;
        }
        if (projectedRoiPercentage.compareTo(BigDecimal.valueOf(8)) >= 0) {
            return 35;
        }
        if (projectedRoiPercentage.compareTo(BigDecimal.ZERO) >= 0) {
            return 50;
        }
        return 75;
    }

    private int concentrationRisk(BigDecimal investorOwnershipPercentage) {
        if (investorOwnershipPercentage == null) {
            return 10;
        }
        if (investorOwnershipPercentage.compareTo(BigDecimal.valueOf(35)) > 0) {
            return 65;
        }
        if (investorOwnershipPercentage.compareTo(BigDecimal.valueOf(20)) > 0) {
            return 45;
        }
        if (investorOwnershipPercentage.compareTo(BigDecimal.valueOf(10)) > 0) {
            return 25;
        }
        return 10;
    }

    private String classifyRisk(int riskScore) {
        if (riskScore < 30) {
            return "LOW";
        }
        if (riskScore < 60) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
