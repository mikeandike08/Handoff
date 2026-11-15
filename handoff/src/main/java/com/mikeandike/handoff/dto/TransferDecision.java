package com.mikeandike.handoff.dto;

import com.mikeandike.handoff.model.TransferState;

/**
 * Receiver's answer to an offer.
 * state is ACCEPTED, DECLINED or EXPIRED. reason is set when the offer was declined automatically.
 */
public record TransferDecision(TransferState state, String reason) {}
