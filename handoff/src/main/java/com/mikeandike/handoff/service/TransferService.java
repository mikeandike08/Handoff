package com.mikeandike.handoff.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import com.mikeandike.handoff.dto.DeviceInfo;
import com.mikeandike.handoff.dto.StartTransferRequest;
import com.mikeandike.handoff.dto.TransferDecision;
import com.mikeandike.handoff.dto.TransferRequest;
import com.mikeandike.handoff.dto.TransferView;
import com.mikeandike.handoff.exceptions.BadRequestException;
import com.mikeandike.handoff.exceptions.ErrorResponse;
import com.mikeandike.handoff.exceptions.NotFoundException;
import com.mikeandike.handoff.exceptions.PayloadTooLargeException;
import com.mikeandike.handoff.exceptions.PeerUnavailableException;
import com.mikeandike.handoff.model.DeviceStore;
import com.mikeandike.handoff.model.Transfer;
import com.mikeandike.handoff.model.TransferState;
import com.mikeandike.handoff.network.MultiDeviceDiscovery;
import com.mikeandike.handoff.network.MultiDeviceDiscovery.PeerInfo;

import jakarta.annotation.PreDestroy;

@Service
public class TransferService {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    // How long the receiver has to accept or decline
    private static final Duration DECISION_TIMEOUT = Duration.ofSeconds(60);
    // A bit longer than DECISION_TIMEOUT, so we hear the receiver's "expired" instead of timing out first
    private static final Duration OFFER_REQUEST_TIMEOUT = DECISION_TIMEOUT.plusSeconds(15);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // How long finished transfers stay visible to /status
    private static final Duration FINISHED_RETENTION = Duration.ofMinutes(5);
    // An accepted transfer whose file never gets sent is failed after this long
    private static final Duration ACCEPTED_TIMEOUT = Duration.ofMinutes(10);

    private final MultiDeviceDiscovery discovery;
    private final DeviceStore deviceStore;
    private final Path downloadDir;
    private final long maxFileSize;

    // Offers wait for the receiver's answer, so they get a read timeout.
    // Uploads can legitimately take a long time, so they only get a connect timeout.
    private final RestClient offerClient;
    private final RestClient uploadClient;

    // Runs outgoing offers in the background so /start can return right away
    private final ExecutorService offerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();

    public TransferService(MultiDeviceDiscovery discovery,
                           DeviceStore deviceStore,
                           @Value("${handoff.download-dir}") String downloadDir,
                           @Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        this.discovery = discovery;
        this.deviceStore = deviceStore;
        this.downloadDir = Path.of(downloadDir);
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();

        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

        JdkClientHttpRequestFactory offerFactory = new JdkClientHttpRequestFactory(http);
        offerFactory.setReadTimeout(OFFER_REQUEST_TIMEOUT);
        this.offerClient = RestClient.builder().requestFactory(offerFactory).build();
        this.uploadClient = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)).build();
    }

    @PreDestroy
    void shutdown() {
        offerExecutor.shutdownNow();
    }

    // --- Sending side ---

    /**
     * Creates an outgoing transfer in PENDING and sends the offer in the background.
     * The UI polls /status to see the receiver's answer.
     */
    public TransferView startTransfer(StartTransferRequest req) {
        if (req == null) throw new BadRequestException("Request body is required");
        if (isBlank(req.deviceId())) throw new BadRequestException("deviceId is required");
        if (isBlank(req.fileName())) throw new BadRequestException("fileName is required");
        if (req.fileSize() == null) throw new BadRequestException("fileSize is required");
        if (req.fileSize() < 0) throw new BadRequestException("fileSize can't be negative");
        if (req.fileSize() > maxFileSize) {
            throw new PayloadTooLargeException(req.fileName() + " is " + humanSize(req.fileSize())
                + ", which is over the " + humanSize(maxFileSize) + " limit");
        }

        PeerInfo peer = requirePeer(req.deviceId());
        DeviceInfo me = DeviceInfo.from(deviceStore.getDevice());

        String transferId = UUID.randomUUID().toString();
        Transfer transfer = new Transfer(transferId, peer.info().id(), peer.info().alias(), peer.info().number(),
            req.fileName(), req.fileSize(), true);
        transfers.put(transferId, transfer);

        TransferRequest offer = new TransferRequest(transferId, me.id(), me.alias(), me.number(),
            req.fileName(), req.fileSize());
        offerExecutor.submit(() -> sendOffer(transfer, peer, offer));

        return toView(transfer);
    }

    private void sendOffer(Transfer transfer, PeerInfo peer, TransferRequest offer) {
        try {
            TransferDecision decision = offerClient.post()
                .uri(peerUrl(peer, "/api/peer/transfer/request"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(offer)
                .retrieve()
                .body(TransferDecision.class);

            if (decision == null || decision.state() == null) {
                transfer.fail(transfer.getPeerAlias() + " sent an invalid response");
                return;
            }
            switch (decision.state()) {
                case ACCEPTED -> transfer.transition(TransferState.PENDING, TransferState.ACCEPTED);
                case EXPIRED -> transfer.finish(TransferState.EXPIRED, transfer.getPeerAlias() + " didn't respond in time");
                default -> transfer.finish(TransferState.DECLINED, decision.reason());
            }
        } catch (Exception e) {
            log.warn("Offer {} to {} failed", transfer.getTransferId(), transfer.getPeerAlias(), e);
            transfer.fail(describePeerFailure(transfer, e));
        } finally {
            // Never leave the transfer stuck in PENDING, even on an Error
            if (transfer.getState() == TransferState.PENDING) {
                transfer.fail("Unexpected error while sending the offer");
            }
        }
    }

    public void sendFile(String transferId, MultipartFile file) {
        Transfer transfer = requireTransfer(transferId);
        if (!transfer.isOutgoing()) throw new IllegalStateException("Transfer " + transferId + " is incoming, not outgoing");
        if (file.getSize() != transfer.getFileSize()) {
            throw new BadRequestException("File is " + file.getSize() + " bytes but the offer said "
                + transfer.getFileSize() + " bytes");
        }
        PeerInfo peer = discovery.getPeerInfo(transfer.getPeerDeviceId());
        if (peer == null) throw new NotFoundException(transfer.getPeerAlias() + " isn't on the network right now");

        // Atomic, so two /send calls for the same transfer can't both start
        if (!transfer.transition(TransferState.ACCEPTED, TransferState.TRANSFERRING)) {
            throw new IllegalStateException("Transfer not ready: " + transferId + " is " + transfer.getState());
        }

        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", file.getResource());

            uploadClient.post()
                .uri(peerUrl(peer, "/api/peer/transfer/" + transferId + "/upload"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .toBodilessEntity();

            transfer.addBytes(file.getSize());
            transfer.transition(TransferState.TRANSFERRING, TransferState.COMPLETE);
        } catch (RestClientException e) {
            String reason = describePeerFailure(transfer, e);
            transfer.fail(reason);
            throw new PeerUnavailableException(reason, e);
        } catch (Exception e) {
            transfer.fail("Couldn't read the file on this device");
            throw new RuntimeException("Failed to send file for transfer " + transferId, e);
        } finally {
            // Never leave the transfer stuck in TRANSFERRING, even on an Error
            if (transfer.getState() == TransferState.TRANSFERRING) {
                transfer.fail("Unexpected error while sending the file");
            }
        }
    }

    // --- Receiving side ---

    /** Called when a peer offers us a file. Waits for the local user's answer. */
    public TransferDecision awaitDecision(TransferRequest req) {
        if (req == null) throw new BadRequestException("Request body is required");
        if (isBlank(req.transferId())) throw new BadRequestException("transferId is required");
        if (isBlank(req.fromDeviceId())) throw new BadRequestException("fromDeviceId is required");
        if (isBlank(req.fileName())) throw new BadRequestException("fileName is required");
        if (req.fileSize() == null || req.fileSize() < 0) throw new BadRequestException("fileSize is required");

        if (req.fileSize() > maxFileSize) {
            return new TransferDecision(TransferState.DECLINED,
                "File is over the receiver's " + humanSize(maxFileSize) + " limit");
        }

        String alias = isBlank(req.fromAlias()) ? "Unknown device" : req.fromAlias();
        Transfer transfer = new Transfer(req.transferId(), req.fromDeviceId(), alias, req.fromNumber(),
            req.fileName(), req.fileSize(), false);
        if (transfers.putIfAbsent(req.transferId(), transfer) != null) {
            throw new IllegalStateException("Transfer " + req.transferId() + " already exists");
        }

        Boolean answer;
        try {
            answer = transfer.getDecision().get(DECISION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // complete(null) only wins if the user hasn't answered at this exact moment
            transfer.getDecision().complete(null);
            answer = transfer.getDecision().getNow(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transfer.getDecision().complete(null);
            transfer.fail("Interrupted while waiting for an answer");
            return new TransferDecision(TransferState.DECLINED, "Receiver shut down");
        } catch (ExecutionException e) {
            transfer.fail("Unexpected error while waiting for an answer");
            return new TransferDecision(TransferState.DECLINED, "Receiver error");
        }

        if (answer == null) {
            transfer.finish(TransferState.EXPIRED, "No response within " + DECISION_TIMEOUT.toSeconds() + " seconds");
            return new TransferDecision(TransferState.EXPIRED, null);
        }
        if (answer) {
            transfer.transition(TransferState.PENDING, TransferState.ACCEPTED);
            return new TransferDecision(TransferState.ACCEPTED, null);
        }
        transfer.finish(TransferState.DECLINED, null);
        return new TransferDecision(TransferState.DECLINED, null);
    }

    /** Called by the UI when the user clicks accept or decline. */
    public void respond(String transferId, Boolean accepted) {
        if (accepted == null) throw new BadRequestException("accepted is required (true or false)");
        Transfer transfer = requireTransfer(transferId);
        if (transfer.isOutgoing()) throw new IllegalStateException("Can't respond to your own offer: " + transferId);
        // complete() returns false if the offer already expired or was answered
        if (transfer.getState() != TransferState.PENDING || !transfer.getDecision().complete(accepted)) {
            throw new IllegalStateException("Transfer is no longer pending: " + transferId);
        }
    }

    public void receiveFile(String transferId, MultipartFile file) {
        Transfer transfer = requireTransfer(transferId);
        if (transfer.isOutgoing()) throw new IllegalStateException("Transfer " + transferId + " is outgoing, not incoming");
        if (!transfer.transition(TransferState.ACCEPTED, TransferState.TRANSFERRING)) {
            throw new IllegalStateException("Transfer not ready: " + transferId + " is " + transfer.getState());
        }

        try {
            if (file.getSize() != transfer.getFileSize()) {
                transfer.fail("Received " + file.getSize() + " bytes but expected " + transfer.getFileSize());
                throw new BadRequestException("File is " + file.getSize() + " bytes but the offer said "
                    + transfer.getFileSize() + " bytes");
            }
            saveToDownloads(file, safeName(transfer.getFileName()));
            transfer.addBytes(file.getSize());
            transfer.transition(TransferState.TRANSFERRING, TransferState.COMPLETE);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            transfer.fail("Couldn't save the file on this device");
            throw new RuntimeException("Failed to write incoming file for transfer " + transferId, e);
        } finally {
            if (transfer.getState() == TransferState.TRANSFERRING) {
                transfer.fail("Unexpected error while saving the file");
            }
        }
    }

    // --- Queries ---

    /** Offers waiting for this user's decision. */
    public List<TransferView> getPendingIncoming() {
        return transfers.values().stream()
            .filter(t -> !t.isOutgoing() && t.getState() == TransferState.PENDING)
            .sorted(Comparator.comparing(Transfer::getCreatedAt))
            .map(this::toView)
            .toList();
    }

    /** Every transfer this device still knows about, newest first. */
    public List<TransferView> getAllTransfers() {
        return transfers.values().stream()
            .sorted(Comparator.comparing(Transfer::getCreatedAt).reversed())
            .map(this::toView)
            .toList();
    }

    public TransferView getTransfer(String transferId) {
        return toView(requireTransfer(transferId));
    }

    // --- Cleanup ---

    @Scheduled(fixedRate = 30_000)
    public void sweepTransfers() {
        Instant now = Instant.now();

        // An accepted transfer whose file never arrives would otherwise wait forever
        transfers.values().forEach(t -> {
            if (t.getState() == TransferState.ACCEPTED && t.getUpdatedAt().isBefore(now.minus(ACCEPTED_TIMEOUT))) {
                t.fail(t.isOutgoing() ? "The file was never sent" : t.getPeerAlias() + " never sent the file");
            }
        });

        transfers.values().removeIf(t ->
            t.getState().isFinished() && t.getUpdatedAt().isBefore(now.minus(FINISHED_RETENTION)));
    }

    // --- Helpers ---

    private Transfer requireTransfer(String transferId) {
        Transfer t = transfers.get(transferId);
        if (t == null) throw new NotFoundException("Unknown transfer: " + transferId);
        return t;
    }

    private PeerInfo requirePeer(String deviceId) {
        PeerInfo peer = discovery.getPeerInfo(deviceId);
        if (peer == null) throw new NotFoundException("Peer not found: " + deviceId);
        return peer;
    }

    private TransferView toView(Transfer t) {
        return new TransferView(t.getTransferId(), t.getPeerDeviceId(), t.getPeerAlias(), t.getPeerNumber(),
            t.getFileName(), t.getFileSize(), t.getState().name(), t.getBytesTransferred(),
            t.isOutgoing(), t.getError(), t.getCreatedAt());
    }

    /** Turns an exception from calling a peer into a message the UI can show. */
    private String describePeerFailure(Transfer transfer, Exception e) {
        String alias = transfer.getPeerAlias();
        if (e instanceof RestClientResponseException re) {
            String peerMessage = null;
            try {
                ErrorResponse body = re.getResponseBodyAs(ErrorResponse.class);
                if (body != null) peerMessage = body.message();
            } catch (Exception ignored) {
                // Not our error format, fall back to the status code
            }
            if (re.getStatusCode().is4xxClientError()) {
                return alias + " rejected the transfer: "
                    + (peerMessage != null ? peerMessage : "HTTP " + re.getStatusCode().value());
            }
            return alias + " ran into an error receiving the file";
        }
        if (e instanceof ResourceAccessException) {
            return alias + " went offline";
        }
        return "Couldn't reach " + alias;
    }

    /**
     * Saves the file without overwriting anything: report.zip, then report (1).zip, report (2).zip, ...
     * The name is reserved with createFile, which fails atomically if the file already exists.
     */
    private Path saveToDownloads(MultipartFile file, String name) throws IOException {
        Files.createDirectories(downloadDir);

        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";

        for (int i = 0; i < 10_000; i++) {
            Path target = downloadDir.resolve(i == 0 ? name : stem + " (" + i + ")" + ext);
            try {
                Files.createFile(target);
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (IOException e) {
                Files.deleteIfExists(target);
                throw e;
            }
        }
        throw new IOException("Too many files named " + name + " in " + downloadDir);
    }

    /**
     * Turns a sender-supplied file name into something safe to write inside downloadDir.
     * Strips any directory parts (both / and \), and falls back to "untitled" for names
     * that would point at a directory instead of a file (".", "..", "/", blank).
     */
    private String safeName(String name) {
        if (name == null) return "untitled";

        // Keep only the last segment, treating both separators as path breaks on every OS
        String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);

        // Drop control characters (including NUL, which makes Path.of throw)
        base = base.replaceAll("\\p{Cntrl}", "").strip();

        if (base.isEmpty() || base.equals(".") || base.equals("..")) return "untitled";
        return base;
    }

    private String peerUrl(PeerInfo peer, String path) {
        return "http://" + peer.ip() + ":" + peer.port() + path;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 11811160064 -> "11 GB" */
    static String humanSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return value == Math.floor(value)
            ? String.format("%d %s", (long) value, units[i])
            : String.format("%.1f %s", value, units[i]);
    }
}
