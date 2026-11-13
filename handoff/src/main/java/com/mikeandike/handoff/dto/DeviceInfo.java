package com.mikeandike.handoff.dto;

import com.mikeandike.handoff.model.Device;
import io.swagger.v3.oas.annotations.media.Schema;

/* DTO for device information, carries only Alias and Number*/
@Schema(description = "DTO for device information, carries only Alias and Number")
public record DeviceInfo(
    @Schema(description = "The alias of the device", requiredMode = Schema.RequiredMode.REQUIRED) String alias,
    @Schema(description = "The number of the device", requiredMode = Schema.RequiredMode.REQUIRED) int number,
    @Schema(description = "The alphanumeric ID of the device", requiredMode = Schema.RequiredMode.REQUIRED) String id
) 
{
    public DeviceInfo {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be null or blank");
        }
        if (number == 0) {
            throw new IllegalArgumentException("Number cannot be zero");
        }
        if (id == null || id.isBlank()){
            throw new IllegalArgumentException("ID cannot be null or blank");
        }
    }

    public static DeviceInfo from(Device device){
        return new DeviceInfo(device.alias(), device.number(), device.id());
    }
}
