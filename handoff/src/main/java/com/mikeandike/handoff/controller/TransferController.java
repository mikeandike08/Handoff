package com.mikeandike.handoff.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mikeandike.handoff.dto.ListResponse;
import com.mikeandike.handoff.dto.RespondRequest;
import com.mikeandike.handoff.dto.StartTransferRequest;
import com.mikeandike.handoff.dto.TransferView;
import com.mikeandike.handoff.service.TransferService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value="/api/transfer", produces=MediaType.APPLICATION_JSON_VALUE)
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService){
        this.transferService = transferService;
    }

    @Operation(summary="Offer a file to a peer",
        description="Returns right away with the transfer in PENDING. Poll /{transferId}/status until it becomes ACCEPTED, DECLINED, EXPIRED or FAILED.")
    @PostMapping(value="/start", consumes=MediaType.APPLICATION_JSON_VALUE)
    public TransferView startTransfer(@RequestBody StartTransferRequest body){
        return transferService.startTransfer(body);
    }

    @Operation(summary="List all transfers", description="Every transfer this device still knows about (incoming and outgoing), newest first. Use it to recover transfer IDs after a page refresh.")
    @GetMapping
    public ListResponse<TransferView> allTransfers(){
        return new ListResponse<>(transferService.getAllTransfers());
    }

    @Operation(summary="Get the current state of a transfer")
    @GetMapping(value="/{transferId}/status")
    public TransferView transferStatus(@PathVariable String transferId){
        return transferService.getTransfer(transferId);
    }

    @Operation(
        summary = "List transfers awaiting this user's decision",
        description = "Called by this device's UI, typically polled. Returns transfers other devices have offered that this user has not yet accepted or declined."
    )
    @GetMapping(value="/incoming")
    public ListResponse<TransferView> incoming(){
        return new ListResponse<>(transferService.getPendingIncoming());
    }

    @Operation(summary="Accept or decline an incoming transfer")
    @PostMapping(value="/{transferId}/respond", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> respondTransfer(@PathVariable String transferId, @RequestBody RespondRequest body){
        transferService.respond(transferId, body.accepted());
        return ResponseEntity.ok().build();
    }

    @Operation(summary="Upload the file for an accepted outgoing transfer",
        description="Blocks until the peer has received the whole file. The file's size must match the fileSize from /start.")
    @PostMapping(value = "/{transferId}/send", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> send(@PathVariable String transferId, @RequestParam("file") MultipartFile file){
        transferService.sendFile(transferId, file);
        return ResponseEntity.ok().build();
    }
}
