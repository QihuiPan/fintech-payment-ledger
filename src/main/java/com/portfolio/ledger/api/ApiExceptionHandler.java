package com.portfolio.ledger.api;

import java.time.Instant;
import java.util.Map;

import com.portfolio.ledger.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handleDomain(DomainException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(
                        exception.code(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        Instant.now(),
                        null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED",
                "Request validation failed",
                request.getRequestURI(),
                Instant.now(),
                fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "CONSTRAINT_CONFLICT",
                "The request conflicts with an existing record or ledger constraint",
                request.getRequestURI(),
                Instant.now(),
                null));
    }

    public record ApiError(
            String code,
            String message,
            String path,
            Instant timestamp,
            Map<String, String> fields) {
    }
}
