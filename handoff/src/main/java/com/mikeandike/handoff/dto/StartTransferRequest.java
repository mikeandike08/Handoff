package com.mikeandike.handoff.dto;

public record StartTransferRequest(String deviceId, String fileName, long fileSize){}
