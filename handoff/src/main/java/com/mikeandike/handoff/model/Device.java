package com.mikeandike.handoff.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record Device(
    @Schema(description = "The alias of the device", requiredMode = Schema.RequiredMode.REQUIRED) String alias,
    @Schema(description = "The number of the device", requiredMode = Schema.RequiredMode.REQUIRED) int number,
    @Schema(description = "The device ID", requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(description = "The IP address of the device") String ip,
    @Schema(description = "The port of the device") String port
) {
    public Device {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be null or blank");
        }
        if (number == 0) {
            throw new IllegalArgumentException("Number cannot be zero");
        }
    }
}

