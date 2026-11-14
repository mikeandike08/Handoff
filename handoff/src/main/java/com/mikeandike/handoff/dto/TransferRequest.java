package com.mikeandike.handoff.dto;

public record TransferRequest(String transferId, String fromDeviceId, String fileName, long fileSize){}

