package ai.riskvision.graveyard.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GitHubRateLimitException extends GitHubApiException {
    private final Long rateLimitRemaining;
    private final Long rateLimitResetEpochSeconds;

    public GitHubRateLimitException(String message, Long remaining, Long resetEpochSeconds) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        this.rateLimitRemaining = remaining;
        this.rateLimitResetEpochSeconds = resetEpochSeconds;
    }

    public GitHubRateLimitException(String message, Long remaining, Long resetEpochSeconds, String endpoint, String details) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, endpoint, details);
        this.rateLimitRemaining = remaining;
        this.rateLimitResetEpochSeconds = resetEpochSeconds;
    }
}
