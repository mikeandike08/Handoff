package com.mikeandike.handoff.dto;

// Boxed Boolean so {} is rejected instead of silently meaning "decline"
public record RespondRequest(Boolean accepted) {}
