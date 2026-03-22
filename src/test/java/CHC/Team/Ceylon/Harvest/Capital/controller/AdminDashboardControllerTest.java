package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.AdminDashboardResponseDTO;
import CHC.Team.Ceylon.Harvest.Capital.dto.UserDTO;
import CHC.Team.Ceylon.Harvest.Capital.exception.AdminDashboardException;
import CHC.Team.Ceylon.Harvest.Capital.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: Dashboard data is retrieved via GET /admin/dashboard
// AC-2: Complete list of all farmers and investors is returned
// AC-3: Platform-wide investment total is present and accurate
// AC-4: Account status (verificationStatus) is included per user
// AC-5: Error state is handled — 500 returned when service throws
@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminDashboardController adminDashboardController =
                new AdminDashboardController(adminDashboardService);

        mockMvc = MockMvcBuilders.standaloneSetup(adminDashboardController).build();
    }

    // ── AC-1 + AC-2 + AC-3 + AC-4 ────────────────────────────────────────────
    @Test
    void getDashboard_shouldReturnLiveAdminDashboardData() throws Exception {
        List<UserDTO> farmers = List.of(
                new UserDTO(1L, "Nuwan Perera",   "nuwan@farm.lk",   "FARMER",   "VERIFIED"),
                new UserDTO(2L, "Kasun Silva",    "kasun@farm.lk",   "FARMER",   "PENDING"));

        List<UserDTO> investors = List.of(
                new UserDTO(3L, "Sachith Fernando", "sachith@invest.lk", "INVESTOR", "VERIFIED"),
                new UserDTO(4L, "Rashmi Jayawardena","rashmi@invest.lk",  "INVESTOR", "NOT_SUBMITTED"));

        AdminDashboardResponseDTO response = new AdminDashboardResponseDTO(
                2,
                2,
                BigDecimal.valueOf(750000.00),
                farmers,
                investors);

        when(adminDashboardService.getDashboardData()).thenReturn(response);

        // AC-1: correct endpoint responds 200
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())

                // AC-2: farmer and investor counts
                .andExpect(jsonPath("$.totalFarmers").value(2))
                .andExpect(jsonPath("$.totalInvestors").value(2))

                // AC-2: farmer list details
                .andExpect(jsonPath("$.farmers.length()").value(2))
                .andExpect(jsonPath("$.farmers[0].id").value(1))
                .andExpect(jsonPath("$.farmers[0].name").value("Nuwan Perera"))
                .andExpect(jsonPath("$.farmers[0].email").value("nuwan@farm.lk"))
                .andExpect(jsonPath("$.farmers[0].role").value("FARMER"))
                .andExpect(jsonPath("$.farmers[1].name").value("Kasun Silva"))

                // AC-2: investor list details
                .andExpect(jsonPath("$.investors.length()").value(2))
                .andExpect(jsonPath("$.investors[0].id").value(3))
                .andExpect(jsonPath("$.investors[0].name").value("Sachith Fernando"))
                .andExpect(jsonPath("$.investors[0].email").value("sachith@invest.lk"))
                .andExpect(jsonPath("$.investors[0].role").value("INVESTOR"))
                .andExpect(jsonPath("$.investors[1].name").value("Rashmi Jayawardena"))

                // AC-3: platform-wide investment total
                .andExpect(jsonPath("$.totalInvestment").value(750000.00))

                // AC-4: account status per user (verificationStatus)
                .andExpect(jsonPath("$.farmers[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.farmers[1].status").value("PENDING"))
                .andExpect(jsonPath("$.investors[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.investors[1].status").value("NOT_SUBMITTED"));

        verify(adminDashboardService).getDashboardData();
    }

    // ── AC-2: Dashboard handles zero farmers and investors gracefully ─────────
    @Test
    void getDashboard_shouldReturnEmptyListsWhenNoUsersExist() throws Exception {
        AdminDashboardResponseDTO emptyResponse = new AdminDashboardResponseDTO(
                0, 0, BigDecimal.ZERO, List.of(), List.of());

        when(adminDashboardService.getDashboardData()).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFarmers").value(0))
                .andExpect(jsonPath("$.totalInvestors").value(0))
                .andExpect(jsonPath("$.totalInvestment").value(0))
                .andExpect(jsonPath("$.farmers.length()").value(0))
                .andExpect(jsonPath("$.investors.length()").value(0));
    }

    // ── AC-3: Zero investment total is returned accurately ────────────────────
    @Test
    void getDashboard_shouldReturnZeroTotalInvestmentWhenNoInvestmentsExist() throws Exception {
        List<UserDTO> farmers = List.of(
                new UserDTO(1L, "Nuwan Perera", "nuwan@farm.lk", "FARMER", "VERIFIED"));

        AdminDashboardResponseDTO response = new AdminDashboardResponseDTO(
                1, 0, BigDecimal.ZERO, farmers, List.of());

        when(adminDashboardService.getDashboardData()).thenReturn(response);

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvestment").value(0))
                .andExpect(jsonPath("$.totalFarmers").value(1))
                .andExpect(jsonPath("$.totalInvestors").value(0));
    }

    // ── AC-5: Error state — service failure returns 500 with error body ───────
    @Test
    void getDashboard_shouldReturn500WhenServiceThrowsAdminDashboardException() throws Exception {
        when(adminDashboardService.getDashboardData())
                .thenThrow(new AdminDashboardException(
                        "Unable to load admin dashboard at the moment. Please try again later."));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Dashboard data could not be loaded"))
                .andExpect(jsonPath("$.message").value(
                        "Unable to load admin dashboard at the moment. Please try again later."))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
