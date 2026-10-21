package com.dev.seatbooking.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ErrorResponse(e.code(), e.getMessage()));
    }

    @ExceptionHandler(SeatUnavailableException.class)
    ResponseEntity<ErrorResponse> seat(SeatUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SEAT_UNAVAILABLE", e.getMessage(), e.getSeatIds()));
    }

    @ExceptionHandler(OptimisticConflictException.class)
    ResponseEntity<ErrorResponse> optimisticConflict(OptimisticConflictException e) {
        log.warn("Optimistic conflict on seats: {}", e.getSeatIds());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SEAT_UNAVAILABLE", "One or more seats are not available", e.getSeatIds()));
    }

    @ExceptionHandler(EventClosedException.class)
    ResponseEntity<ErrorResponse> closed(EventClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EVENT_CLOSED", e.getMessage(), null));
    }

    @ExceptionHandler(ValidationFailedException.class)
    ResponseEntity<ErrorResponse> invalid(ValidationFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> notReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "Request validation failed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> objectOptimisticConflict(ObjectOptimisticLockingFailureException e) {
        log.warn("JPA optimistic lock conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SEAT_UNAVAILABLE", "One or more seats are not available"));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> pessimisticLockConflict(PessimisticLockingFailureException e) {
        if (isDeadlock(e)) {
            log.error("Database deadlock detected: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("DEADLOCK", "Database deadlock detected"));
        }
        log.warn("Pessimistic lock conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SEAT_UNAVAILABLE", "One or more seats are not available"));
    }

    private static boolean isDeadlock(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx && "40P01".equals(sqlEx.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> catchAll(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse spring) {
            int sc = spring.getStatusCode().value();
            String code = sc == 400 ? "VALIDATION_FAILED" : "REQUEST_REJECTED";
            return ResponseEntity.status(sc).body(new ErrorResponse(code, "Request rejected"));
        }
        log.error("Unhandled exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

