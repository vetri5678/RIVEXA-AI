package ai.riskvision.graveyard.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class GitHubApiException extends RuntimeException {
    private final HttpStatus status;
    private final String endpoint;
    private final String details;

    public GitHubApiException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR, null, null);
    }

    public GitHubApiException(String message, HttpStatus status) {
        this(message, status, null, null);
    }

    public GitHubApiException(String message, HttpStatus status, String endpoint, String details) {
        super(message);
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.endpoint = endpoint;
        this.details = details;
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.endpoint = null;
        this.details = cause != null ? cause.getMessage() : null;
    }
}
