package dev.ncc.webhook.common;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final Clock clock;

  public ApiExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(NotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), Map.of());
  }

  @ExceptionHandler({VersionConflictException.class, OptimisticLockingFailureException.class})
  ResponseEntity<ApiError> handleVersionConflict(RuntimeException exception) {
    String message =
        exception instanceof VersionConflictException
            ? exception.getMessage()
            : "Webhook endpoint changed; refresh it before retrying the update";
    return response(HttpStatus.CONFLICT, "version_conflict", message, Map.of());
  }

  @ExceptionHandler(DeliveryStateConflictException.class)
  ResponseEntity<ApiError> handleDeliveryStateConflict(DeliveryStateConflictException exception) {
    return response(
        HttpStatus.CONFLICT, "delivery_state_conflict", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
    return response(HttpStatus.BAD_REQUEST, "invalid_request", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableMessage() {
    return response(
        HttpStatus.BAD_REQUEST, "invalid_json", "Request body is not valid JSON", Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return response(
        HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", fields);
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    ApiError error =
        new ApiError(
            Instant.now(clock), status.value(), code, message, fields, MDC.get("requestId"));
    return ResponseEntity.status(status).body(error);
  }
}
