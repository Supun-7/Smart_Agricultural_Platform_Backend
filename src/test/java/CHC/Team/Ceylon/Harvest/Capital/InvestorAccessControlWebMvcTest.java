package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.InvestorController;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.InvestorDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvestorController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class InvestorAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private KycSubmissionRepository kycSubmissionRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private InvestorDashboardService investorDashboardService;

    @Test
    void investorEndpoint_shouldAllowInvestorRole() throws Exception {
        stubValidTokenWithRole("INVESTOR");

        mockMvc.perform(get("/api/investor/opportunities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk());
    }

    @Test
    void investorEndpoint_shouldRejectFarmerRoleWith403() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(get("/api/investor/opportunities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isForbidden());
    }

    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("1");
    }
}
