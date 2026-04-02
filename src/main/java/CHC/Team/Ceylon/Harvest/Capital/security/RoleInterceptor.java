package CHC.Team.Ceylon.Harvest.Capital.security;

import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public RoleInterceptor(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
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

        // 5. Extract userId and store in request attribute
        String userIdStr = jwtUtil.extractUserId(token);
        Long userId = Long.parseLong(userIdStr);
        request.setAttribute("userId", userId);

        // 6. AC-5 — Check if account is SUSPENDED.
        //    Wrapped in try/catch so that any DB issue (e.g. migration not yet
        //    applied, connection hiccup) never accidentally blocks valid users.
        //    A null accountStatus (pre-migration rows) is treated as ACTIVE.
        try {
            userRepository.findById(userId).ifPresent(user -> {
                AccountStatus status = user.getAccountStatus();
                if (status == AccountStatus.SUSPENDED) {
                    // Use a runtime exception to break out of the lambda;
                    // caught immediately below and converted to a 403.
                    throw new SuspendedAccountException();
                }
            });
        } catch (SuspendedAccountException ex) {
            response.sendError(403,
                "Account suspended. Please contact the platform administrator.");
            return false;
        } catch (Exception ex) {
            // DB or mapping error — log it but do NOT block the request.
            // The suspension check is a safety net; authentication already
            // passed via JWT above, so we allow the request through.
            System.err.println("[RoleInterceptor] accountStatus check failed " +
                "(non-fatal): " + ex.getMessage());
        }

        // 7. Extract role from token
        String userRoleStr = jwtUtil.extractRole(token);

        // 8. Convert string to enum safely
        Role userRole;
        try {
            userRole = Role.valueOf(userRoleStr);
        } catch (IllegalArgumentException e) {
            response.sendError(403, "Invalid role in token");
            return false;
        }

        // 9. Check if user's role is in the allowed list
        for (Role allowed : requiredRole.value()) {
            if (allowed == userRole) {
                return true; // ✅ access granted
            }
        }

        // 10. Role didn't match
        response.sendError(403, "Access denied — insufficient role");
        return false;
    }

    /** Sentinel exception used to signal suspension inside the lambda. */
    private static class SuspendedAccountException extends RuntimeException {
        SuspendedAccountException() { super(null, null, true, false); }
    }
}
