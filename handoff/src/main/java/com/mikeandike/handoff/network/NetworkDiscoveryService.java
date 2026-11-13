package com.mikeandike.handoff.network;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mikeandike.handoff.dto.DeviceInfo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service 
public class NetworkDiscoveryService {
    private final NetworkBroadcaster broadcaster;
    private final NetworkListener listener;
    private final MultiDeviceDiscovery discovery;

    public NetworkDiscoveryService(){
        this.broadcaster = new NetworkBroadcaster();
        this.listener = new NetworkListener();
        this.discovery = new MultiDeviceDiscovery();
    }

    public List<DeviceInfo> getDiscoveredDevices(){
        return discovery.getActiveDevices();
    }

    @PostConstruct 
    public void init(){
        listener.startListening();
        broadcaster.startBroadcasting();
    }

    @PreDestroy
    public void cleanup(){
        broadcaster.stop();
        listener.stop();
    }
}
