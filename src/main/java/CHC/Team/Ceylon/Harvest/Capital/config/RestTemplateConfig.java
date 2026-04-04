package CHC.Team.Ceylon.Harvest.Capital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a shared RestTemplate bean used by SupabaseStorageService
 * to call the Supabase Storage REST API.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
