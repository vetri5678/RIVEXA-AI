package ai.riskvision.graveyard.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Application-wide Spring beans for HTTP client and JSON serialisation.
 * Providing RestTemplate and ObjectMapper as beans ensures they are:
 *  - properly managed by the Spring container
 *  - injectable via @RequiredArgsConstructor / @Autowired
 *  - configurable in one central place
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate bean used by RepoPredictionService to call the Python ML service.
     * Using a Spring-managed instance (rather than `new RestTemplate()`) allows
     * interceptors, error handlers, or request factories to be wired in later.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * ObjectMapper bean with sensible defaults for the Spring Boot 3.x / Jakarta EE stack:
     *  - JavaTimeModule registered so LocalDateTime serialises to ISO-8601 strings
     *  - WRITE_DATES_AS_TIMESTAMPS disabled (human-readable dates in JSON)
     *  - FAIL_ON_UNKNOWN_PROPERTIES disabled (tolerant deserialization from ML service)
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
