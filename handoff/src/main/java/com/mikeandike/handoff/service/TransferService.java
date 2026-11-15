package com.mikeandike.handoff.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.mikeandike.handoff.dto.TransferDecision;
import com.mikeandike.handoff.dto.TransferRequest;
import com.mikeandike.handoff.dto.TransferView;
import com.mikeandike.handoff.exceptions.NotFoundException;
import com.mikeandike.handoff.exceptions.PeerUnavailableException;
import com.mikeandike.handoff.model.DeviceStore;
import com.mikeandike.handoff.model.Transfer;
import com.mikeandike.handoff.model.TransferState;
import com.mikeandike.handoff.network.MultiDeviceDiscovery;
import com.mikeandike.handoff.network.MultiDeviceDiscovery.PeerInfo;

@Service 
public class TransferService {
    private final MultiDeviceDiscovery discovery;
    private final DeviceStore deviceStore;
    private final RestClient restClient;
    private final Path downloadDir;

    private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
    
    // One minute before it times out
    private static final long DECISION_TIMEOUT_SECONDS = 60;

    public TransferService(MultiDeviceDiscovery discovery, 
                            DeviceStore deviceStore, 
                            @Value("${handoff.download-dir}") String downloadDir){
        this.discovery = discovery;
        this.deviceStore = deviceStore;
        this.restClient = RestClient.create();
        this.downloadDir = Path.of(downloadDir);
    }

    public record PendingTransfer(
        TransferRequest request, String createdAt, CompletableFuture<Boolean> decision
    ){}

    // --- Sending side ----

    public TransferView requestPermission(String toDeviceId, String fileName, long fileSize){
        PeerInfo peer = requirePeer(toDeviceId);

        String transferId = UUID.randomUUID().toString();

        Transfer transfer = new Transfer(transferId, toDeviceId, fileName, fileSize, true);
        transfers.put(transferId, transfer);

        TransferRequest body = new TransferRequest(
            transferId,
            deviceStore.getIDFromLocal(),
            fileName,
            fileSize
        );

        // http://{ip}:{port}/api/transfer/request
        String url = "http://" + peer.ip() + ":" + peer.port() + "/api/peer/transfer/request";

        TransferDecision response;
        try {
            response = restClient.post().uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(TransferDecision.class);
        } catch (Exception e) {
            transfer.setState(TransferState.FAILED);
            return toView(transfer);
        }
        
        boolean accepted = response != null && response.accepted();
        transfer.setState(accepted ? TransferState.ACCEPTED : TransferState.DECLINED);
        return toView(transfer);
    }

    public void sendFile(String transferId, MultipartFile file){
        Transfer transfer = requireAccepted(transferId);
        PeerInfo peer = requirePeer(transfer.getPeerDeviceId());

        transfer.setState(TransferState.TRANSFERRING);

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new InputStreamResource(file.getInputStream()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
                @Override public long contentLength() { return file.getSize(); }
            });

            restClient.post()
                .uri(peerUrl(peer, "/api/peer/transfer/" + transferId + "/upload"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .toBodilessEntity();

            transfer.addBytes(file.getSize());
            transfer.setState(TransferState.COMPLETE);
        } catch (RestClientException e) {
            // The peer couldn't be reached, timed out, or rejected the upload
            transfer.setState(TransferState.FAILED);
            throw new PeerUnavailableException("Could not send file to peer " + peer.info().alias(), e);
        } catch (Exception e) {
            // Something went wrong on our side (e.g. reading the uploaded file)
            transfer.setState(TransferState.FAILED);
            throw new RuntimeException("Failed to send file to peer", e);
        }
    }

    // -- Recieving Side -- 

    public boolean awaitDecision(TransferRequest request){
        Transfer transfer = new Transfer(
        request.transferId(), request.fromDeviceId(),
        request.fileName(), request.fileSize(), false
        );
        transfers.put(request.transferId(), transfer);

        try {
            boolean accepted = transfer.getDecision().get(DECISION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            transfer.setState(accepted ? TransferState.ACCEPTED : TransferState.DECLINED);
            return accepted;
        } catch (TimeoutException e) {
            transfer.setState(TransferState.DECLINED);
            return false;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            transfer.setState(TransferState.FAILED);
            return false;
        }
    }

    // This is called by the UI when the user clicks accept or decline
    public void respond(String transferId, boolean accepted) {
        Transfer transfer = transfers.get(transferId);
        if (transfer == null) throw new NotFoundException("Unknown transfer: " + transferId);
        if (transfer.getState() != TransferState.PENDING) throw new IllegalStateException("The transfer: " + transferId + " has expired");
        transfer.getDecision().complete(accepted);
    }

    public void receiveFile(String transferId, MultipartFile file) {
        Transfer transfer = requireAccepted(transferId);
        transfer.setState(TransferState.TRANSFERRING);

        try {
            Files.createDirectories(downloadDir);
            Path target = downloadDir.resolve(safeName(transfer.getFileName()));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            transfer.addBytes(file.getSize());
            transfer.setState(TransferState.COMPLETE);
        } catch (Exception e) {
            transfer.setState(TransferState.FAILED);
            throw new RuntimeException("Failed to write incoming file", e);
        }
    }

    // What the UI polls to see if something is waiting on us
    public List<TransferView> getPendingIncoming(){
        return transfers.values().stream()
            .filter(t -> !t.getOutgoing() && t.getState() == TransferState.PENDING)
            .map(this::toView)
            .toList();
    }

    public TransferView getTransfer(String transferId) {
        Transfer t = transfers.get(transferId);
        if (t == null) throw new NotFoundException("Unknown transfer: " + transferId);
        return toView(t);
    }

    // Helper functions

    private Transfer requireAccepted(String transferId){
        Transfer t = transfers.get(transferId);
        if (t == null) throw new NotFoundException("Unknown transfer: " + transferId);
        if (t.getState() != TransferState.ACCEPTED)
            throw new IllegalStateException("Transfer not ready: " + transferId + " is " + t.getState());
        return t;
    }

    private TransferView toView(Transfer t){
        return new TransferView(t.getTransferId(), t.getPeerDeviceId(), t.getFileName(),
            t.getFileSize(), t.getState().name(), t.getBytesTransferred(),
            t.getOutgoing(), t.getCreatedAt());
    }

    private PeerInfo requirePeer(String deviceId){
        PeerInfo peer = discovery.getPeerInfo(deviceId);
        if (peer == null) throw new NotFoundException("Peer not found: " + deviceId);
        return peer;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "untitled";
        return Path.of(name).getFileName().toString();
    }

    private String peerUrl(PeerInfo peer, String path) {
        return "http://" + peer.ip() + ":" + peer.port() + path;
    }

    @Scheduled(fixedRate = 60_000)
    public void sweepFinishedTransfers(){
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        transfers.values().removeIf(t -> 
            switch (t.getState()) {
                case COMPLETE, DECLINED, FAILED -> Instant.parse(t.getCreatedAt()).isBefore(cutoff);
                default -> Instant.parse(t.getCreatedAt()).isBefore(Instant.now().minus(Duration.ofHours(1)));
            }
        );
    }
}
