package ai.riskvision.graveyard.config;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Diagnostic failure analyzer that catches database connectivity issues
 * during Spring Boot startup and produces clean, actionable guidance.
 */
public class DatabaseConnectionFailureAnalyzer extends AbstractFailureAnalyzer<JDBCConnectionException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JDBCConnectionException cause) {
        String description = "Failed to establish JDBC connection to the target PostgreSQL database.\n" +
                "Error Details: " + (cause.getCause() != null ? cause.getCause().getMessage() : cause.getMessage());

        String action = "\n===============================================================================\n" +
                "DATABASE CONNECTION RECOVERY GUIDANCE:\n" +
                "1. Ensure the PostgreSQL / Supabase server is running and reachable on port 5432.\n" +
                "2. Verify SUPABASE_DB_HOST, SUPABASE_DB_NAME, SUPABASE_DB_USER, and SUPABASE_DB_PASSWORD\n" +
                "   environment variables in your environment or .env configuration.\n" +
                "3. Check your network or VPN settings to confirm access to external host.\n" +
                "===============================================================================\n";

        return new FailureAnalysis(description, action, cause);
    }
}
