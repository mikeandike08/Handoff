package com.mikeandike.handoff.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex){
        log.error("Unhandled exception", ex);
        return ErrorResponse.of("An unexpected error occured", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(ErrorResponse.of("No endpoint at /" + ex.getResourcePath(), HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleMissing(NotFoundException ex){
        return ErrorResponse.of(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalState(IllegalStateException ex){
        return ErrorResponse.of(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PeerUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handlePeerUnavailable(PeerUnavailableException ex){
        log.warn("Peer request failed: {}", ex.getMessage(), ex.getCause());
        return ErrorResponse.of(ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex){
        HttpHeaders headers = new HttpHeaders();
        if(ex.getSupportedHttpMethods() != null){
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                                                .body(ErrorResponse.of(ex.getMethod() + " is not supported for " +
                                                "this endpoint", HttpStatus.METHOD_NOT_ALLOWED));

    }
}
