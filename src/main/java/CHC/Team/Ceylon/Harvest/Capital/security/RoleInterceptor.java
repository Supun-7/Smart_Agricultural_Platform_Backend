package CHC.Team.Ceylon.Harvest.Capital.security;

import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public RoleInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        // 1. Only process actual controller methods
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        // 2. Check if this endpoint has @RequiredRole
        RequiredRole requiredRole = method.getMethodAnnotation(RequiredRole.class);
        if (requiredRole == null) {
            return true;
        }

        // 3. Get the Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(401, "Missing or invalid Authorization header");
            return false;
        }

        // 4. Extract and validate the token
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.sendError(401, "Invalid or expired token");
            return false;
        }

        // 5. ── NEW — extract userId and store in request ──────────────────
        //    Any controller can now do: request.getAttribute("userId")
        //    extractUserId() returns a String, so we parse it to Long
        String userIdStr = jwtUtil.extractUserId(token);
        request.setAttribute("userId", Long.parseLong(userIdStr));
        // ────────────────────────────────────────────────────────────────

        // 6. Extract role from token
        String userRoleStr = jwtUtil.extractRole(token);

        // 7. Convert string to enum safely
        Role userRole;
        try {
            userRole = Role.valueOf(userRoleStr);
        } catch (IllegalArgumentException e) {
            response.sendError(403, "Invalid role in token");
            return false;
        }

        // 8. Check if user's role is in the allowed list
        for (Role allowed : requiredRole.value()) {
            if (allowed == userRole) {
                return true; // ✅ access granted
            }
        }

        // 9. Role didn't match
        response.sendError(403, "Access denied — insufficient role");
        return false;
    }
}