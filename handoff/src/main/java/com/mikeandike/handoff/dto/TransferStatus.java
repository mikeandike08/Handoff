package com.mikeandike.handoff.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransferStatus(
    @Schema(description="The transfer ID, used to request transfer status later on", requiredMode=Schema.RequiredMode.REQUIRED) String id,
    @Schema(description="The Device ID of the device you are sending the file to", requiredMode=Schema.RequiredMode.REQUIRED) String deviceID,
    @Schema(description="The name of the file being sent") String filename,
    @Schema(description="The file size in bytes", requiredMode=Schema.RequiredMode.REQUIRED) long filesize,
    @Schema(description="The status of the transfer", requiredMode=Schema.RequiredMode.REQUIRED) String status,
    @Schema(description="The time the transfer was started, in ISO 8601 UTC format") String createdAt
) {}
