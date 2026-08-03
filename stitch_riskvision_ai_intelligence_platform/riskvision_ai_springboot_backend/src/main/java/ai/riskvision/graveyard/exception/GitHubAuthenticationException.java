package ai.riskvision.graveyard.exception;

import org.springframework.http.HttpStatus;

public class GitHubAuthenticationException extends GitHubApiException {
    public GitHubAuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public GitHubAuthenticationException(String message, String endpoint, String details) {
        super(message, HttpStatus.UNAUTHORIZED, endpoint, details);
    }
}
