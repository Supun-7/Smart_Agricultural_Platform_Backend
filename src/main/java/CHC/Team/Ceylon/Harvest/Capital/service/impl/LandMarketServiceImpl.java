package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorRoiService;
import CHC.Team.Ceylon.Harvest.Capital.service.LandMarketService;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LandMarketServiceImpl implements LandMarketService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LandRepository landRepository;
    private final InvestmentRepository investmentRepository;
    private final InvestorRoiService investorRoiService;

    public LandMarketServiceImpl(
            LandRepository landRepository,
            InvestmentRepository investmentRepository,
            InvestorRoiService investorRoiService) {
        this.landRepository = landRepository;
        this.investmentRepository = investmentRepository;
        this.investorRoiService = investorRoiService;
    }

    @Override
    public Map<String, Object> getInvestorLandMarket(Long userId) {
        List<Land> activeLands = landRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        List<Investment> investorInvestments = investmentRepository.findAllByUserIdWithLand(userId);

        Set<Long> investedLandIds = investorInvestments.stream()
                .map(investment -> investment.getLand().getLandId())
                .collect(Collectors.toSet());

        Map<Long, Map<String, Object>> marketByLandId = new LinkedHashMap<>();
        for (Land land : activeLands) {
            marketByLandId.put(land.getLandId(), buildMarketRow(land, investedLandIds.contains(land.getLandId())));
        }

        List<Map<String, Object>> investorComparisons = new ArrayList<>();
        for (Investment investment : investorInvestments) {
            Land land = investment.getLand();
            Map<String, Object> benchmark = marketByLandId.computeIfAbsent(
                    land.getLandId(),
                    ignored -> buildMarketRow(land, true)
            );
            Map<String, Object> roiMetrics = investorRoiService.buildInvestmentRoiMetrics(investment);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("investmentId", investment.getInvestmentId());
            row.put("landId", land.getLandId());
            row.put("projectName", land.getProjectName());
            row.put("location", land.getLocation());
            row.put("province", benchmark.get("province"));
            row.put("cropType", benchmark.get("cropType"));
            row.put("marketProjectedRoiPercentage", benchmark.get("marketProjectedRoiPercentage"));
            row.put("marketRiskScore", benchmark.get("marketRiskScore"));
            row.put("marketRiskLevel", benchmark.get("marketRiskLevel"));
            row.put("benchmarkConfidence", benchmark.get("benchmarkConfidence"));
            row.put("provinceFit", benchmark.get("provinceFit"));
            row.put("liveRoiPercentage", roiMetrics.get("liveRoiPercentage"));
            row.put("projectedRoiPercentage", roiMetrics.get("projectedRoiPercentage"));
            row.put("currentEstimatedValue", roiMetrics.get("currentEstimatedValue"));
            row.put("expectedInvestorReturn", roiMetrics.get("expectedInvestorReturn"));
            row.put("riskScore", roiMetrics.get("riskScore"));
            row.put("riskLevel", roiMetrics.get("riskLevel"));
            row.put("relativeToMarketPercentage",
                    difference(
                            (BigDecimal) roiMetrics.get("projectedRoiPercentage"),
                            toBigDecimal(benchmark.get("marketProjectedRoiPercentage"))));
            investorComparisons.add(row);
        }

        investorComparisons.sort(
                Comparator.comparing(
                                (Map<String, Object> row) -> toBigDecimal(row.get("relativeToMarketPercentage")),
                                Comparator.reverseOrder())
                        .thenComparing(row -> String.valueOf(row.get("projectName")), String.CASE_INSENSITIVE_ORDER)
        );

        List<Map<String, Object>> marketRows = new ArrayList<>(marketByLandId.values());
        marketRows.sort(
                Comparator.comparing(
                                (Map<String, Object> row) -> toBigDecimal(row.get("marketProjectedRoiPercentage")),
                                Comparator.reverseOrder())
                        .thenComparing(row -> String.valueOf(row.get("projectName")), String.CASE_INSENSITIVE_ORDER)
        );

        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> bestMarket = marketRows.isEmpty() ? null : marketRows.get(0);
        summary.put("updatedAt", LocalDate.now().toString());
        summary.put("coverageCount", marketRows.size());
        summary.put("supportedCrops", List.of("Paddy", "Tea", "Rubber", "Coconut", "Potato", "Capsicum/Chilli", "Maize", "Vegetables", "Fruit"));
        summary.put("topProvince", bestMarket != null ? bestMarket.get("province") : null);
        summary.put("topCrop", bestMarket != null ? bestMarket.get("cropType") : null);
        summary.put("topBenchmarkRoiPercentage", bestMarket != null ? bestMarket.get("marketProjectedRoiPercentage") : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("marketRows", marketRows);
        result.put("investorComparisons", investorComparisons);
        result.put("methodology", List.of(
                "Investor ROI stays backend-computed from investor-specific cash flows and milestone progress.",
                "Land market ROI is an indicative benchmark built from Sri Lankan public crop economics plus province suitability, not a guaranteed payout.",
                "Official benchmark-backed crops: paddy, tea, rubber and coconut. Other crop families use agronomic fit weighting where no single official province-by-crop ROI table exists."
        ));
        return result;
    }

    private Map<String, Object> buildMarketRow(Land land, boolean investedByUser) {
        String province = detectProvince(land.getLocation());
        String cropType = normalizeCrop(land.getCropType());
        CropBenchmark benchmark = benchmarkFor(cropType);
        ProvinceAdjustment provinceAdjustment = provinceAdjustment(province, cropType);

        BigDecimal adjustedRoi = benchmark.baseProjectedRoi
                .multiply(provinceAdjustment.multiplier)
                .setScale(2, RoundingMode.HALF_UP);
        if (adjustedRoi.compareTo(HUNDRED) > 0) {
            adjustedRoi = HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        if (adjustedRoi.compareTo(BigDecimal.ZERO) < 0) {
            adjustedRoi = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        int marketRiskScore = Math.max(0, Math.min(100, benchmark.baseRiskScore + provinceAdjustment.riskShift));
        String marketRiskLevel = classifyRisk(marketRiskScore);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("landId", land.getLandId());
        row.put("projectName", land.getProjectName());
        row.put("location", land.getLocation());
        row.put("province", province);
        row.put("cropType", cropType);
        row.put("marketProjectedRoiPercentage", adjustedRoi);
        row.put("marketRiskScore", marketRiskScore);
        row.put("marketRiskLevel", marketRiskLevel);
        row.put("benchmarkConfidence", benchmark.confidence);
        row.put("provinceFit", provinceAdjustment.fitLabel);
        row.put("investedByCurrentUser", investedByUser);
        row.put("sizeAcres", land.getSizeAcres());
        row.put("landTotalValue", land.getTotalValue());
        row.put("minimumInvestment", land.getMinimumInvestment());
        row.put("progressPercentage", land.getProgressPercentage());
        row.put("marketMethod", benchmark.method);
        row.put("marketNote", benchmark.note + " " + provinceAdjustment.note);
        return row;
    }

    private CropBenchmark benchmarkFor(String cropType) {
        String normalized = cropType == null ? "OTHER" : cropType.toUpperCase(Locale.ROOT);

        if (normalized.contains("TEA")) {
            return new CropBenchmark(dec("17.40"), 38, "HIGH", "OFFICIAL_COST_VS_PRICE", "Tea benchmark uses official cost-per-kg versus official Colombo auction pricing.");
        }
        if (normalized.contains("RUBBER")) {
            return new CropBenchmark(dec("7.00"), 58, "HIGH", "OFFICIAL_COST_VS_PRICE", "Rubber benchmark uses official cost-per-kg with Sri Lankan rubber price trend support.");
        }
        if (normalized.contains("COCONUT")) {
            return new CropBenchmark(dec("54.90"), 46, "HIGH", "OFFICIAL_COST_VS_PRICE", "Coconut benchmark uses official cost-per-1,000 nuts versus official wholesale nut pricing.");
        }
        if (normalized.contains("PADDY") || normalized.contains("RICE")) {
            return new CropBenchmark(dec("15.50"), 34, "HIGH", "OFFICIAL_COST_VS_PRICE", "Paddy benchmark uses official producer price and official rice cost-of-production references.");
        }
        if (normalized.contains("POTATO")) {
            return new CropBenchmark(dec("22.00"), 52, "MEDIUM", "AGRONOMIC_BENCHMARK", "Potato benchmark uses Department of Agriculture crop-zone and yield references plus platform market normalization.");
        }
        if (normalized.contains("CAPSICUM") || normalized.contains("CHILLI") || normalized.contains("CHILI")) {
            return new CropBenchmark(dec("18.00"), 57, "MEDIUM", "AGRONOMIC_BENCHMARK", "Capsicum/chilli benchmark uses Department of Agriculture crop suitability references and platform market normalization.");
        }
        if (normalized.contains("MAIZE") || normalized.contains("CORN")) {
            return new CropBenchmark(dec("12.00"), 48, "MEDIUM", "AGRONOMIC_BENCHMARK", "Maize benchmark uses Department of Agriculture crop geography references and platform market normalization.");
        }
        if (normalized.contains("VEGETABLE")) {
            return new CropBenchmark(dec("16.00"), 55, "LOW", "AGRONOMIC_BENCHMARK", "Vegetable benchmark is an indicative blended market value based on crop-zone fit.");
        }
        if (normalized.contains("FRUIT")) {
            return new CropBenchmark(dec("14.00"), 50, "LOW", "AGRONOMIC_BENCHMARK", "Fruit benchmark is an indicative blended market value based on crop-zone fit.");
        }
        return new CropBenchmark(dec("10.00"), 50, "LOW", "GENERIC_BENCHMARK", "Generic crop benchmark is a conservative placeholder where no official crop-specific ROI dataset is available.");
    }

    private ProvinceAdjustment provinceAdjustment(String province, String cropType) {
        String p = province == null ? "UNKNOWN" : province.toUpperCase(Locale.ROOT);
        String c = cropType == null ? "OTHER" : cropType.toUpperCase(Locale.ROOT);

        if (c.contains("PADDY") || c.contains("RICE")) {
            if (p.equals("NORTH CENTRAL")) return new ProvinceAdjustment(dec("1.12"), -8, "Leader", "North Central is Sri Lanka's leading paddy province.");
            if (p.equals("NORTHERN")) return new ProvinceAdjustment(dec("1.08"), -5, "Strong", "Northern Province showed a strong recent increase in paddy production.");
            if (p.equals("EASTERN")) return new ProvinceAdjustment(dec("1.06"), -3, "Strong", "Eastern Province remains a strong paddy geography.");
        }

        if (c.contains("TEA")) {
            if (p.equals("CENTRAL")) return new ProvinceAdjustment(dec("1.10"), -6, "Leader", "Central is the main tea producing province in Sri Lanka.");
            if (p.equals("UVA")) return new ProvinceAdjustment(dec("1.08"), -3, "Strong", "Uva showed an increase in made tea production.");
            if (p.equals("SABARAGAMUWA")) return new ProvinceAdjustment(dec("1.06"), -2, "Strong", "Sabaragamuwa showed an increase in made tea production.");
        }

        if (c.contains("RUBBER")) {
            if (p.equals("SABARAGAMUWA")) return new ProvinceAdjustment(dec("1.10"), -4, "Leader", "Sabaragamuwa is the leading rubber province.");
            if (p.equals("WESTERN") || p.equals("SOUTHERN")) return new ProvinceAdjustment(dec("1.04"), -1, "Strong", "Western and Southern belts remain established rubber geographies.");
        }

        if (c.contains("COCONUT")) {
            if (p.equals("NORTH WESTERN")) return new ProvinceAdjustment(dec("1.10"), -3, "Leader", "North Western fits the traditional coconut belt.");
            if (p.equals("WESTERN")) return new ProvinceAdjustment(dec("1.08"), -2, "Strong", "Western Province remains part of Sri Lanka's core coconut economy.");
            if (p.equals("NORTHERN")) return new ProvinceAdjustment(dec("1.04"), 2, "Developing", "Northern Province is expanding under the new Northern Coconut Triangle initiative.");
        }

        if (c.contains("POTATO")) {
            if (p.equals("CENTRAL")) return new ProvinceAdjustment(dec("1.10"), -5, "Leader", "Nuwara Eliya gives Central Province a top potato fit.");
            if (p.equals("UVA")) return new ProvinceAdjustment(dec("1.06"), -2, "Strong", "Badulla gives Uva a strong intermediate-zone potato fit.");
            if (p.equals("NORTHERN")) return new ProvinceAdjustment(dec("1.04"), -1, "Strong", "Jaffna dry-zone potato cultivation supports Northern fit.");
        }

        if (c.contains("CAPSICUM") || c.contains("CHILLI") || c.contains("CHILI")) {
            if (p.equals("CENTRAL")) return new ProvinceAdjustment(dec("1.08"), -3, "Strong", "Central has a major capsicum district in Nuwara Eliya.");
            if (p.equals("UVA")) return new ProvinceAdjustment(dec("1.06"), -2, "Strong", "Uva has a major capsicum district in Badulla.");
            if (p.equals("NORTH WESTERN") || p.equals("NORTH CENTRAL")) return new ProvinceAdjustment(dec("1.04"), -1, "Strong", "North Western and North Central include major capsicum districts.");
        }

        if (c.contains("MAIZE") || c.contains("CORN")) {
            if (p.equals("UVA")) return new ProvinceAdjustment(dec("1.10"), -4, "Leader", "Monaragala makes Uva a leading maize geography.");
            if (p.equals("EASTERN")) return new ProvinceAdjustment(dec("1.06"), -2, "Strong", "Eastern dry-zone conditions support maize.");
            if (p.equals("NORTH CENTRAL")) return new ProvinceAdjustment(dec("1.04"), -1, "Strong", "North Central field-crop belts support maize.");
        }

        return new ProvinceAdjustment(dec("1.00"), 0, "General", "Province adjustment is neutral for this crop family.");
    }

    private String detectProvince(String location) {
        String normalized = normalize(location);
        if (normalized.isBlank()) return "Unknown";

        Map<String, String> districts = new HashMap<>();
        districts.put("colombo", "Western");
        districts.put("gampaha", "Western");
        districts.put("kalutara", "Western");
        districts.put("kandy", "Central");
        districts.put("matale", "Central");
        districts.put("nuwara eliya", "Central");
        districts.put("galle", "Southern");
        districts.put("matara", "Southern");
        districts.put("hambantota", "Southern");
        districts.put("jaffna", "Northern");
        districts.put("kilinochchi", "Northern");
        districts.put("mullaitivu", "Northern");
        districts.put("vavuniya", "Northern");
        districts.put("mannar", "Northern");
        districts.put("trincomalee", "Eastern");
        districts.put("batticaloa", "Eastern");
        districts.put("ampara", "Eastern");
        districts.put("kurunegala", "North Western");
        districts.put("kurunagala", "North Western");
        districts.put("puttalam", "North Western");
        districts.put("anuradhapura", "North Central");
        districts.put("polonnaruwa", "North Central");
        districts.put("badulla", "Uva");
        districts.put("monaragala", "Uva");
        districts.put("ratnapura", "Sabaragamuwa");
        districts.put("kegalle", "Sabaragamuwa");

        for (Map.Entry<String, String> entry : districts.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        for (String province : List.of("Western", "Central", "Southern", "Northern", "Eastern", "North Western", "North Central", "Uva", "Sabaragamuwa")) {
            if (normalized.contains(province.toLowerCase(Locale.ROOT))) {
                return province;
            }
        }

        return "Unknown";
    }

    private String normalizeCrop(String cropType) {
        String normalized = normalize(cropType);
        if (normalized.contains("paddy") || normalized.contains("rice")) return "Paddy";
        if (normalized.contains("tea")) return "Tea";
        if (normalized.contains("rubber")) return "Rubber";
        if (normalized.contains("coconut")) return "Coconut";
        if (normalized.contains("potato")) return "Potato";
        if (normalized.contains("capsicum")) return "Capsicum";
        if (normalized.contains("chilli") || normalized.contains("chili")) return "Chilli";
        if (normalized.contains("maize") || normalized.contains("corn")) return "Maize";
        if (normalized.contains("fruit")) return "Fruit";
        if (normalized.contains("vegetable")) return "Vegetables";
        return cropType == null || cropType.isBlank() ? "Other" : cropType.trim();
    }

    private String classifyRisk(int riskScore) {
        if (riskScore < 30) return "LOW";
        if (riskScore < 60) return "MEDIUM";
        return "HIGH";
    }

    private BigDecimal difference(BigDecimal left, BigDecimal right) {
        return safe(left).subtract(safe(right)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal dec(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record CropBenchmark(BigDecimal baseProjectedRoi, int baseRiskScore, String confidence, String method, String note) {}

    private record ProvinceAdjustment(BigDecimal multiplier, int riskShift, String fitLabel, String note) {}
}
