package CHC.Team.Ceylon.Harvest.Capital.config;

import CHC.Team.Ceylon.Harvest.Capital.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public WebConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(
                "http://localhost:[*]",   // any localhost port — covers 5173, 3000, 4173
                "https://*.vercel.app",   // if you deploy frontend to Vercel later
                "https://*.netlify.app"   // or Netlify
            )
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