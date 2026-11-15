package com.mikeandike.handoff.exceptions;

/** The client sent something invalid. Mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
