package com.mikeandike.handoff.dto;

public record IncomingTransfer(String transferId, String fromDeviceId, String fileName, long fileSize, String createdAt) {}
