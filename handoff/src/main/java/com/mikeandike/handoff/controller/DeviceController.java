package com.mikeandike.handoff.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.model.Device;
import com.mikeandike.handoff.model.DeviceStore;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value="/api/identity", produces=MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    private final DeviceStore deviceStore;

    public DeviceController(DeviceStore deviceStore){
        this.deviceStore = deviceStore;
    }

    @Operation (summary = "Get device identity", description = "Returns the device identity information, including alias and number")
    @GetMapping
    public DeviceInfo getDeviceIdentity() {
        // Create a new Device
        Device _device = deviceStore.getDevice();

        // Create a deviceInfo object with the alias and number of the device
        DeviceInfo _deviceInfo = DeviceInfo.from(_device);
        return _deviceInfo;
    }
}
