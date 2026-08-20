package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.TalqError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Error mapping for the TALQ gateway-server surface only: the CMS expects
 * every error body to be a JSON <b>array</b> of TALQ error messages —
 * never RFC 7807 — so this advice is scoped to this package and takes
 * precedence over {@link com.exati.itg.exception.GlobalExceptionHandler}.
 */
@Slf4j
// Must outrank GlobalExceptionHandler: its catch-all Exception handler would
// otherwise swallow TalqApiException into an RFC 7807 500 for these routes.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.exati.itg.talqserver")
public class TalqServerAdvice {

    @ExceptionHandler(TalqApiException.class)
    public ResponseEntity<List<TalqError>> handleTalq(TalqApiException ex) {
        log.debug("TALQ error {} -> {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(List.of(ex.getError()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<List<TalqError>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(List.of(TalqError.payload("malformed JSON payload")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<List<TalqError>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(List.of(TalqError.payload("required parameter '" + ex.getParameterName() + "' is missing")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<List<TalqError>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest()
                .body(List.of(TalqError.payload("required header '" + ex.getHeaderName() + "' is missing")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<List<TalqError>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception on TALQ server route", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of(TalqError.payload("unexpected gateway error")));
    }
}
