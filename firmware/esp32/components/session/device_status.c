#include "command_control.h"
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

typedef struct {char *out;size_t used,capacity;bool valid;} writer;
static void append(writer *w,const char *format,...)
{
    if(!w->valid)return;
    va_list args;va_start(args,format);int n=vsnprintf(w->out+w->used,w->capacity-w->used,format,args);va_end(args);
    if(n<0 || (size_t)n>=w->capacity-w->used)w->valid=false;else w->used+=(size_t)n;
}
static const char *boolean(bool b){return b?"true":"false";}
static void optional_uuid(writer *w,const char *id){if(id && *id)append(w,"\"%s\"",id);else append(w,"null");}
size_t hwm_status_json(hwm_command_control *c,const hwm_session *s,uint64_t now,const char *probe,char *out,size_t capacity)
{
    if(!out || !capacity || c->status_seq>=HWM_COUNTER_MAX || now>HWM_CONTROL_UPTIME_MAX){c->fault=true;c->window_live=false;return 0;}
    writer w={out,0,capacity,true};c->status_seq++;
    /* The final representable report already disables new control; counters never wrap. */
    if(c->status_seq==HWM_COUNTER_MAX || s->round>=HWM_COUNTER_MAX || now==HWM_CONTROL_UPTIME_MAX) {
        c->fault=true;c->window_live=false;
    }
    append(&w,"{\"schemaVersion\":1,\"eventType\":\"DEVICE_STATUS\",\"deviceId\":\"%s\",\"bootId\":\"%s\",\"statusSeq\":%lu,\"uptimeMs\":%llu,\"replyToProbeId\":",
        c->device_id,c->boot_id,(unsigned long)c->status_seq,(unsigned long long)now);
    optional_uuid(&w,probe);append(&w,",\"controlWindow\":");
    if(probe && c->window_live && now<c->deadline)append(&w,"{\"windowId\":\"%s\",\"validForMs\":10000}",c->window_id);else append(&w,"null");
    append(&w,",\"state\":\"%s\",\"sessionId\":",hwm_state_name(s->state));optional_uuid(&w,s->session_id);
    append(&w,",\"sampleCount\":%u,\"finalized\":%s,\"round\":%u,\"attempt\":%u,\"controlMode\":\"%s\",\"controlReady\":%s,\"fault\":%s,\"commandResult\":",
        s->count,boolean(s->finalized),s->round,s->attempt,c->mqtt_mode?"MQTT":"UART",boolean(c->ready&&c->mqtt_mode&&!c->fault),c->fault?"\"CONTROL_UNAVAILABLE\"":"null");
    if(c->has_result) {
        append(&w,"{\"commandId\":\"%s\",\"commandType\":\"%s\",\"sessionId\":\"%s\",\"outcome\":\"%s\",\"reason\":",
            c->result.command_id,c->result.retry?"RETRY":"START",c->result.session_id,c->accepted?"ACCEPTED":"REJECTED");
        if(c->reason)append(&w,"\"%s\"",c->reason);else append(&w,"null");append(&w,"}");
    } else append(&w,"null");
    append(&w,",\"lastExecution\":");
    if(c->has_execution)append(&w,"{\"commandId\":\"%s\",\"commandType\":\"%s\",\"sessionId\":\"%s\",\"windowId\":\"%s\"}",
        c->execution.command_id,c->execution.retry?"RETRY":"START",c->execution.session_id,c->execution.window_id);else append(&w,"null");
    append(&w,",\"lastRelease\":");
    if(c->has_release)append(&w,"{\"sessionId\":\"%s\",\"preservationResult\":\"%s\",\"preservedSampleCount\":%u,\"reason\":\"PRESERVATION_CONFIRMED\"}",
        c->release.session_id,c->release.normal?"NORMAL":"INCOMPLETE",c->release.count);else append(&w,"null");
    append(&w,"}");return w.valid && w.used<=HWM_CONTROL_MAX?w.used:0;
}
