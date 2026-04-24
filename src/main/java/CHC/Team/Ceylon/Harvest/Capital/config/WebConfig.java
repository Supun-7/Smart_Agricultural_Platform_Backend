package CHC.Team.Ceylon.Harvest.Capital.config;

import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final String[] corsAllowedOrigins;

    public WebConfig(
            RoleInterceptor roleInterceptor,
            @Value("${app.cors.allowed-origins}") String corsAllowedOrigins
    ) {
        this.roleInterceptor = roleInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(corsAllowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
            .addPathPatterns("/api/**", "/farmer/**")
            .excludePathPatterns(
                "/api/users/register",
                "/api/users/login",
                "/api/gate/check",
                "/api/auth/google"
            );
    }
}
