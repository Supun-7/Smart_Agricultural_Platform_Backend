package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.service.AiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.*;

// @Primary
@Service
public class DeepSeekAiServiceImpl implements AiChatService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sendMessage(String systemPrompt, List<Map<String, String>> history) {
        try {
            // DeepSeek uses OpenAI format:
            // system prompt goes as the FIRST message with role "system"
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.addAll(history); // then the conversation history

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", 1024);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey) // different from Claude
                .POST(HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            // DeepSeek response format (OpenAI style):
            // { "choices": [{ "message": { "content": "reply here" } }] }
            Map<String, Object> responseBody =
                objectMapper.readValue(response.body(), Map.class);

            List<Map<String, Object>> choices =
                (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message =
                (Map<String, Object>) choices.get(0).get("message");

            return (String) message.get("content");

        } catch (Exception e) {
            return "සමාවෙන්න, දැනට සේවාව ලබා ගත නොහැක. පසුව උත්සාහ කරන්න.";
        }
    }
}