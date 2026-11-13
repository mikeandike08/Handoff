package com.mikeandike.handoff.exceptions;

import org.springframework.http.HttpStatus;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;


public record ErrorResponse(
    @Schema(example = "2026-11-04T19:30:00Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "Size must be positive") String message
) {
    public static ErrorResponse of(String message, HttpStatus status) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message);
    }
}
