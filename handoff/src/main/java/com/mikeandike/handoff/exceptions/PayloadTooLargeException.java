package com.mikeandike.handoff.exceptions;

/** The file is over the size limit. Mapped to 413. */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) { super(message); }
}
