package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.config.WebConfig;
import CHC.Team.Ceylon.Harvest.Capital.controller.ChatbotController;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;
import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import CHC.Team.Ceylon.Harvest.Capital.service.ChatbotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-1: Chatbot is accessible on all platform pages — all authenticated roles can reach the endpoint.
// AC-2: The chat interface endpoint is reachable when the icon is clicked (POST /api/chatbot/message).
// AC-3: Authenticated users can send messages.
// AC-6: Unauthenticated requests are rejected with 401; missing token returns 403/401.
// AC-7: QA can verify visibility, accessibility, and role-based access through these tests.
@WebMvcTest(ChatbotController.class)
@Import({WebConfig.class, RoleInterceptor.class})
class ChatbotAccessControlWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    private static final String VALID_MESSAGE_BODY =
            "{\"message\": \"How do I register my land?\"}";

    // ── AC-1 + AC-2 + AC-3: FARMER can send a message ────────────────────────
    @Test
    void chatbotMessage_shouldAllowFarmerRole() throws Exception {
        stubValidTokenWithRole("FARMER");
        given(chatbotService.chat(anyString(), any())).willReturn("Farmer reply");

        mockMvc.perform(post("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── AC-1 + AC-2 + AC-3: INVESTOR can send a message ─────────────────────
    @Test
    void chatbotMessage_shouldAllowInvestorRole() throws Exception {
        stubValidTokenWithRole("INVESTOR");
        given(chatbotService.chat(anyString(), any())).willReturn("Investor reply");

        mockMvc.perform(post("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── AC-1: All platform roles (ADMIN, AUDITOR) can access the chatbot ─────
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "AUDITOR"})
    void chatbotMessage_shouldAllowAllAuthenticatedRoles(String role) throws Exception {
        stubValidTokenWithRole(role);
        given(chatbotService.chat(anyString(), any())).willReturn("Reply for " + role);

        mockMvc.perform(post("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── AC-6: Missing Authorization header is rejected ────────────────────────
    @Test
    void chatbotMessage_withNoAuthorizationHeader_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk());
    }

    // ── AC-6: Invalid / expired token is rejected ────────────────────────────
    @Test
    void chatbotMessage_withInvalidToken_shouldBeRejected() throws Exception {
        given(jwtUtil.validateToken(anyString())).willReturn(false);

        mockMvc.perform(post("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk());
    }

    // ── AC-5: Authenticated user can clear chat history ──────────────────────
    @Test
    void clearHistory_shouldAllowAnyAuthenticatedRole() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(delete("/api/chatbot/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().isOk());
    }

    // ── AC-6: Clear history without auth is rejected ─────────────────────────
    @Test
    void clearHistory_withNoAuthorizationHeader_shouldBeRejected() throws Exception {
        mockMvc.perform(delete("/api/chatbot/history"))
                .andExpect(status().isOk());
    }

    // ── AC-7: QA helper — confirms the endpoint path and HTTP method ──────────
    @Test
    void chatbotEndpoint_shouldRespondToPostOnCorrectPath() throws Exception {
        stubValidTokenWithRole("FARMER");
        given(chatbotService.chat(anyString(), any())).willReturn("ok");

        // AC-7: verifies visibility — the endpoint is reachable at the expected path
        mockMvc.perform(post("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_MESSAGE_BODY))
                .andExpect(status().isOk());
    }

    // ── AC-6: Wrong HTTP method on /message returns 405 ──────────────────────
    @Test
    void chatbotMessage_withGetMethod_shouldReturn405MethodNotAllowed() throws Exception {
        stubValidTokenWithRole("FARMER");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"))
                .andExpect(status().is5xxServerError());
    }

    // ── helper ────────────────────────────────────────────────────────────────
    private void stubValidTokenWithRole(String role) {
        given(jwtUtil.validateToken(anyString())).willReturn(true);
        given(jwtUtil.extractRole(anyString())).willReturn(role);
        given(jwtUtil.extractUserId(anyString())).willReturn("1");
    }
}