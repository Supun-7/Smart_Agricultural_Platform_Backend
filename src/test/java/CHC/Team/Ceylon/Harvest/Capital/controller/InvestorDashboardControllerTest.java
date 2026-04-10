package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestmentService;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvestorDashboardControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InvestorDashboardService investorDashboardService;

    @Mock
    private LandRepository landRepository;

    @Mock
    private InvestmentService investmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InvestorController investorController = new InvestorController(
                userRepository,
                kycSubmissionRepository,
                landRepository,
                jwtUtil,
                investorDashboardService,
                investmentService);

        mockMvc = MockMvcBuilders.standaloneSetup(investorController).build();
    }

    @Test
    void getDashboard_shouldReturnLiveInvestorDashboardData() throws Exception {
        Map<String, Object> dashboard = Map.of(
                "investorId", 99L,
                "investorName", "Sachith Investor",
                "email", "investor@test.com",
                "kycStatus", "VERIFIED",
                "walletBalance", BigDecimal.valueOf(250000.00),
                "currency", "LKR",
                "investedLands", List.of(
                        Map.of(
                                "investmentId", 501L,
                                "landId", 71L,
                                "projectName", "Pepper Estate Phase 1",
                                "location", "Kandy",
                                "amountInvested", BigDecimal.valueOf(150000.00),
                                "landTotalValue", BigDecimal.valueOf(600000.00),
                                "progressPercentage", 65,
                                "investmentDate", "2026-03-01T10:15",
                                "status", "ACTIVE"),
                        Map.of(
                                "investmentId", 502L,
                                "landId", 72L,
                                "projectName", "Tea Revival Project",
                                "location", "Nuwara Eliya",
                                "amountInvested", BigDecimal.valueOf(50000.00),
                                "landTotalValue", BigDecimal.valueOf(200000.00),
                                "progressPercentage", 40,
                                "investmentDate", "2026-03-05T09:00",
                                "status", "PENDING")),
                "investmentBreakdown", Map.of(
                        "totalInvested", BigDecimal.valueOf(200000.00),
                        "activeInvestments", BigDecimal.valueOf(150000.00),
                        "pendingInvestments", BigDecimal.valueOf(50000.00),
                        "completedInvestments", BigDecimal.ZERO,
                        "totalLandCount", 2L,
                        "activeLandCount", 1L));

        when(jwtUtil.extractUserId("qa-token")).thenReturn("99");
        when(investorDashboardService.getDashboard(99L)).thenReturn(dashboard);

        mockMvc.perform(get("/api/investor/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investorId").value(99))
                .andExpect(jsonPath("$.investorName").value("Sachith Investor"))
                .andExpect(jsonPath("$.walletBalance").value(250000.00))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.investedLands.length()").value(2))
                .andExpect(jsonPath("$.investedLands[0].projectName").value("Pepper Estate Phase 1"))
                .andExpect(jsonPath("$.investedLands[0].amountInvested").value(150000.00))
                .andExpect(jsonPath("$.investedLands[0].progressPercentage").value(65))
                .andExpect(jsonPath("$.investedLands[1].status").value("PENDING"))
                .andExpect(jsonPath("$.investmentBreakdown.totalInvested").value(200000.00))
                .andExpect(jsonPath("$.investmentBreakdown.activeInvestments").value(150000.00))
                .andExpect(jsonPath("$.investmentBreakdown.pendingInvestments").value(50000.00))
                .andExpect(jsonPath("$.investmentBreakdown.totalLandCount").value(2))
                .andExpect(jsonPath("$.investmentBreakdown.activeLandCount").value(1));

        verify(investorDashboardService).getDashboard(99L);
    }
}
