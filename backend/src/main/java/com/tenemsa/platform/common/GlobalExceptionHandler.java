package com.tenemsa.platform.common;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import org.hibernate.LazyInitializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safety net for exceptions that reach the controller layer uncaught.
 * Before this existed, an uncaught exception anywhere (like the
 * HttpMessageConversionException from the Hibernate-proxy/Jackson bug this
 * class was added alongside — see UserRepository and UserResponseDTO) fell
 * all the way through to a raw container-level error page with a full stack
 * trace, instead of a clean, consistent JSON error response.
 *
 * This does NOT replace the specific try/catch blocks already in
 * AuthController/AdminController for expected failure cases (invalid
 * credentials, frozen account, etc.) — those return deliberately-worded,
 * situation-specific messages and should stay exactly as they are. This
 * only catches what nothing else caught.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<?> handleLazyInit(LazyInitializationException e) {
        // Should be rare now (see UserRepository's JOIN FETCH queries and
        // Hibernate6Module in JacksonConfig), but if it ever recurs on some
        // other entity/relation this wasn't updated for, this at least turns
        // it into a clean 500 instead of a raw stack trace reaching the client.
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "A data-loading error occurred. This has been logged.");
    }

    // Malformed JSON, a wrong type or an unknown enum value is the CLIENT's mistake, so 400,
    // not the 500 the broader conversion handler below would give (this class extends it).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "The request body is not valid. Check the field types and allowed values.");
    }

    @ExceptionHandler({InvalidDefinitionException.class, HttpMessageConversionException.class})
    public ResponseEntity<?> handleSerializationError(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "A response formatting error occurred. This has been logged.");
    }

    // e.g. ?status=NOPE on a filter: the client sent a value the parameter cannot take.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleBadParameter(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "'.");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "That file is larger than 10 MB.");
    }

    // ---- AI (Phase 6a): each failure has its own status so the UI can say something useful ----
    @ExceptionHandler(com.tenemsa.platform.ai.exception.AiUnavailableException.class)
    public ResponseEntity<?> handleAiUnavailable(com.tenemsa.platform.ai.exception.AiUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(com.tenemsa.platform.ai.exception.AiLimitExceededException.class)
    public ResponseEntity<?> handleAiLimit(com.tenemsa.platform.ai.exception.AiLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(com.tenemsa.platform.ai.exception.AiTimeoutException.class)
    public ResponseEntity<?> handleAiTimeout(com.tenemsa.platform.ai.exception.AiTimeoutException e) {
        return error(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler({com.tenemsa.platform.ai.exception.AiProviderException.class,
                       com.tenemsa.platform.ai.exception.AiOutputException.class})
    public ResponseEntity<?> handleAiFailure(RuntimeException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "You don't have permission to do that.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        // The first field message becomes the headline so the UI can show it as-is.
        String headline = fieldErrors.values().stream().findFirst().orElse("Validation failed.");
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, headline);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // Deliberately last/broadest. Every controller in this app already
    // throws plain RuntimeException for expected business failures — most
    // are caught locally with a specific status code, but any that aren't
    // land here as a 400 rather than a raw 500 stack trace.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "Request failed.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. This has been logged.");
    }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
