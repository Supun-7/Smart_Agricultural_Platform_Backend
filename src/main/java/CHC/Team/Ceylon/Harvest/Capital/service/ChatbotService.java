package CHC.Team.Ceylon.Harvest.Capital.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ChatbotService {

    private final AiChatService aiChatService;
    private final String systemPrompt;

    public ChatbotService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
        this.systemPrompt = loadSystemPrompt();
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource res = new ClassPathResource("chatbot-context.txt");
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "You are a helpful assistant for Ceylon Harvest Capital. Reply in Sinhala.";
        }
    }

    // AC-5: history lives in HttpSession — survives across messages, dies on browser close
    public String chat(String userMessage, HttpSession session) {
        List<Map<String, String>> history =
            (List<Map<String, String>>) session.getAttribute("chatHistory");
        if (history == null) history = new ArrayList<>();

        history.add(Map.of("role", "user", "content", userMessage));
        String reply = aiChatService.sendMessage(systemPrompt, history);
        history.add(Map.of("role", "assistant", "content", reply));

        session.setAttribute("chatHistory", history);
        return reply;
    }

    public void clearHistory(HttpSession session) {
        session.removeAttribute("chatHistory");
    }
}