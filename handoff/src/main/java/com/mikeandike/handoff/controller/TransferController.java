package com.mikeandike.handoff.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController 
@RequestMapping(value="/api/transfer", produces=MediaType.APPLICATION_JSON_VALUE)
public class TransferController {
    
    // We can only have one status at a time.
    private final TransferService transferService;

    public TransferController(TransferService transferService){
        this.transferService = transferService;
    }

    @Operation(summary="Ask a peer to accept a file", description="Blocks until the peer responds or times out")
    @PostMapping(value="/start", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransferView startTransfer(@RequestBody StartTransferRequest body){
         return transferService.requestPermission(body.deviceId(), body.fileName(), body.fileSize());
    }

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

    @Operation(summary="Accept or Decline an incoming transfer")
    @PostMapping(value="/{transferId}/respond")
    public ResponseEntity<Void> respondTransfer(@PathVariable String transferId, @RequestBody RespondRequest body){
        transferService.respond(transferId, body.accepted());
        return ResponseEntity.ok().build();
    }

    @Operation(summary="Upload the file for an accepted outgoing transfer")
    @PostMapping(value = "/{transferId}/send", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> send(@PathVariable String transferId, @RequestParam("file") MultipartFile file){
        transferService.sendFile(transferId, file);
        return ResponseEntity.ok().build();
    }

}
