package com.eventsystem.infrastructure.api.exceptions;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.AccountSuspendedException;
import com.eventsystem.application.appexceptions.AccountSuspendedReadOnlyException;
import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.QueueAdmissionRequiredException;
import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String STORAGE_FAILURE_MESSAGE = "We could not complete your request right now due to a temporary problem. Please try again in a moment.";

    // 1. Handling authentication failures - not logged in (401 Unauthorized)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.UNAUTHORIZED, request);
    }

    // 2. Handling authorization failures - logged in but no access (403 Forbidden)
    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleNotAuthorizedException(NotAuthorizedException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(AccountSuspendedReadOnlyException.class)
    public ResponseEntity<Map<String, Object>> handleSuspendedReadOnlyException(AccountSuspendedReadOnlyException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    // 2b. Conflict situations - already exists (409 Conflict)
    @ExceptionHandler(AlreadyExistsOrderException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyExistsOrder(AlreadyExistsOrderException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(QueueAdmissionRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleQueueAdmissionRequired(QueueAdmissionRequiredException ex, HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    // 3. Handling not found exceptions - resources not found (404 Not Found)
    @ExceptionHandler({ MemberNotFoundException.class, OrderNotFoundException.class, LotteryNotFoundException.class })
    public ResponseEntity<Map<String, Object>> handleNotFoundExceptions(RuntimeException ex,
            HttpServletRequest request) {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    // 3b. Database failures - return a generic message, log the real cause (500
    // Internal Server Error)
    @ExceptionHandler({ DataAccessException.class, SQLException.class })
    public ResponseEntity<Map<String, Object>> handlePersistenceException(Exception ex, HttpServletRequest request) {
        log.error("Persistence failure on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString(), ex);
        return buildSafeErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", STORAGE_FAILURE_MESSAGE,
                request);
    }

    // 4. Handling domain and validation exceptions - other logic and domain errors
    // (400 Bad Request)
    // Catches exceptions like ActiveOrderHasExpiredException,
    // OrderViolatesPolicyException etc.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleDomainAndValidationExceptions(RuntimeException ex,
            HttpServletRequest request) {
        // If this is a general exception that wasn't caught above, return 400 with the
        // original message (V2 requirement for client)
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    //handling Checkout Ticket issuance failure - since it refunds and cancels order, needs valid response to it
    @ExceptionHandler(IssuanceFailedException.class)
    public ResponseEntity<Map<String, Object>> handleIssuanceFailedException(
            IssuanceFailedException ex,
            HttpServletRequest request) {
        return buildIssuanceFailedException(ex, request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(Exception ex, HttpStatus status,
            HttpServletRequest request) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", status.value());
        errorBody.put("errorType", ex.getClass().getSimpleName());
        errorBody.put("errorCode", errorCodeFor(ex));
        errorBody.put("message", ex.getMessage());

        errorBody.put("path", request.getRequestURI());

        return new ResponseEntity<>(errorBody, status);
    }

    private ResponseEntity<Map<String, Object>> buildSafeErrorResponse(Exception ex, HttpStatus status,
            String errorCode, String safeMessage, HttpServletRequest request) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", status.value());
        errorBody.put("errorType", ex.getClass().getSimpleName());
        errorBody.put("errorCode", errorCode);
        errorBody.put("message", safeMessage);
        errorBody.put("path", request.getRequestURI());
        return new ResponseEntity<>(errorBody, status);
    }

    private String errorCodeFor(Exception ex) {
        // Map exceptions to stable machine-readable error codes used by the UI.
        if (ex instanceof AccountSuspendedException) return "ACCOUNT_SUSPENDED";
        if (ex instanceof AccountSuspendedReadOnlyException) return "ACCOUNT_SUSPENDED";
        if (ex instanceof QueueAdmissionRequiredException) return "QUEUE_REQUIRED";
        if (ex instanceof AuthenticationException) return "AUTH_INVALID";
        if (ex instanceof NotAuthorizedException || ex instanceof SecurityException) return "FORBIDDEN";
        if (ex instanceof AlreadyExistsOrderException) return "CONFLICT";
        if (ex instanceof MemberNotFoundException || ex instanceof OrderNotFoundException || ex instanceof LotteryNotFoundException) return "NOT_FOUND";
        // Default for domain/validation/runtime exceptions
        return "DOMAIN_ERROR";
    }

    private ResponseEntity<Map<String, Object>> buildIssuanceFailedException(
            IssuanceFailedException ex,
            HttpServletRequest request) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("status", HttpStatus.CONFLICT.value());
        errorBody.put("errorType", ex.getClass().getSimpleName());
        errorBody.put("errorCode", "TICKET_ISSUANCE_FAILURE");
        errorBody.put("message", ex.getMessage());
        errorBody.put("orderCancelled", true);
        errorBody.put("redirectToEvent", true);
        errorBody.put("path", request.getRequestURI());

        return new ResponseEntity<>(errorBody, HttpStatus.CONFLICT);
    }
    
}