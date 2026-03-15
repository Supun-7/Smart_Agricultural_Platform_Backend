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
        //    (ignores things like static files)
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        // 2. Check if this endpoint has @RequiredRole
        //    If not — no restriction, let it pass
        RequiredRole requiredRole =
            method.getMethodAnnotation(RequiredRole.class);

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

        // 5. Extract role from token
        String userRoleStr = jwtUtil.extractRole(token);

        // 6. Convert string to enum safely
        Role userRole;
        try {
            userRole = Role.valueOf(userRoleStr);
        } catch (IllegalArgumentException e) {
            response.sendError(403, "Invalid role in token");
            return false;
        }

        // 7. Check if user's role is in the allowed list
        for (Role allowed : requiredRole.value()) {
            if (allowed == userRole) {
                return true; // ✅ access granted
            }
        }

        // 8. Role didn't match — AC-3 and AC-4 enforced here
        response.sendError(403, "Access denied — insufficient role");
        return false;
    }
}