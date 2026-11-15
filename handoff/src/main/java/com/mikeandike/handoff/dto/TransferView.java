package com.mikeandike.handoff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransferView(
    String transferId,
    @Schema(description = "ID of the other device") String peerDeviceId,
    @Schema(description = "Alias of the other device", example = "Soggy Waffle") String peerAlias,
    @Schema(description = "Number of the other device", example = "4070") int peerNumber,
    String fileName,
    long fileSize,
    @Schema(allowableValues = {"PENDING", "ACCEPTED", "TRANSFERRING", "COMPLETE", "DECLINED", "EXPIRED", "FAILED"}) String state,
    long bytesTransferred,
    boolean outgoing,
    @Schema(description = "Why the transfer failed, declined or expired. null otherwise", example = "Soggy Waffle went offline") String error,
    String createdAt
){}
