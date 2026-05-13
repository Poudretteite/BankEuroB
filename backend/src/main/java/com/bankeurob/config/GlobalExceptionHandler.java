package com.bankeurob.config;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Globalny handler wyjątków – mapuje wyjątki na czytelne odpowiedzi JSON
 * z odpowiednimi kodami HTTP, zamiast zwracać 500 Internal Server Error.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Błędy walidacji biznesowej (np. nieprawidłowy kod kraju, zły PIN, brak środków)
     * → HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Błędy stanu (np. konto nieaktywne, niewystarczające środki)
     * → HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Bad request (state): {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Błędy autoryzacji – brak dostępu do zasobu
     * → HTTP 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Błędy walidacji pól (@Valid) – np. nieprawidłowy email, zbyt krótkie hasło
     * → HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation errors: {}", errors);
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errors);
    }

    /**
     * Wszystkie inne nieobsłużone wyjątki
     * → HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Wewnętrzny błąd serwera: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
