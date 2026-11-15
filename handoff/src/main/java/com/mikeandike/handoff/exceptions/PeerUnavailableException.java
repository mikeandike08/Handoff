package com.mikeandike.handoff.exceptions;

/**
 * Thrown when a call to another Handoff device fails (unreachable, timed out, or it returned an error).
 * Mapped to 502 Bad Gateway so the frontend can tell it apart from a bug on this device.
 */
public class PeerUnavailableException extends RuntimeException {
    public PeerUnavailableException(String message, Throwable cause) { super(message, cause); }
}
