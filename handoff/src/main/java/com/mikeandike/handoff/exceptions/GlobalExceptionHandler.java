package com.mikeandike.handoff.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into an ErrorResponse.
 *
 * Extends ResponseEntityExceptionHandler so Spring's own request errors (bad JSON, missing
 * multipart part, wrong Content-Type, wrong method, unknown URL, ...) keep their proper 4xx
 * status instead of falling through to the catch-all 500 below.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Our own exceptions ---

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex){
        return ErrorResponse.of(ex.getMessage(), HttpStatus.BAD_REQUEST);
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

    @ExceptionHandler(PayloadTooLargeException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ErrorResponse handleTooLarge(PayloadTooLargeException ex){
        return ErrorResponse.of(ex.getMessage(), HttpStatus.CONTENT_TOO_LARGE);
    }

    @ExceptionHandler(PeerUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handlePeerUnavailable(PeerUnavailableException ex){
        log.warn("Peer request failed: {}", ex.getMessage(), ex.getCause());
        return ErrorResponse.of(ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex){
        return ErrorResponse.of("Invalid value for '" + ex.getName() + "'", HttpStatus.BAD_REQUEST);
    }

    // Not a multipart request at all (e.g. a plain body sent to /send)
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMultipart(MultipartException ex){
        return ErrorResponse.of("Request must be multipart/form-data with a 'file' field", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex){
        log.error("Unhandled exception", ex);
        return ErrorResponse.of("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Spring's request errors, reshaped into ErrorResponse ---

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = switch (ex) {
            case HttpMessageNotReadableException e -> "Request body is missing or isn't valid JSON";
            case MissingServletRequestPartException e -> "Missing multipart field '" + e.getRequestPartName() + "'";
            case MaxUploadSizeExceededException e -> "File is over the upload size limit";
            case NoResourceFoundException e -> "No endpoint at /" + e.getResourcePath();
            case HttpRequestMethodNotSupportedException e -> e.getMethod() + " is not supported for this endpoint";
            case HttpMediaTypeNotSupportedException e -> (e.getContentType() == null
                    ? "Content-Type header is missing"
                    : "Content-Type " + e.getContentType() + " isn't supported")
                + "; expected " + e.getSupportedMediaTypes();
            default -> body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : status.getReasonPhrase();
        };
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(message, status));
    }
}
