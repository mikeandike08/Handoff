package com.mikeandike.handoff.network;

import java.net.DatagramPacket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikeandike.handoff.dto.DeviceAnnouncement;
import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.model.DeviceStore;

import io.swagger.v3.oas.annotations.media.Schema;

@Component
public class MultiDeviceDiscovery {
    private final DeviceStore deviceStore;

    private final String MY_DEVICE_ID;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final long PEER_TIMEOUT_MS = 6000; // Expire after 6 seconds

    // Thread-safe registry of all active peers on the LAN
    private static final Map<String, PeerInfo> activePeers = new ConcurrentHashMap<>();

    public record PeerInfo(@Schema(description="DeviceInfo Object")DeviceInfo info, @Schema(description="Last seen in ms")long lastSeenMs, @Schema(description="IP of peer")String ip, @Schema(description="Port of peer")int port){}

    public MultiDeviceDiscovery(DeviceStore deviceStore){
        this.deviceStore = deviceStore;
        this.MY_DEVICE_ID = deviceStore.getIDFromLocal();
    }

    public void handleIncomingPacket(DatagramPacket packet) {
        try {
            DeviceAnnouncement ann = mapper.readValue(packet.getData(), 0, packet.getLength(), DeviceAnnouncement.class);

            // Verify that this is not our own broadcast
            if(MY_DEVICE_ID.equals(ann.device().id())){
                return;
            }

            // Add new device to list
            String senderIP = packet.getAddress().getHostAddress();
            activePeers.put(ann.device().id(), new PeerInfo(ann.device(), System.currentTimeMillis(), senderIP, ann.port()));
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
