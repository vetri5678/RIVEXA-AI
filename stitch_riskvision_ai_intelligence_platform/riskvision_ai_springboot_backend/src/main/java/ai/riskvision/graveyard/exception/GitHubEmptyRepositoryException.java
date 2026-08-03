package ai.riskvision.graveyard.exception;

import org.springframework.http.HttpStatus;

public class GitHubEmptyRepositoryException extends GitHubApiException {
    public GitHubEmptyRepositoryException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public GitHubEmptyRepositoryException(String message, String endpoint, String details) {
        super(message, HttpStatus.CONFLICT, endpoint, details);
    }
}
