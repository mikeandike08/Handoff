package com.mikeandike.handoff.model;

public enum TransferState {
    PENDING,      // Offered, waiting for the receiver's decision
    ACCEPTED,     // Accepted, bytes haven't started
    TRANSFERRING, // Bytes moving
    COMPLETE,     // File saved on the receiver
    DECLINED,     // Receiver said no (or the offer was rejected automatically, e.g. too big)
    EXPIRED,      // Receiver didn't answer in time
    FAILED;       // Something went wrong, see Transfer.getError()

    /** True for states a transfer never leaves. */
    public boolean isFinished() {
        return this == COMPLETE || this == DECLINED || this == EXPIRED || this == FAILED;
    }
}
