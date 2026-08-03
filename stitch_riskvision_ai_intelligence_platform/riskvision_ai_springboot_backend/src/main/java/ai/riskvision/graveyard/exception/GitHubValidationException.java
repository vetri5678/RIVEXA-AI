package ai.riskvision.graveyard.exception;

import org.springframework.http.HttpStatus;

public class GitHubValidationException extends GitHubApiException {
    public GitHubValidationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public GitHubValidationException(String message, String endpoint, String details) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, endpoint, details);
    }
}
