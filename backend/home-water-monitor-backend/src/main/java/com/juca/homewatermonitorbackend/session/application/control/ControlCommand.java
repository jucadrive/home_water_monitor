package com.juca.homewatermonitorbackend.session.application.control;

import java.time.Instant;
import java.util.UUID;

public record ControlCommand(UUID commandId, UUID idempotencyKey, String deviceId, String commandType,
        UUID sessionId, ControlModel.State commandState, UUID targetBootId, UUID windowId, String dispatchPayload,
        Instant requestedAt, Instant dispatchingAt, String reason, Instant acceptedAt, Instant releaseConfirmedAt) {
    public boolean same(String device, String type, UUID requestedSession) {
        return deviceId.equals(device) && commandType.equals(type)
                && ("START".equals(type) || sessionId.equals(requestedSession));
    }
}
