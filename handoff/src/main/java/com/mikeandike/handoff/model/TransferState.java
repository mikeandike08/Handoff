package com.mikeandike.handoff.model;

public enum TransferState {
    PENDING, // Offered, awaiting the receivers decision
    ACCEPTED, // Accepted, bytes haven't started
    TRANSFERRING, // bytes moving
    COMPLETE,
    DECLINED,
    FAILED
}

