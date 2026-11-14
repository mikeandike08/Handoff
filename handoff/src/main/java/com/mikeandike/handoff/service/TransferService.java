package com.mikeandike.handoff.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.mikeandike.handoff.dto.IncomingTransfer;
import com.mikeandike.handoff.dto.TransferDecision;
import com.mikeandike.handoff.dto.TransferRequest;
import com.mikeandike.handoff.dto.TransferStatus;
import com.mikeandike.handoff.model.DeviceStore;
import com.mikeandike.handoff.network.MultiDeviceDiscovery;
import com.mikeandike.handoff.network.MultiDeviceDiscovery.PeerInfo;

@Service 
public class TransferService {
    private final MultiDeviceDiscovery discovery;
    private final DeviceStore deviceStore;
    private final RestClient restClient;
    private final Path downloadDir;

    // Incoming transfers waiting on our user's decision
    private final Map<String, PendingTransfer> pending = new ConcurrentHashMap<>();

    // Incoming transfers our user accepted, waiting on peer upload
    private final Map<String, TransferRequest> acceptedIncoming = new ConcurrentHashMap<>();

    // Outgoing transfers the peer accepted, awaiting our upload
    private final Map<String, String> acceptedOutgoing = new ConcurrentHashMap<>();
    
    // One minute before it times out
    private static final long DECISION_TIMEOUT_SECONDS = 60;

    public TransferService(MultiDeviceDiscovery discovery, 
                            DeviceStore deviceStore, 
                            @Value("${handoff.download-dir") String downloadDir){
        this.discovery = discovery;
        this.deviceStore = deviceStore;
        this.restClient = RestClient.create();
        this.downloadDir = Path.of(downloadDir);
    }

    public record PendingTransfer(
        TransferRequest request, String createdAt, CompletableFuture<Boolean> decision
    ){}

    // --- Sending side ----

    public TransferStatus requestPermission(String toDeviceId, String fileName, long fileSize){
        PeerInfo peer = requirePeer(toDeviceId);

        String transferId = UUID.randomUUID().toString();

        TransferRequest body = new TransferRequest(
            transferId,
            deviceStore.getIDFromLocal(),
            fileName,
            fileSize
        );

        // http://{ip}:{port}/api/transfer/request
        String url = "http://" + peer.ip() + ":" + peer.port() + "/api/transfer/request";

        TransferDecision response;
        try {
            response = restClient.post().uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(TransferDecision.class);
        } catch (Exception e) {
            return status(transferId, toDeviceId, fileName, fileSize, "failed");
        }
        
        boolean accepted = response != null && response.accepted();
        if (accepted){
            acceptedOutgoing.put(transferId, toDeviceId);
        }

        return status(transferId, toDeviceId, fileName, fileSize, accepted ? "accepted" : "declined");
    }

    public void sendFile(String transferId, MultipartFile file){
        String toDeviceId = acceptedOutgoing.remove(transferId);
        if (toDeviceId == null){
            throw new IllegalStateException("No Accepted transfer with id " + transferId);
        }

        PeerInfo peer = requirePeer(toDeviceId);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("file", new InputStreamResource(file.getInputStream()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
                @Override public long contentLength() { return file.getSize(); }
            });
        } catch (IOException e) {
            throw new RuntimeException("Could not read upload", e);
        }

        restClient.post()
            .uri(peerUrl(peer, "/api/peer/transfer" + transferId + "/upload"))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(builder.build())
            .retrieve()
            .toBodilessEntity();
    }

    // -- Recieving Side -- 

    public boolean awaitDecision(TransferRequest request){
        PendingTransfer p = new PendingTransfer(request, Instant.now().toString(), new CompletableFuture<>());
        pending.put(request.transferId(), p);

        try {
            boolean accepted = p.decision().get(DECISION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (accepted){
                acceptedIncoming.put(request.transferId(), request);
            }
            return accepted;
        } catch (TimeoutException e) {
            return false; // No one answered
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pending.remove(request.transferId());
        }
    }

    // This is called by the UI when the user clicks accept or decline
    public void respond(String transferId, boolean accepted) {
        PendingTransfer p = pending.get(transferId);
        if (pending != null) {
            p.decision().complete(accepted);
        }
    }

    public void receiveFile(String transferId, MultipartFile file) {
        TransferRequest request = acceptedIncoming.remove(transferId);
        if (request == null){
            throw new IllegalStateException("No accepted transfer with id " + transferId);
        }

        try {
            Files.createDirectories(downloadDir);
            Path target = downloadDir.resolve(safeName(request.fileName()));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write incoming file", e);
        }
    }

    // What the UI polls to see if something is waiting on us
    public List<IncomingTransfer> getPendingIncoming(){
        return pending.values().stream()
            .map(p -> new IncomingTransfer(
                p.request().transferId(),
                p.request().fromDeviceId(),
                p.request().fileName(),
                p.request().fileSize(),
                p.createdAt()
            )).toList();
    }

    // Helper functions

    private PeerInfo requirePeer(String deviceId){
        PeerInfo peer = discovery.getPeerInfo(deviceId);
        if (peer == null){
            throw new IllegalStateException("Peer not found: " + deviceId);
        }
        return peer;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "untitled";
        return Path.of(name).getFileName().toString();
    }

    private String peerUrl(PeerInfo peer, String path) {
        return "http://" + peer.ip() + ":" + peer.port() + path;
    }

    private TransferStatus status(String id, String deviceId, String name, long size, String state){
        return new TransferStatus(id, deviceId, name, size, state, state);
    }
}
