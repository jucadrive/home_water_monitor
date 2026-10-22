package com.juca.homewatermonitorbackend.session.application.control;

import com.juca.homewatermonitorbackend.session.infrastructure.persistence.DeviceControlStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix="water-monitor.control",name="enabled",havingValue="true")
public class DeviceControlQueryService {
    private final DeviceControlStore store;
    private final ControlRuntime runtime;
    public DeviceControlQueryService(DeviceControlStore store,ControlRuntime runtime){this.store=store;this.runtime=runtime;}
    public Map<String,Object> device(String device) {
        runtime.requireDevice(device);var o=store.observation(device);var result=new LinkedHashMap<String,Object>();
        result.put("deviceId",device);result.put("liveness",runtime.liveness(device));
        result.put("snapshot",o==null?null:o.status());result.put("receivedAt",o==null?null:o.receivedAt());
        result.put("confirmedAt",o==null?null:o.confirmedAt());return result;
    }
    public Map<String,Object> command(UUID id) {
        var c=store.command(id);if(c==null)throw new ControlException(404,"COMMAND_NOT_FOUND");
        var r=new LinkedHashMap<String,Object>();r.put("commandId",id);r.put("sessionId",c.sessionId());r.put("deviceId",c.deviceId());
        r.put("commandType",c.commandType());r.put("commandState",c.commandState());r.put("reason",c.reason());
        r.put("requestedAt",c.requestedAt());r.put("dispatchingAt",c.dispatchingAt());r.put("serverPreservation",store.preservation(c.sessionId()));
        r.put("acceptedAt",c.acceptedAt());r.put("releaseConfirmedAt",c.releaseConfirmedAt());
        var o=store.observation(c.deviceId());String buffer="NOT_OBSERVED";
        if(c.releaseConfirmedAt()!=null)buffer="RELEASE_CONFIRMED";
        else if(c.commandState()==ControlModel.State.INTERRUPTED_REBOOT)buffer="UNKNOWN_AFTER_REBOOT";
        else if(o!=null && o.status().bootId().equals(c.targetBootId())) {
            if(c.sessionId().equals(o.status().sessionId()))buffer="HELD";
            else if(o.status().lastRelease()!=null && c.sessionId().equals(o.status().lastRelease().sessionId()))buffer="RELEASE_CONFIRMED";
        }
        r.put("deviceBuffer",buffer);r.put("liveness",runtime.liveness(c.deviceId()));return r;
    }
}
