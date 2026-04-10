package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.ChatMessageRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.ChatMessageResponse;
import CHC.Team.Ceylon.Harvest.Capital.service.ChatbotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AC-3: Users can type and send questions through the chat interface.
// AC-4: The AI responds with relevant answers based on platform documentation.
// AC-5: Chat history is maintained during the user session.
// AC-6: The system handles empty or invalid inputs with appropriate messages.
@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

    @Mock
    private ChatbotService chatbotService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ChatbotController chatbotController = new ChatbotController(chatbotService);
        mockMvc = MockMvcBuilders.standaloneSetup(chatbotController).build();
        objectMapper = new ObjectMapper();
    }

    // ── AC-3 + AC-4 ──────────────────────────────────────────────────────────
    @Test
    void sendMessage_shouldReturnAiReplyWithSuccessTrue() throws Exception {
        String userQuestion = "How do I register my land?";
        String aiReply = "You can register your land by navigating to the Farmer Dashboard and clicking 'Register Land'.";

        when(chatbotService.chat(anyString(), any(HttpSession.class))).thenReturn(aiReply);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage(userQuestion);

        // AC-3: POST /api/chatbot/message is the send-message endpoint
        // AC-4: response body contains the AI-generated reply
        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(aiReply))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").isEmpty());

        verify(chatbotService).chat(eq(userQuestion), any(HttpSession.class));
    }

    // ── AC-4: AI reply is specific to the platform context ───────────────────
    @Test
    void sendMessage_shouldReturnPlatformContextualReply() throws Exception {
        String userQuestion = "What is Ceylon Harvest Capital?";
        String platformReply = "Ceylon Harvest Capital is an agricultural investment platform connecting farmers and investors.";

        when(chatbotService.chat(anyString(), any(HttpSession.class))).thenReturn(platformReply);

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage(userQuestion);

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(platformReply))
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── AC-5: Session is passed to service so history persists ───────────────
    @Test
    void sendMessage_shouldPassHttpSessionToServiceForHistoryMaintenance() throws Exception {
        when(chatbotService.chat(anyString(), any(HttpSession.class))).thenReturn("Session-aware reply");

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("Tell me about investment milestones");

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify the session object is forwarded — this is what allows history to persist (AC-5)
        verify(chatbotService, times(1)).chat(anyString(), any(HttpSession.class));
    }

    // ── AC-5: Multiple messages in one session accumulate in history ──────────
    @Test
    void sendMessage_multipleTurns_shouldInvokeServiceForEachMessageWithSameSession() throws Exception {
        when(chatbotService.chat(anyString(), any(HttpSession.class)))
                .thenReturn("First answer")
                .thenReturn("Second answer");

        ChatMessageRequest firstRequest = new ChatMessageRequest();
        firstRequest.setMessage("What is KYC?");

        ChatMessageRequest secondRequest = new ChatMessageRequest();
        secondRequest.setMessage("How long does KYC take?");

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("First answer"));

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Second answer"));

        verify(chatbotService, times(2)).chat(anyString(), any(HttpSession.class));
    }

    // ── AC-6: Empty message is rejected with 400 ─────────────────────────────
    @Test
    void sendMessage_withBlankMessage_shouldReturn400DueToValidationConstraint() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("");   // @NotBlank on ChatMessageRequest.message

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatbotService);
    }

    // ── AC-6: Null message is rejected with 400 ──────────────────────────────
    @Test
    void sendMessage_withNullMessage_shouldReturn400DueToValidationConstraint() throws Exception {
        String bodyWithNullMessage = "{\"message\": null}";

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithNullMessage))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatbotService);
    }

    // ── AC-6: Message exceeding 1000 characters is rejected ──────────────────
    @Test
    void sendMessage_withMessageExceedingMaxLength_shouldReturn400() throws Exception {
        String oversizedMessage = "A".repeat(1001);   // @Size(max = 1000) on ChatMessageRequest.message
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage(oversizedMessage);

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatbotService);
    }

    // ── AC-6: Request body missing the message field entirely ────────────────
    @Test
    void sendMessage_withMissingMessageField_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatbotService);
    }

    // ── AC-4 + AC-6: Internal service failure returns 500 with error body ────
    @Test
    void sendMessage_whenServiceThrowsException_shouldReturn500WithErrorBody() throws Exception {
        when(chatbotService.chat(anyString(), any(HttpSession.class)))
                .thenThrow(new RuntimeException("AI provider unreachable"));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("What are the investment options?");

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("AI provider unreachable"));
    }

    // ── AC-5: DELETE /api/chatbot/history clears session history ─────────────
    @Test
    void clearHistory_shouldDelegateToServiceAndReturn200() throws Exception {
        doNothing().when(chatbotService).clearHistory(any(HttpSession.class));

        mockMvc.perform(delete("/api/chatbot/history"))
                .andExpect(status().isOk());

        verify(chatbotService, times(1)).clearHistory(any(HttpSession.class));
    }

    // ── AC-3: Whitespace-only message is treated as blank (invalid input) ─────
    @Test
    void sendMessage_withWhitespaceOnlyMessage_shouldReturn400() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMessage("     ");   // @NotBlank rejects whitespace-only strings

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatbotService);
    }
}