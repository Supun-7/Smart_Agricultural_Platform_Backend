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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
            .addPathPatterns("/api/**")       
            .excludePathPatterns(
                "/api/users/register",        
                "/api/users/login",        
                "/api/gate/check"             
            );
    }
}