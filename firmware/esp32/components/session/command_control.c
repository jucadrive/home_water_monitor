#include "command_control.h"
#include <string.h>

bool hwm_control_init(hwm_command_control *c,const char *device,const char *boot,bool mqtt)
{
    if(!c || !device || !strlen(device) || strlen(device)>64 || !hwm_uuid4(boot))return false;
    memset(c,0,sizeof(*c));strcpy(c->device_id,device);strcpy(c->boot_id,boot);c->mqtt_mode=mqtt;return true;
}
void hwm_control_probe(hwm_command_control *c,const hwm_session *s,const char *window,uint64_t now)
{
    c->window_live=false;
    if(c->mqtt_mode && c->ready && !c->fault && hwm_uuid4(window) && (s->state==HWM_IDLE || s->state==HWM_MANUAL_WAIT)) {
        strcpy(c->window_id,window);c->deadline=now+10000;c->window_live=true;
    }
}
static bool same(const hwm_control_message *a,const hwm_control_message *b)
{
    return a->retry==b->retry && !strcmp(a->device_id,b->device_id) && !strcmp(a->command_id,b->command_id)
        && !strcmp(a->session_id,b->session_id) && !strcmp(a->boot_id,b->boot_id) && !strcmp(a->window_id,b->window_id);
}
static bool result(hwm_command_control *c,const hwm_control_message *m,const char *reason)
{
    c->has_result=true;c->result=*m;c->reason=reason;c->accepted=reason==NULL;return c->accepted;
}
bool hwm_control_execute(hwm_command_control *c,hwm_session *s,const hwm_control_message *m,uint64_t now)
{
    if(m->probe || strcmp(m->device_id,c->device_id))return false;
    if(c->has_execution && !strcmp(m->command_id,c->execution.command_id))return result(c,m,same(m,&c->execution)?NULL:"COMMAND_CONFLICT");
    if(c->status_seq>=HWM_COUNTER_MAX || s->round>=HWM_COUNTER_MAX || now>=HWM_CONTROL_UPTIME_MAX) {
        c->fault=true;c->window_live=false;return result(c,m,"DEVICE_FAULT");
    }
    if(strcmp(m->boot_id,c->boot_id))return result(c,m,"BOOT_MISMATCH");
    if(!c->window_live || strcmp(m->window_id,c->window_id))return result(c,m,"WINDOW_INVALID");
    if(now>=c->deadline)return result(c,m,"WINDOW_EXPIRED");
    if(!c->mqtt_mode || !c->ready)return result(c,m,"NOT_READY");
    if(c->fault)return result(c,m,"DEVICE_FAULT");
    if(m->retry) {
        if(s->state==HWM_IDLE)return result(c,m,"NO_PENDING_BUFFER");
        if(strcmp(s->session_id,m->session_id))return result(c,m,"SESSION_MISMATCH");
        if(s->state!=HWM_MANUAL_WAIT)return result(c,m,"BUSY");
    } else {
        if(s->state!=HWM_IDLE)return result(c,m,s->state==HWM_MEASURING?"BUSY":"BUFFER_PENDING");
        if(!strcmp(s->last_session_id,m->session_id))return result(c,m,"SESSION_REUSED");
    }
    c->window_live=false;
    bool accepted=m->retry?hwm_retry(s,m->session_id,now):hwm_start(s,m->session_id,60,now);
    if(!accepted){c->fault=true;return result(c,m,"DEVICE_FAULT");}
    c->execution=*m;c->has_execution=true;return result(c,m,NULL);
}
void hwm_control_released(hwm_command_control *c,const hwm_confirmation *release){c->release=*release;c->has_release=true;}
