package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.dto.ChatMessageRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.ChatMessageResponse;
import CHC.Team.Ceylon.Harvest.Capital.service.ChatbotService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            HttpSession session) {
        try {
            String reply = chatbotService.chat(request.getMessage(), session);
            return ResponseEntity.ok(new ChatMessageResponse(reply, true, null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ChatMessageResponse(
                    "සමාවෙන්න, දෝෂයක් ඇති විය. නැවත උත්සාහ කරන්න.",
                    false, e.getMessage()));
        }
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(HttpSession session) {
        chatbotService.clearHistory(session);
        return ResponseEntity.ok().build();
    }
}