package com.mikeandike.handoff.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mikeandike.handoff.dto.TransferDecision;
import com.mikeandike.handoff.dto.TransferRequest;
import com.mikeandike.handoff.service.TransferService;

import io.swagger.v3.oas.annotations.Operation;

@RestController 
@RequestMapping(value="/api/peer/transfer", produces = MediaType.APPLICATION_JSON_VALUE)
public class PeerTransferController {
    private final TransferService transferService;

    public PeerTransferController(TransferService transferService){
        this.transferService = transferService;
    }

    @Operation(summary = "Receive a transfer offer from another device",
        description="Called by a peer's backend, not by a browser. Blocks until this device's user responds or 60 seconds pass, then returns the decision to the caller."
    )
    @PostMapping("/request")
    public TransferDecision request(@RequestBody TransferRequest body){
        boolean accepted = transferService.awaitDecision(body);
        return new TransferDecision(accepted);
    }

    @Operation(
        summary = "Receive file bytes for an accepted transfer",
        description = "Called by a peer’s backend after its offer was accepted. The peer uploads the file bytes here; this device writes them to its download directory."
    )
    @PostMapping(value = "/{transferId}/upload", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> receiveUpload(@PathVariable String transferId, @RequestParam("file") MultipartFile file){
        transferService.receiveFile(transferId, file);
        return ResponseEntity.ok().build();
    }
}
