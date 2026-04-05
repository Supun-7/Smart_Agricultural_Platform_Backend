package CHC.Team.Ceylon.Harvest.Capital.service;

import java.util.List;
import java.util.Map;

// Swap Claude → GPT → Gemini by just creating a new impl. Zero other changes.
public interface AiChatService {
    String sendMessage(String systemPrompt, List<Map<String, String>> history);
}