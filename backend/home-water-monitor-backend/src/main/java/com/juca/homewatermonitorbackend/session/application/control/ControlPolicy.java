package com.juca.homewatermonitorbackend.session.application.control;

import java.util.Set;
import java.util.UUID;
import static com.juca.homewatermonitorbackend.session.application.control.ControlModel.*;

public final class ControlPolicy {
    private static final Set<String> STATES = Set.of("IDLE", "MEASURING", "PREPARING", "WAIT_PUBACK",
            "WAIT_CONFIRMATION", "BACKOFF", "MANUAL_WAIT");
    private static final Set<String> REASONS = Set.of("BOOT_MISMATCH", "WINDOW_INVALID", "WINDOW_EXPIRED", "BUSY",
            "BUFFER_PENDING", "NO_PENDING_BUFFER", "SESSION_MISMATCH", "SESSION_REUSED", "COMMAND_CONFLICT", "NOT_READY", "DEVICE_FAULT");
    private ControlPolicy() { }
    public static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("CONTROL_INPUT_INVALID");
    }
    public static void uuid(UUID id) { require(id != null && id.version() == 4 && id.variant() == 2); }
    public static void type(String type) { require("START".equals(type) || "RETRY".equals(type)); }

    public static void validate(Status s) {
        require(s.schemaVersion() == 1 && "DEVICE_STATUS".equals(s.eventType())
                && s.deviceId() != null && s.deviceId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"));
        uuid(s.bootId());
        require(s.statusSeq() >= 1 && s.statusSeq() <= Integer.MAX_VALUE && s.uptimeMs() >= 0
                && s.uptimeMs() <= 9007199254740991L && STATES.contains(s.state())
                && s.sampleCount() >= 0 && s.sampleCount() <= 60 && s.round() >= 0
                && s.attempt() >= 0 && s.attempt() <= 5
                && ("MQTT".equals(s.controlMode()) || "UART".equals(s.controlMode()))
                && (s.fault() == null || "CONTROL_UNAVAILABLE".equals(s.fault())));
        if ("IDLE".equals(s.state())) require(s.sessionId() == null && s.sampleCount() == 0
                && !s.finalized() && s.round() == 0 && s.attempt() == 0);
        else {
            uuid(s.sessionId());
            if ("MEASURING".equals(s.state())) require(!s.finalized() && s.round() == 1 && s.attempt() == 0);
            else require(s.finalized() && s.round() >= 1 && s.attempt() >= 1);
        }
        if (s.replyToProbeId() != null) uuid(s.replyToProbeId());
        if (s.controlWindow() != null) {
            uuid(s.controlWindow().windowId());
            require(s.controlWindow().validForMs() == 10000 && s.replyToProbeId() != null && s.controlReady()
                    && "MQTT".equals(s.controlMode()) && s.fault() == null
                    && ("IDLE".equals(s.state()) || "MANUAL_WAIT".equals(s.state())));
        }
        if (s.lastExecution() != null) {
            uuid(s.lastExecution().commandId()); uuid(s.lastExecution().sessionId());
            uuid(s.lastExecution().windowId()); type(s.lastExecution().commandType());
        }
        if (s.commandResult() != null) {
            var r = s.commandResult(); uuid(r.commandId()); uuid(r.sessionId()); type(r.commandType());
            require("ACCEPTED".equals(r.outcome()) && r.reason() == null
                    || "REJECTED".equals(r.outcome()) && r.reason() != null && REASONS.contains(r.reason()));
        }
        if (s.lastRelease() != null) {
            var r = s.lastRelease(); uuid(r.sessionId());
            require("PRESERVATION_CONFIRMED".equals(r.reason()) && ("NORMAL".equals(r.preservationResult())
                    && r.preservedSampleCount() == 60 || "INCOMPLETE".equals(r.preservationResult())
                    && r.preservedSampleCount() >= 0 && r.preservedSampleCount() < 60));
        }
    }

    public static boolean freshProbe(Status s, UUID probe, long elapsedMs, long limitMs) {
        return probe.equals(s.replyToProbeId()) && elapsedMs >= 0 && elapsedMs < limitMs;
    }
    public static boolean executable(Status s, String type, UUID session) {
        return s.controlReady() && "MQTT".equals(s.controlMode()) && s.fault() == null && s.controlWindow() != null
                && ("START".equals(type) ? "IDLE".equals(s.state())
                : "MANUAL_WAIT".equals(s.state()) && session.equals(s.sessionId()));
    }
    public static String liveness(boolean sameEpoch, long ageMs) {
        return !sameEpoch || ageMs < 0 ? "UNKNOWN" : ageMs <= 45000 ? "FRESH" : "STALE";
    }
    public static boolean newer(Status current, Status incoming) {
        return current.bootId().equals(incoming.bootId()) && incoming.statusSeq() > current.statusSeq();
    }
}
