package ai.riskvision.graveyard.config;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler that provides consistent error responses
 * across all controllers. Replaces per-controller try/catch blocks.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle IllegalArgumentException (thrown by AuthService for bad requests).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Handle validation errors on @Valid annotated request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.toList());
        log.warn("Validation failed: {}", errors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + String.join(", ", errors), request);
    }

    /**
     * Handle constraint violations (e.g., @RequestParam validation).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toList());
        log.warn("Constraint violation: {}", errors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Constraint violation: " + String.join(", ", errors),
                request);
    }

    /**
     * Handle malformed JSON or unreadable request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Malformed request body: " + ex.getMessage(), request);
    }

    /**
     * Handle GitHub API Rate Limit Exception.
     */
    @ExceptionHandler(ai.riskvision.graveyard.exception.GitHubRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubRateLimitException(
            ai.riskvision.graveyard.exception.GitHubRateLimitException ex, WebRequest request) {
        log.warn("GitHub API rate limit exceeded: {}", ex.getMessage());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("rate_limit_remaining", ex.getRateLimitRemaining());
        body.put("rate_limit_reset_epoch_seconds", ex.getRateLimitResetEpochSeconds());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, new HttpHeaders(), ex.getStatus());
    }

    /**
     * Handle GitHub API Exception hierarchy.
     */
    @ExceptionHandler(ai.riskvision.graveyard.exception.GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubApiException(
            ai.riskvision.graveyard.exception.GitHubApiException ex, WebRequest request) {
        log.warn("GitHub API exception [{}]: {}", ex.getStatus(), ex.getMessage());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getStatus().getReasonPhrase());
        body.put("message", ex.getMessage());
        if (ex.getEndpoint() != null) body.put("endpoint", ex.getEndpoint());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, new HttpHeaders(), ex.getStatus());
    }

    /**
     * Catch-all for any other unhandled exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(
            Exception ex, WebRequest request) {
        log.error("Unhandled exception: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message, WebRequest request) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, new HttpHeaders(), status);
    }
}
