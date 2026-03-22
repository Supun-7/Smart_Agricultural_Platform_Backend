package CHC.Team.Ceylon.Harvest.Capital.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@Service
public class GoogleService {

    @Value("${google.client.id}")
    private String clientId;

    // ── Verify Google ID token (used by popup flow) ───────────
    public GoogleIdToken.Payload verifyToken(String idTokenString) throws Exception {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        ).setAudience(Collections.singletonList(clientId)).build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken != null) {
            return idToken.getPayload();
        } else {
            throw new Exception("Invalid Google token");
        }
    }

    // ── Exchange authorization code for user info (redirect flow) ──
    // This is the standard OAuth2 authorization code flow:
    // 1. Exchange code for access token at Google's token endpoint
    // 2. Use access token to get user info from Google's userinfo endpoint
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCodeForUserInfo(
            String code, String clientId, String clientSecret, String redirectUri)
            throws Exception {

        // Step 1 — Exchange code for tokens
        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        String tokenParams = "code="          + code
                + "&client_id="     + clientId
                + "&client_secret=" + clientSecret
                + "&redirect_uri="  + redirectUri
                + "&grant_type=authorization_code";

        URL tokenUrl = new URL(tokenEndpoint);
        HttpURLConnection tokenConn = (HttpURLConnection) tokenUrl.openConnection();
        tokenConn.setRequestMethod("POST");
        tokenConn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
        tokenConn.setDoOutput(true);

        try (OutputStream os = tokenConn.getOutputStream()) {
            os.write(tokenParams.getBytes(StandardCharsets.UTF_8));
        }

        String tokenResponse;
        try (InputStream is = tokenConn.getInputStream()) {
            tokenResponse = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, Object> tokenData =
                new Gson().fromJson(tokenResponse, Map.class);
        String accessToken = (String) tokenData.get("access_token");

        if (accessToken == null) {
            throw new Exception("Failed to get access token from Google");
        }

        // Step 2 — Use access token to get user info
        String userInfoEndpoint = "https://www.googleapis.com/oauth2/v2/userinfo";
        URL userInfoUrl = new URL(userInfoEndpoint);
        HttpURLConnection userInfoConn =
                (HttpURLConnection) userInfoUrl.openConnection();
        userInfoConn.setRequestProperty("Authorization", "Bearer " + accessToken);

        String userInfoResponse;
        try (InputStream is = userInfoConn.getInputStream()) {
            userInfoResponse = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        return new Gson().fromJson(userInfoResponse, Map.class);
    }
}