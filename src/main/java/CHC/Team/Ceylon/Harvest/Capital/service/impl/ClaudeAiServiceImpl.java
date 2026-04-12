package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.service.AiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.*;

// @Primary
@Service
public class ClaudeAiServiceImpl implements AiChatService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sendMessage(String systemPrompt, List<Map<String, String>> history) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("max_tokens", 1024);
            body.put("system", systemPrompt);
            body.put("messages", history);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> responseBody =
                objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> content =
                (List<Map<String, Object>>) responseBody.get("content");

            return (String) content.get(0).get("text");

        } catch (Exception e) {
            // Sinhala fallback error — AC-6
            return "සමාවෙන්න, දැනට සේවාව ලබා ගත නොහැක. පසුව උත්සාහ කරන්න.";
        }
    }
}
