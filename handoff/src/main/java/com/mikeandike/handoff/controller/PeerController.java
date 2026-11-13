package com.mikeandike.handoff.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.dto.ListResponse;
import com.mikeandike.handoff.network.NetworkDiscoveryService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value="/api/devices", produces=MediaType.APPLICATION_JSON_VALUE)
public class PeerController {
    private final NetworkDiscoveryService discoveryService;

    public PeerController(NetworkDiscoveryService discoveryService){
        this.discoveryService = discoveryService;
    }

    @Operation (summary = "Get nearby devices by scanning the local network", description = "Returns a list of alphabetically sorted (by alias, number as tiebreaker) device identity information in the local network")
    @GetMapping
    public ListResponse<DeviceInfo> getNearbyDevices(){
        List<DeviceInfo> activeDevices = discoveryService.getDiscoveredDevices().stream()
        .sorted(Comparator.comparing(DeviceInfo::alias, String.CASE_INSENSITIVE_ORDER).thenComparingInt(DeviceInfo::number))
        .toList();
        return new ListResponse<>(activeDevices);
    }
}
