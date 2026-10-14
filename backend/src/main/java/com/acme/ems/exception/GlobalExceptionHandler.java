package com.acme.ems.exception;

import jakarta.persistence.LockTimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiErrorResponse> api(ApiException ex, HttpServletRequest request) {
    return body(ex.status(), ex.code(), ex.getMessage(), Map.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors()
        .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", fields, request);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class,
      ConstraintViolationException.class})
  ResponseEntity<ApiErrorResponse> malformed(Exception ex, HttpServletRequest request) {
    return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request is malformed",
        Map.of(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException ex,
      HttpServletRequest request) {
    log.warn("Database constraint rejected {} {}", request.getMethod(), request.getRequestURI());
    return body(HttpStatus.CONFLICT, "DATA_CONFLICT",
        "The request conflicts with existing data", Map.of(), request);
  }

  @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
  ResponseEntity<ApiErrorResponse> dependencyUnavailable(Exception ex,
      HttpServletRequest request) {
    log.error("Database unavailable for {} {} requestId={}", request.getMethod(),
        request.getRequestURI(), requestId(request), ex);
    return body(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
        "A required service is temporarily unavailable", Map.of(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ApiErrorResponse> stale(ObjectOptimisticLockingFailureException ex,
      HttpServletRequest request) {
    return body(HttpStatus.CONFLICT, "STALE_UPDATE",
        "The record was changed by another request; reload and try again", Map.of(), request);
  }

  @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class})
  ResponseEntity<ApiErrorResponse> temporarilyUnavailable(Exception ex,
      HttpServletRequest request) {
    log.warn("Database lock unavailable for {} {} requestId={}", request.getMethod(),
        request.getRequestURI(), requestId(request), ex);
    return body(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_UNAVAILABLE",
        "The operation could not acquire a database lock; retry shortly", Map.of(), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(NoResourceFoundException ex,
      HttpServletRequest request) {
    return body(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", Map.of(), request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex,
      HttpServletRequest request) {
    return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
        "HTTP method is not supported for this resource", Map.of(), request);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiErrorResponse> authentication(AuthenticationException ex,
      HttpServletRequest request) {
    return body(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
        "Authentication is required", Map.of(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiErrorResponse> denied(AccessDeniedException ex, HttpServletRequest request) {
    return body(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", Map.of(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> other(Exception ex, HttpServletRequest request) {
    log.error("Unhandled failure for {} {} requestId={}", request.getMethod(),
        request.getRequestURI(), requestId(request), ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "An unexpected error occurred", Map.of(), request);
  }

  private ResponseEntity<ApiErrorResponse> body(HttpStatus status, String code, String message,
      Map<String, String> fields, HttpServletRequest request) {
    String requestId = requestId(request);
    return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(), status.value(),
        code, message, fields, requestId));
  }

  private String requestId(HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
  }
}
