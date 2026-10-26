package com.juca.homewatermonitorbackend.session.application.control;

import java.time.Instant;
import java.util.UUID;

public record ControlCommand(UUID commandId, UUID idempotencyKey, String deviceId, String commandType,
        UUID sessionId, ControlModel.State commandState, UUID targetBootId, UUID windowId, String dispatchPayload,
        Instant requestedAt, Instant dispatchingAt, String reason, Instant acceptedAt, Instant releaseConfirmedAt, UUID cultivationId) {
    public ControlCommand(UUID commandId,UUID idempotencyKey,String deviceId,String commandType,UUID sessionId,
            ControlModel.State state,UUID boot,UUID window,String payload,Instant requested,Instant dispatching,
            String reason,Instant accepted,Instant released) {
        this(commandId,idempotencyKey,deviceId,commandType,sessionId,state,boot,window,payload,requested,dispatching,reason,accepted,released,null);
    }
    public boolean same(String device, String type, UUID requestedSession) {
        return same(device,type,requestedSession,null);
    }
    public boolean same(String device,String type,UUID requestedSession,UUID cultivation) {
        return deviceId.equals(device) && commandType.equals(type)
                && ("START".equals(type) ? java.util.Objects.equals(cultivationId,cultivation) : sessionId.equals(requestedSession));
    }
}
