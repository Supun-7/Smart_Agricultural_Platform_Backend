package CHC.Team.Ceylon.Harvest.Capital.service;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// AC-4: The AI responds with relevant answers based on platform documentation.
// AC-5: Chat history is maintained during the user session.
// AC-6: The system handles empty or invalid inputs with appropriate messages.
@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock
    private AiChatService aiChatService;

    @Mock
    private HttpSession session;

    @InjectMocks
    private ChatbotService chatbotService;

    // ── AC-5: First message — history is initialised from session ─────────────
    @Test
    void chat_withNoExistingHistory_shouldInitialiseHistoryAndReturnReply() {
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("Welcome reply");

        String result = chatbotService.chat("Hello", session);

        assertThat(result).isEqualTo("Welcome reply");
        verify(session).setAttribute(eq("chatHistory"), anyList());
    }

    // ── AC-5: Subsequent message — existing history is preserved and extended ─
    @Test
    void chat_withExistingHistory_shouldAppendNewTurnAndPersistToSession() {
        List<Map<String, String>> existingHistory = new ArrayList<>();
        existingHistory.add(Map.of("role", "user",      "content", "What is KYC?"));
        existingHistory.add(Map.of("role", "assistant", "content", "KYC verifies your identity."));

        given(session.getAttribute("chatHistory")).willReturn(existingHistory);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("KYC takes 2–3 business days.");

        String result = chatbotService.chat("How long does KYC take?", session);

        assertThat(result).isEqualTo("KYC takes 2–3 business days.");

        // AC-5: the history stored back into the session must contain all four turns
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(session).setAttribute(eq("chatHistory"), captor.capture());

        List<Map<String, String>> savedHistory = captor.getValue();
        assertThat(savedHistory).hasSize(4);
        assertThat(savedHistory.get(2).get("role")).isEqualTo("user");
        assertThat(savedHistory.get(2).get("content")).isEqualTo("How long does KYC take?");
        assertThat(savedHistory.get(3).get("role")).isEqualTo("assistant");
        assertThat(savedHistory.get(3).get("content")).isEqualTo("KYC takes 2–3 business days.");
    }

    // ── AC-4: User message is forwarded to AiChatService with the system prompt ─
    @Test
    void chat_shouldForwardUserMessageAndSystemPromptToAiChatService() {
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("Platform answer");

        chatbotService.chat("How do I invest?", session);

        // AC-4: AiChatService must be called exactly once with a non-empty system prompt
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> historyCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        verify(aiChatService).sendMessage(promptCaptor.capture(), historyCaptor.capture());

        assertThat(promptCaptor.getValue()).isNotBlank();
        // The history passed to AI must contain the user's message
        List<Map<String, String>> historyPassedToAi = historyCaptor.getValue();
        assertThat(historyPassedToAi).anySatisfy(turn ->
                assertThat(turn.get("content")).isEqualTo("How do I invest?"));
    }

    // ── AC-4: Reply from AiChatService is returned verbatim to the controller ─
    @Test
    void chat_shouldReturnExactReplyFromAiChatService() {
        String expectedReply = "You can invest by selecting a farm project from the Investor Dashboard.";
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn(expectedReply);

        String actual = chatbotService.chat("How do I invest?", session);

        assertThat(actual).isEqualTo(expectedReply);
    }

    // ── AC-5: clearHistory removes the chatHistory attribute from the session ──
    @Test
    void clearHistory_shouldRemoveChatHistoryAttributeFromSession() {
        chatbotService.clearHistory(session);

        verify(session, times(1)).removeAttribute("chatHistory");
        verifyNoMoreInteractions(session);
    }

    // ── AC-5: History grows correctly over three consecutive turns ────────────
    @Test
    void chat_overThreeTurns_shouldAccumulateCorrectNumberOfHistoryEntries() {
        // Turn 1 — no prior history
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("Reply 1");
        chatbotService.chat("Question 1", session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> captor1 = ArgumentCaptor.forClass(List.class);
        verify(session, times(1)).setAttribute(eq("chatHistory"), captor1.capture());
        List<Map<String, String>> historyAfterTurn1 = new ArrayList<>(captor1.getValue());
        assertThat(historyAfterTurn1).hasSize(2); // user + assistant

        reset(session, aiChatService);

        // Turn 2 — history from turn 1 is loaded
        given(session.getAttribute("chatHistory")).willReturn(historyAfterTurn1);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("Reply 2");
        chatbotService.chat("Question 2", session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> captor2 = ArgumentCaptor.forClass(List.class);
        verify(session, times(1)).setAttribute(eq("chatHistory"), captor2.capture());
        List<Map<String, String>> historyAfterTurn2 = new ArrayList<>(captor2.getValue());
        assertThat(historyAfterTurn2).hasSize(4); // 2 prior + user + assistant

        reset(session, aiChatService);

        // Turn 3 — history from turns 1 & 2 is loaded
        given(session.getAttribute("chatHistory")).willReturn(historyAfterTurn2);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("Reply 3");
        chatbotService.chat("Question 3", session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> captor3 = ArgumentCaptor.forClass(List.class);
        verify(session, times(1)).setAttribute(eq("chatHistory"), captor3.capture());
        assertThat(captor3.getValue()).hasSize(6); // 4 prior + user + assistant
    }

    // ── AC-6: AiChatService failure propagates as an exception ───────────────
    @Test
    void chat_whenAiChatServiceThrows_shouldPropagateException() {
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList()))
                .willThrow(new RuntimeException("AI provider timeout"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> chatbotService.chat("Any question", session));
    }

    // ── AC-5: Each message is stored with the correct role labels ─────────────
    @Test
    void chat_shouldStoreHistoryWithCorrectRoleLabels() {
        given(session.getAttribute("chatHistory")).willReturn(null);
        given(aiChatService.sendMessage(anyString(), anyList())).willReturn("AI answer");

        chatbotService.chat("User question", session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(session).setAttribute(eq("chatHistory"), captor.capture());

        List<Map<String, String>> saved = captor.getValue();
        assertThat(saved.get(0).get("role")).isEqualTo("user");
        assertThat(saved.get(0).get("content")).isEqualTo("User question");
        assertThat(saved.get(1).get("role")).isEqualTo("assistant");
        assertThat(saved.get(1).get("content")).isEqualTo("AI answer");
    }
}