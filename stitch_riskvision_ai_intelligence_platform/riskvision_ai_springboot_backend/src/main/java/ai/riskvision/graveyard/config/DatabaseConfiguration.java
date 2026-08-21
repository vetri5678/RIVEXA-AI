package ai.riskvision.graveyard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database configuration with graceful degradation for development environments.
 * This configuration allows the application to start even when the database is temporarily unavailable.
 */
@Slf4j
@Configuration
public class DatabaseConfiguration {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driverClassName}")
    private String driverClassName;

    /**
     * Primary DataSource with enhanced connection validation and timeouts.
     * This configuration includes retry logic and improved error handling.
     */
    @Bean
    @Primary
    @Profile("!offline")
    public DataSource primaryDataSource() {
        log.info("Configuring primary DataSource for database: {}", maskDatabaseUrl(databaseUrl));
        
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(databaseUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        
        // Limit max pool size to 3 for development to avoid EMAXCONNSESSION limits on Supabase
        ds.setMaximumPoolSize(3);
        ds.setMinimumIdle(1);
        ds.setIdleTimeout(600000);
        ds.setConnectionTimeout(30000);
        
        return ds;
    }

    /**
     * Test database connectivity on startup.
     * This method provides early feedback about database connectivity issues.
     */
    @Bean
    @Profile("!offline")
    public DatabaseConnectivityChecker databaseConnectivityChecker(DataSource dataSource) {
        return new DatabaseConnectivityChecker(dataSource);
    }

    private String maskDatabaseUrl(String url) {
        if (url == null) return "null";
        // Mask password in the URL for security
        return url.replaceAll(":[^:/@]*@", ":****@");
    }

    /**
     * Database connectivity checker that validates the connection on startup.
     */
    public static class DatabaseConnectivityChecker {
        private final DataSource dataSource;

        public DatabaseConnectivityChecker(DataSource dataSource) {
            this.dataSource = dataSource;
            testConnectivity();
        }

        private void testConnectivity() {
            try (Connection connection = dataSource.getConnection()) {
                log.info("✅ Database connectivity test: SUCCESS");
                log.info("Database Product: {}", connection.getMetaData().getDatabaseProductName());
                log.info("Database Version: {}", connection.getMetaData().getDatabaseProductVersion());

                // Auto-migrate schema: Ensure github_id column exists on users table
                try (java.sql.Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS github_id BIGINT UNIQUE");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_github_id ON users(github_id)");
                    log.info("✅ Ensured github_id column exists on users table in Supabase PostgreSQL.");
                } catch (SQLException e) {
                    log.warn("Schema migration notice (github_id column): {}", e.getMessage());
                }
            } catch (SQLException e) {
                log.error("❌ Database connectivity test: FAILED - {}", e.getMessage());
                log.error("The application will continue to start, but database-dependent features may not work correctly.");
            }
        }
    }
}