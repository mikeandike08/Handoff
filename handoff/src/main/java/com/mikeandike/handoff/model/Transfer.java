package com.mikeandike.handoff.model;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * One file transfer, from the point of view of this device (outgoing = we are sending).
 * State changes are synchronized so a background task, a request thread and the sweeper
 * can't overwrite each other's changes.
 */
public class Transfer {
    private final String transferId;
    private final String peerDeviceId;
    private final String peerAlias;
    private final int peerNumber;
    private final String fileName;
    private final long fileSize;
    private final String createdAt;
    private final boolean outgoing;

    // Receiving side only: completed with the user's answer, or null if the offer expired
    private final CompletableFuture<Boolean> decision = new CompletableFuture<>();

    private TransferState state = TransferState.PENDING;
    private String error;
    private Instant updatedAt;
    private volatile long bytesTransferred;

    public Transfer(String transferId, String peerDeviceId, String peerAlias, int peerNumber,
                    String fileName, long fileSize, boolean outgoing) {
        this.transferId = transferId;
        this.peerDeviceId = peerDeviceId;
        this.peerAlias = peerAlias;
        this.peerNumber = peerNumber;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.outgoing = outgoing;
        Instant now = Instant.now();
        this.createdAt = now.toString();
        this.updatedAt = now;
    }

    /** Moves to {@code next} only if the transfer is currently in {@code expected}. */
    public synchronized boolean transition(TransferState expected, TransferState next) {
        if (state != expected) return false;
        state = next;
        updatedAt = Instant.now();
        return true;
    }

    /** Ends the transfer with an optional reason. Does nothing if it already finished. */
    public synchronized void finish(TransferState finalState, String reason) {
        if (state.isFinished()) return;
        state = finalState;
        error = reason;
        updatedAt = Instant.now();
    }

    public void fail(String reason) {
        finish(TransferState.FAILED, reason);
    }

    public void addBytes(long n) { this.bytesTransferred += n; }

    public synchronized TransferState getState() { return state; }
    public synchronized String getError() { return error; }
    public synchronized Instant getUpdatedAt() { return updatedAt; }

    public String getTransferId() { return transferId; }
    public String getPeerDeviceId() { return peerDeviceId; }
    public String getPeerAlias() { return peerAlias; }
    public int getPeerNumber() { return peerNumber; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getCreatedAt() { return createdAt; }
    public boolean isOutgoing() { return outgoing; }
    public long getBytesTransferred() { return bytesTransferred; }
    public CompletableFuture<Boolean> getDecision() { return decision; }
}
