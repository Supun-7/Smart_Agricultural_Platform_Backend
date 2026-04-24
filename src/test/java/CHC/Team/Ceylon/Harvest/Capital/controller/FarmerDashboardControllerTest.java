package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.exception.FarmerDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.exception.GlobalExceptionHandler;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.service.FarmerDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FarmerDashboardControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmerApplicationRepository farmerApplicationRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FarmerDashboardService farmerDashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FarmerController farmerController = new FarmerController(
                userRepository,
                farmerApplicationRepository,
                jwtUtil,
                farmerDashboardService,
                "test-value" // ✅ ADD THIS
        );

        mockMvc = MockMvcBuilders.standaloneSetup(farmerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getFarmerDashboard_shouldReturnLiveDashboardDataFromService() throws Exception {
        // FarmerDashboardService.getFarmerDashboard() returns Map<String, Object>
        Map<String, Object> summary = Map.of(
                "totalFundedLands", 1,
                "totalInvestmentAmount", 150000.0);

        Map<String, Object> land = Map.of(
                "projectId", 55,
                "projectName", "Pepper Expansion",
                "landName", "Green Valley Plot",
                "farmLocation", "Kandy",
                "investmentAmount", 150000.0,
                "projectProgress", 65.0);

        Map<String, Object> response = Map.of(
                "summary", summary,
                "fundedLands", List.of(land));

        when(jwtUtil.extractUserId("qa-token")).thenReturn("99");
        when(farmerDashboardService.getFarmerDashboard(99L)).thenReturn(response);

        mockMvc.perform(get("/api/farmer/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalFundedLands").value(1))
                .andExpect(jsonPath("$.summary.totalInvestmentAmount").value(150000.0))
                .andExpect(jsonPath("$.fundedLands[0].projectId").value(55))
                .andExpect(jsonPath("$.fundedLands[0].investmentAmount").value(150000.0))
                .andExpect(jsonPath("$.fundedLands[0].projectProgress").value(65.0));

        verify(farmerDashboardService).getFarmerDashboard(99L);
    }

    @Test
    void getFarmerDashboard_whenServiceFails_shouldReturnUserFriendlyErrorMessage() throws Exception {
        when(jwtUtil.extractUserId("qa-token")).thenReturn("99");
        when(farmerDashboardService.getFarmerDashboard(99L))
                .thenThrow(new FarmerDashboardException(
                        "Unable to load farmer dashboard at the moment. Please try again later.",
                        new RuntimeException("Simulated backend failure")));

        mockMvc.perform(get("/api/farmer/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer qa-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("Unable to load farmer dashboard at the moment. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
