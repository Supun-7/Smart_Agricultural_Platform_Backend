package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.InvestmentController;
import CHC.Team.Ceylon.Harvest.Capital.repository.FarmerApplicationRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.BlockchainService;
import CHC.Team.Ceylon.Harvest.Capital.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-2  Only a user with INVESTOR role can submit an investment — all other roles are blocked
// AC-3  Requests without a valid Authorization header are rejected before any validation runs
// AC-4  Non-investor roles receive 403, not a balance or investment error
@WebMvcTest(InvestmentController.class)
@Import({ WebConfig.class, RoleInterceptor.class })
class InvestmentAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private WalletRepository walletRepository;
    @MockitoBean
    private LandRepository landRepository;
    @MockitoBean
    private InvestmentRepository investmentRepository;
    @MockitoBean
    private FarmerApplicationRepository farmerApplicationRepository;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private BlockchainService blockchainService;
    @MockitoBean
    private TransactionService transactionService;

    // ── AC-2: INVESTOR role is allowed to call POST /api/investment/fund
    // ─────────────────
    @Test
    void fundLand_withInvestorRole_shouldPassAccessControl() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        Exception exception = org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> mockMvc.perform(post("/api/investment/fund")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"landId\":1,\"amount\":50000}"))
        );
        org.junit.jupiter.api.Assertions.assertTrue(exception.getCause() instanceof RuntimeException);
        org.junit.jupiter.api.Assertions.assertEquals("Investor not found", exception.getCause().getMessage());
    }

    // ── AC-4: FARMER role is rejected from POST /api/investment/fund with 403
    // ──────────
    @Test
    void fundLand_withFarmerRole_shouldReturn403() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(post("/api/investment/fund")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"landId\":1,\"amount\":50000}"))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: ADMIN role is rejected from POST /api/investment/fund with 403
    // ──────────
    @Test
    void fundLand_withAdminRole_shouldReturn403() throws Exception {
        stubValidTokenWithRole("ADMIN");

        mockMvc.perform(post("/api/investment/fund")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"landId\":1,\"amount\":50000}"))
                .andExpect(status().isForbidden());
    }

    // ── AC-4: AUDITOR role is rejected from POST /api/investment/fund with 403
    // ──────────
    @Test
    void fundLand_withAuditorRole_shouldReturn403() throws Exception {
        stubValidTokenWithRole("AUDITOR");

        mockMvc.perform(post("/api/investment/fund")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"landId\":1,\"amount\":50000}"))
                .andExpect(status().isForbidden());
    }

    // ── AC-3: Missing Authorization header on fund endpoint returns 401
    // ───────────────────
    @Test
    void fundLand_withMissingAuthorizationHeader_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/investment/fund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"landId\":1,\"amount\":50000}"))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-2: INVESTOR role is allowed to call GET /api/investment/contract/{id}
    // ─────────
    @Test
    void getContractLink_withInvestorRole_shouldPassAccessControl() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        Exception exception = org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> mockMvc.perform(get("/api/investment/contract/501")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
        );
        org.junit.jupiter.api.Assertions.assertTrue(exception.getCause() instanceof RuntimeException);
        org.junit.jupiter.api.Assertions.assertEquals("Investment not found", exception.getCause().getMessage());
    }

    // ── AC-4: FARMER role is rejected from GET /api/investment/contract/{id} with
    // 403 ──
    @Test
    void getContractLink_withFarmerRole_shouldReturn403() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(get("/api/investment/contract/501")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    // ── AC-3: Missing Authorization header on contract endpoint returns 401
    // ─────────────
    @Test
    void getContractLink_withMissingAuthorizationHeader_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/investment/contract/501"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper
    // ────────────────────────────────────────────────────────────────────────────
    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("10");
    }
}