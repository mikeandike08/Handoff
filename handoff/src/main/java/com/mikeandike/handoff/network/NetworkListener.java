package com.mikeandike.handoff.network;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

public class NetworkListener {
    // TODO: add these to an environment variable at some point
    private static final String MULTICAST_ADDRESS = "244.0.2.60";
    private static final int PORT = 4445;

    private Thread listenerThread;
    private MulticastSocket socket;

    public void startListening(){
        listenerThread = new Thread(() -> {
            try {
                MulticastSocket socket = new MulticastSocket(PORT);
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                InetSocketAddress groupAddress = new InetSocketAddress(group, PORT);

                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                socket.joinGroup(groupAddress, netIf);

                byte[] buffer = new byte[1024];
                while (!Thread.currentThread().isInterrupted() && !socket.isClosed()){
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Handle conversion of packet payload to whatever data type we choose
                    MultiDeviceDiscovery.handleIncomingPacket(packet); // Parse's JSON, skip self, and update list of all active devices
                }

                if (!socket.isClosed()){
                    socket.leaveGroup(groupAddress, netIf);
                }

                // Clean up when leaving
                socket.leaveGroup(groupAddress, netIf);
            } catch (Exception e){
                throw new RuntimeException("Failed to listen for broadcast presence", e);
            }
        }, "UDP-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop(){
        if (listenerThread != null){
            listenerThread.interrupt();
        }
        if(socket != null && !socket.isClosed()){
            socket.close();
        }
    }
}
