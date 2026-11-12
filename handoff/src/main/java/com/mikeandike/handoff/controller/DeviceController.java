package com.mikeandike.handoff.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikeandike.handoff.dto.ListResponse;
import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.model.Device;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value="/api/identity", produces=MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    @Operation (summary = "Get device identity", description = "Returns the device identity information, including alias and number")
    @GetMapping
    public ListResponse<DeviceInfo> getDeviceIdentity() {
        // Create a new Device
        Device _device = Device.generateDevice();

        // Create a deviceInfo object with the alias and number of the device
        DeviceInfo deviceInfo = DeviceInfo.from(_device);
        return new ListResponse<>(List.of(deviceInfo));
    }
}
