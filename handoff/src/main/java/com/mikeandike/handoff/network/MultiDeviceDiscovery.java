package com.mikeandike.handoff.network;

import java.net.DatagramPacket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.model.Device;

import io.swagger.v3.oas.annotations.media.Schema;

public class MultiDeviceDiscovery {
    private static final String MY_DEVICE_ID = Device.getIDFromLocal();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final long PEER_TIMEOUT_MS = 6000; // Expire after 6 seconds

    // Thread-safe registry of all active peers on the LAN
    private static final Map<String, PeerInfo> activePeers = new ConcurrentHashMap<>();

    public record PeerInfo(@Schema(description="DeviceInfo Object")DeviceInfo info, @Schema(description="Last seen in ms")long lastSeenMs){}

    public static void handleIncomingPacket(DatagramPacket packet) {
        try {
            DeviceInfo _deviceInfo = mapper.readValue(packet.getData(), 0, packet.getLength(), DeviceInfo.class);

            // Verify that this is not our own broadcast
            if(MY_DEVICE_ID.equals(_deviceInfo.id())){
                return;
            }

            // Add new device to list
            //String senderIP = packet.getAddress().getHostAddress();
            activePeers.put(_deviceInfo.id(), new PeerInfo(_deviceInfo, System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Gets all active peers, and removes any from the list that hasn't been seen for 6 seconds
     * @return List of Map<String, PeerInfo>
     */
    public List<PeerInfo> getActivePeers() {
        long now = System.currentTimeMillis();
        activePeers.entrySet().removeIf(entry ->
            (now - entry.getValue().lastSeenMs()) > PEER_TIMEOUT_MS
        );
        return List.copyOf(activePeers.values());
    }

    public List<DeviceInfo> getActiveDevices(){
        return getActivePeers().stream().map(PeerInfo::info).toList();
    }
}
