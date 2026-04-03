package CHC.Team.Ceylon.Harvest.Capital;

import CHC.Team.Ceylon.Harvest.Capital.controller.AdminController;
import CHC.Team.Ceylon.Harvest.Capital.controller.AuditorController;
import CHC.Team.Ceylon.Harvest.Capital.controller.FarmerController;
import CHC.Team.Ceylon.Harvest.Capital.controller.InvestorController;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.security.RequiredRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RoleDefinitionAndControllerAnnotationTest {

    @Test
    void roleConstants_shouldContainAllRequiredRoles() {
        assertEquals(Set.of("FARMER", "INVESTOR", "AUDITOR", "ADMIN", "SYSTEM_ADMIN"),
                Arrays.stream(Role.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void everyEndpointInTheFourModules_shouldHaveRequiredRoleAnnotation() {
        List<Class<?>> controllers = List.of(
                FarmerController.class,
                InvestorController.class,
                AuditorController.class,
                AdminController.class
        );

        List<String> missingAnnotations = new ArrayList<>();

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (isRequestHandler(method) && method.getAnnotation(RequiredRole.class) == null) {
                    missingAnnotations.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(missingAnnotations.isEmpty(),
                "Missing @RequiredRole on endpoints: " + missingAnnotations);
    }

    @Test
    void adminEndpoints_shouldOnlyAllowAdminRole() {
        for (Method method : AdminController.class.getDeclaredMethods()) {
            if (!isRequestHandler(method)) {
                continue;
            }

            RequiredRole requiredRole = method.getAnnotation(RequiredRole.class);
            assertNotNull(requiredRole, "Expected @RequiredRole on " + method.getName());
            Set<Role> roles = Arrays.stream(requiredRole.value()).collect(Collectors.toSet());
            assertTrue(roles.contains(Role.ADMIN),
                    "Admin endpoint must allow ADMIN: " + method.getName());
            assertTrue(Set.of(Role.ADMIN, Role.SYSTEM_ADMIN).containsAll(roles),
                    "Admin endpoint must only allow ADMIN and SYSTEM_ADMIN: " + method.getName());
        }
    }

    @Test
    void moduleEndpoints_shouldUseConsistentSingleRoleProtection() {
        assertControllerEndpointsUseOnly(FarmerController.class, Role.FARMER);
        assertControllerEndpointsUseOnly(InvestorController.class, Role.INVESTOR);
        assertControllerEndpointsUseOnly(AuditorController.class, Role.AUDITOR);
    }

    private void assertControllerEndpointsUseOnly(Class<?> controller, Role expectedRole) {
        for (Method method : controller.getDeclaredMethods()) {
            if (!isRequestHandler(method)) {
                continue;
            }

            RequiredRole requiredRole = method.getAnnotation(RequiredRole.class);
            assertNotNull(requiredRole, "Expected @RequiredRole on " + controller.getSimpleName() + "." + method.getName());
            assertArrayEquals(new Role[]{expectedRole}, requiredRole.value(),
                    "Unexpected roles on " + controller.getSimpleName() + "." + method.getName());
        }
    }

    private boolean isRequestHandler(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(RequestMapping.class);
    }
}
