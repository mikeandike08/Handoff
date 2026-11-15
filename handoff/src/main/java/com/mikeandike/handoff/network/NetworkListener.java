package com.mikeandike.handoff.network;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NetworkListener {
    private static final Logger log = LoggerFactory.getLogger(NetworkListener.class);

    private final MultiDeviceDiscovery multiDD;

    // TODO: add these to an environment variable at some point
    private static final String MULTICAST_ADDRESS = "239.255.1.1";
    private static final int PORT = 4445;

    private Thread listenerThread;
    private volatile MulticastSocket socket;
    private final List<NetworkInterface> joinedInterfaces = new ArrayList<>();
    private InetSocketAddress groupAddress;

    public NetworkListener(MultiDeviceDiscovery multiDD){
        this.multiDD = multiDD;
    }

    public void startListening(){
        listenerThread = new Thread(() -> {
            try {
                socket = new MulticastSocket(PORT);
                groupAddress = new InetSocketAddress(InetAddress.getByName(MULTICAST_ADDRESS), PORT);

                joinOnAllInterfaces();
                if (joinedInterfaces.isEmpty()) {
                    log.warn("Couldn't join multicast group {} on any network interface; other devices won't be discovered", MULTICAST_ADDRESS);
                }

                byte[] buffer = new byte[1024];
                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()){
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Parses JSON, skips our own announcements, and updates the list of active devices
                    multiDD.handleIncomingPacket(packet);
                }
            } catch (SocketException e) {
                // stop() closes the socket to unblock receive(), which lands here
                if (socket == null || !socket.isClosed()) {
                    log.error("Discovery listener stopped unexpectedly", e);
                }
            } catch (Exception e){
                log.error("Discovery listener stopped unexpectedly", e);
            }
        }, "UDP-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Joins the multicast group on every interface that can carry it (Wi-Fi, Ethernet, loopback).
     *
     * Joining only on the interface for InetAddress.getLocalHost() breaks on machines whose
     * hostname resolves to 127.0.0.1 (common on macOS): we'd listen on loopback while other
     * devices' announcements arrive on Wi-Fi, so nothing is ever discovered.
     * VPN and other point-to-point interfaces are skipped.
     */
    private void joinOnAllInterfaces() throws SocketException {
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            try {
                if (!ni.isUp() || !ni.supportsMulticast() || ni.isPointToPoint() || !ni.getInetAddresses().hasMoreElements()) {
                    continue;
                }
                socket.joinGroup(groupAddress, ni);
                joinedInterfaces.add(ni);
                log.info("Listening for devices on {}", ni.getName());
            } catch (Exception e) {
                // Some interfaces refuse to join (e.g. no IPv4 address); the others still work
                log.debug("Couldn't join multicast group on {}: {}", ni.getName(), e.getMessage());
            }
        }
    }

    public void stop(){
        if (listenerThread != null){
            listenerThread.interrupt();
        }
        MulticastSocket s = socket;
        if (s != null && !s.isClosed()){
            for (NetworkInterface ni : joinedInterfaces) {
                try {
                    s.leaveGroup(groupAddress, ni);
                } catch (Exception ignored) {
                    // Closing the socket below leaves every group anyway
                }
            }
            // receive() blocks, so interrupting the thread isn't enough; closing the socket unblocks it
            s.close();
        }
    }
}
