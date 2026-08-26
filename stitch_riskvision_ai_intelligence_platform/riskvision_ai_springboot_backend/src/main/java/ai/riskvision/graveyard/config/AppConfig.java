package ai.riskvision.graveyard.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Application-wide Spring beans for HTTP client and JSON serialisation.
 * Providing RestTemplate and ObjectMapper as beans ensures they are:
 *  - properly managed by the Spring container
 *  - injectable via @RequiredArgsConstructor / @Autowired
 *  - configurable in one central place
 */
@Configuration
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.cache.annotation.EnableCaching
public class AppConfig {

    @Bean
    public org.springframework.cache.CacheManager cacheManager() {
        return new org.springframework.cache.concurrent.ConcurrentMapCacheManager(
                "githubMetadata", "githubLanguages", "githubReadme",
                "githubBranches", "githubContributors", "githubCommits", "githubUserProfile"
        );
    }

    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }

    /**
     * Dedicated thread pool for async email delivery.
     *
     * <p>Isolates email sends from the default Spring async executor so that slow SMTP
     * responses or transient mail-server errors never delay the OAuth redirect thread.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Core threads: 2  — always alive, handle steady-state login volume
     *   <li>Max threads: 5   — burst capacity for login spikes
     *   <li>Queue capacity: 50 — backlog before additional threads are created
     *   <li>Keep-alive: 30 s — idle threads beyond core size are recycled after 30 seconds
     * </ul>
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("email-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * RestTemplate bean used by RepoPredictionService to call the Python ML service.
     * Using a Spring-managed instance (rather than `new RestTemplate()`) allows
     * interceptors, error handlers, or request factories to be wired in later.
     */
    @Bean
    public RestTemplate restTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(180000);
        return new RestTemplate(factory);
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
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
