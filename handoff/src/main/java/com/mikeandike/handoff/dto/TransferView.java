package com.mikeandike.handoff.dto;

public record TransferView(
    String transferId,
    String peerDeviceId,
    String fileName,
    long fileSize,
    String state,
    long bytesTransferred,
    boolean outgoing,
    String createdAt
){}
