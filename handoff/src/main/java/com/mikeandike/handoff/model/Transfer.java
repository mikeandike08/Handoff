package com.mikeandike.handoff.model;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Transfer {
    private final String transferId;
    private final String peerDeviceId;
    private final String fileName;
    private final long fileSize;
    private final String createdAt;
    private final boolean outgoing;
    private final CompletableFuture<Boolean> decision;

    private volatile TransferState state;
    private volatile long bytesTransferred;

    public Transfer(String transferId, String peerDeviceId, String fileName,
                    long fileSize, boolean outgoing) {
        this.transferId = transferId;
        this.peerDeviceId = peerDeviceId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.createdAt = Instant.now().toString();
        this.outgoing = outgoing;
        this.decision = new CompletableFuture<>();
        this.state = TransferState.PENDING;
    }

    public void setState(TransferState state) { this.state = state; }
    public void addBytes(long n) { this.bytesTransferred += n; }
    
    public String getTransferId(){
        return this.transferId;
    }
    public String getPeerDeviceId(){
        return this.peerDeviceId;
    }
    public String getFileName(){
        return this.fileName;
    }
    public long getFileSize(){
        return this.fileSize;
    }
    public String getCreatedAt(){
        return this.createdAt;
    }

    public CompletableFuture<Boolean> getDecision(){
        return this.decision;
    }

    public boolean getOutgoing(){
        return this.outgoing;
    }
    public long getBytesTransferred(){
        return this.bytesTransferred;
    }
    public TransferState getState(){
        return this.state;
    }
}