package com.mikeandike.handoff.dto;

// Boxed Long so a missing fileSize can be told apart from 0
public record StartTransferRequest(String deviceId, String fileName, Long fileSize){}
