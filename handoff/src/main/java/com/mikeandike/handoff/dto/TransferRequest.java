package com.mikeandike.handoff.dto;

/** Offer sent from the sender's backend to the receiver's backend. */
public record TransferRequest(
    String transferId,
    String fromDeviceId,
    String fromAlias,
    int fromNumber,
    String fileName,
    Long fileSize
){}
