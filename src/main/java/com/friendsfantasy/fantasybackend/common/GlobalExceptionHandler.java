package com.friendsfantasy.fantasybackend.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API exception", ex);
        } else {
            log.warn("API exception: {} {}", ex.getStatus().value(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Application state error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Service temporarily unavailable"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Request conflicts with existing data"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Request failed"
                : ex.getMessage();
        HttpStatus status = classifyRuntimeStatus(message);

        if (status.is5xxServerError()) {
            log.error("Runtime request failure", ex);
        } else {
            log.warn("Runtime request failure: {} {}", status.value(), message);
        }

        return ResponseEntity.status(status)
                .body(ApiResponse.fail(message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Resource not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail("Request method '" + ex.getMethod() + "' is not supported"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Internal server error"));
    }

    private HttpStatus classifyRuntimeStatus(String message) {
        String normalized = message.toLowerCase();

        if (normalized.contains("not found")
                || normalized.contains("no user found")
                || normalized.contains("stats not found")) {
            return HttpStatus.NOT_FOUND;
        }

        if (normalized.contains("invalid credentials")
                || normalized.contains("invalid refresh token")
                || normalized.contains("refresh token expired")
                || normalized.contains("refresh session not found")
                || normalized.contains("session not found")) {
            return HttpStatus.UNAUTHORIZED;
        }

        if (normalized.contains("not active")
                || normalized.contains("viewed after the match starts")) {
            return HttpStatus.FORBIDDEN;
        }

        if (normalized.contains("already")
                || normalized.contains("full")
                || normalized.contains("closed")
                || normalized.contains("expired")
                || normalized.contains("inactive")
                || normalized.contains("no longer")
                || normalized.contains("contest is not open")
                || normalized.contains("not configured")) {
            return HttpStatus.CONFLICT;
        }

        return HttpStatus.BAD_REQUEST;
    }
}
