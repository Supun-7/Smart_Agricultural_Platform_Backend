package CHC.Team.Ceylon.Harvest.Capital.security;

import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import java.lang.annotation.*;

// This annotation can only go on methods
@Target(ElementType.METHOD)
// Keep it alive at runtime so the interceptor can read it
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredRole {
    // Accepts one or more roles
    // Example: @RequiredRole({Role.FARMER, Role.ADMIN})
    Role[] value();
}