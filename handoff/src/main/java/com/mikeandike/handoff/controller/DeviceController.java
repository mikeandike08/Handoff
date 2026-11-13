package com.mikeandike.handoff.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.dto.ListResponse;
import com.mikeandike.handoff.model.Device;
import com.mikeandike.handoff.model.DeviceStore;
import com.mikeandike.handoff.network.NetworkDiscoveryService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value="/api/identity", produces=MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    private final NetworkDiscoveryService discoveryService;
    private final DeviceStore deviceStore;

    public DeviceController(NetworkDiscoveryService discoveryService, DeviceStore deviceStore){
        this.discoveryService = discoveryService;
        this.deviceStore = deviceStore;
    }

    @Operation (summary = "Get device identity", description = "Returns the device identity information, including alias and number")
    @GetMapping
    public DeviceInfo getDeviceIdentity() {
        // Create a new Device
        Device _device = deviceStore.loadOrCreateDevice();

        // Create a deviceInfo object with the alias and number of the device
        DeviceInfo _deviceInfo = DeviceInfo.from(_device);
        return _deviceInfo;
    }

    @Operation (summary = "Get nearby devices by scanning the local network", description = "Returns a list of alphebetically sorted (by alias) device identity information in the local network")
    @GetMapping (value="/nearby-devices")
    public ListResponse<DeviceInfo> getNearbyDevices(){
        List<DeviceInfo> activeDevices = discoveryService.getDiscoveredDevices().stream()
        .sorted(Comparator.comparing(DeviceInfo::alias, String.CASE_INSENSITIVE_ORDER))
        .toList();
        return new ListResponse<>(activeDevices);
    }
}
