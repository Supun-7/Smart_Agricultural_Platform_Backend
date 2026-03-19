package CHC.Team.Ceylon.Harvest.Capital.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=", 3600000L);
    }

    @Test
    void generateToken_shouldContainUserIdRoleAndVerificationStatus() {
        String token = jwtUtil.generateToken(99L, "ADMIN", "VERIFIED");

        assertNotNull(token);
        assertEquals("99", jwtUtil.extractUserId(token));
        assertEquals("ADMIN", jwtUtil.extractRole(token));
        assertEquals("VERIFIED", jwtUtil.extractVerificationStatus(token));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.validateToken("this-is-not-a-valid-jwt"));
    }
}
