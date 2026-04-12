package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.service.AiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.util.*;

@Primary
@Service
public class GeminiAiServiceImpl implements AiChatService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sendMessage(String systemPrompt, List<Map<String, String>> history) {
        try {
            // ── Build contents array from history ──────────────────
            List<Map<String, Object>> contents = new ArrayList<>();

            for (Map<String, String> msg : history) {
                String role = msg.get("role").equals("assistant") ? "model" : "user";

                Map<String, Object> part = new HashMap<>();
                part.put("text", msg.get("content"));

                Map<String, Object> content = new HashMap<>();
                content.put("role", role);
                content.put("parts", List.of(part));

                contents.add(content);
            }

            // ── System instruction (Gemini's way of system prompt) ──
            Map<String, Object> systemPart = new HashMap<>();
            systemPart.put("text", systemPrompt);

            Map<String, Object> systemInstruction = new HashMap<>();
            systemInstruction.put("parts", List.of(systemPart));

            // ── Full request body ───────────────────────────────────
            Map<String, Object> body = new HashMap<>();
            body.put("systemInstruction", systemInstruction);
            body.put("contents", contents);

            String requestJson = objectMapper.writeValueAsString(body);

            // ── API URL — key goes as query param for Gemini ────────
            String baseUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
            String url = baseUrl + model + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

            // ── Log raw response for debugging ──────────────────────
            System.out.println("Gemini status: " + response.statusCode());
            System.out.println("Gemini response: " + response.body());

            // ── Parse response ──────────────────────────────────────
            // Gemini returns: { candidates: [{ content: { parts: [{ text }] } }] }
            Map<String, Object> responseBody =
                objectMapper.readValue(response.body(), Map.class);

            List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) responseBody.get("candidates");
            Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");

            return (String) parts.get(0).get("text");

        } catch (Exception e) {
            System.err.println("Gemini API ERROR: " + e.getClass().getName() + " — " + e.getMessage());
            e.printStackTrace();
            return "සමාවෙන්න, දැනට සේවාව ලබා ගත නොහැක. පසුව උත්සාහ කරන්න.";
        }
    }
}
