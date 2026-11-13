package com.mikeandike.handoff.network;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikeandike.handoff.dto.DeviceAnnouncement;
import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.model.Device;
import com.mikeandike.handoff.model.DeviceStore;

@Component 
public class NetworkBroadcaster {
    private final DeviceStore deviceStore;
    private final int httpPort;

    private static final String MULTICAST_ADDRESS = "239.255.1.1";
    private static final int PORT = 4445;

    private ScheduledExecutorService scheduler;

    public NetworkBroadcaster(DeviceStore deviceStore, @Value("${server.port}") int httpPort){
        this.deviceStore = deviceStore;
        this.httpPort = httpPort;
    }

    public void startBroadcasting(){
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UDP-Broadcaster");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try (MulticastSocket socket = new MulticastSocket()){
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);

                // Create a new Device
                Device _device = deviceStore.loadOrCreateDevice();

                // Create a deviceInfo object with the alias and number of the device
                DeviceInfo _deviceInfo = DeviceInfo.from(_device);

                DeviceAnnouncement announcement = new DeviceAnnouncement(_deviceInfo, httpPort);

                // Broadcast our device as a payload.
                ObjectMapper mapper = new ObjectMapper();

                while (true){
                    byte[] buffer = mapper.writeValueAsBytes(announcement);
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                    socket.send(packet);
                    Thread.sleep(1500); // broadcast every 1.5 seconds
                }
            } catch (Exception e){
                throw new RuntimeException("Failed to broadcast presence", e);
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public void stop(){
        if (scheduler != null)
            scheduler.shutdownNow();
    }
}
