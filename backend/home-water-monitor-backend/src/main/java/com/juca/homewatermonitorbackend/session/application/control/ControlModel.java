package com.juca.homewatermonitorbackend.session.application.control;

import java.util.UUID;

// 순수 값 타입이다. MQTT/HTTP/JPA 객체와 시간/외부 자원에 의존하지 않는다.
public final class ControlModel {
    private ControlModel() { }
    public enum State { REQUESTED, PROBING, DISPATCHING, ACCEPTED, REJECTED, NOT_DISPATCHED,
        NOT_EXECUTED, UNKNOWN, INTERRUPTED_REBOOT }
    public record Window(UUID windowId, int validForMs) { }
    public record Execution(UUID commandId, String commandType, UUID sessionId, UUID windowId) { }
    public record Release(UUID sessionId, String preservationResult, int preservedSampleCount, String reason) { }
    public record Result(UUID commandId, String commandType, UUID sessionId, String outcome, String reason) { }
    public record Status(int schemaVersion, String eventType, String deviceId, UUID bootId, long statusSeq,
            long uptimeMs, UUID replyToProbeId, Window controlWindow, String state, UUID sessionId,
            int sampleCount, boolean finalized, int round, int attempt, String controlMode,
            boolean controlReady, String fault, Result commandResult, Execution lastExecution, Release lastRelease) { }
    public record Probe(int schemaVersion, String eventType, String deviceId, UUID probeId) { }
    public record Start(int schemaVersion, String eventType, String deviceId, UUID commandId, UUID sessionId,
            UUID targetBootId, UUID windowId, int expectedSamples, int samplingIntervalMs) { }
    public record Retry(int schemaVersion, String eventType, String deviceId, UUID commandId, UUID sessionId,
            UUID targetBootId, UUID windowId) { }
}
