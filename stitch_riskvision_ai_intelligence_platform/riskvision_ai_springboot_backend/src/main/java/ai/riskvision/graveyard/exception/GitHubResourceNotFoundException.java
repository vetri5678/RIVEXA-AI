package ai.riskvision.graveyard.exception;

import org.springframework.http.HttpStatus;

public class GitHubResourceNotFoundException extends GitHubApiException {
    public GitHubResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    public GitHubResourceNotFoundException(String message, String endpoint, String details) {
        super(message, HttpStatus.NOT_FOUND, endpoint, details);
    }
}
