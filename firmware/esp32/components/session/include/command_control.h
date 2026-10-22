#ifndef HWM_COMMAND_CONTROL_H
#define HWM_COMMAND_CONTROL_H
#include "session.h"
#define HWM_CONTROL_MAX 4096
#define HWM_CONTROL_UPTIME_MAX 9007199254740991ULL
typedef struct {
    bool probe, retry;
    char device_id[65], probe_id[37], command_id[37], session_id[37], boot_id[37], window_id[37];
} hwm_control_message;
typedef struct {
    char device_id[65], boot_id[37], window_id[37];
    bool mqtt_mode, ready, fault, window_live, has_execution, has_result, accepted, has_release;
    uint64_t deadline;
    uint32_t status_seq;
    hwm_control_message execution, result;
    const char *reason;
    hwm_confirmation release;
} hwm_command_control;
bool hwm_uuid4(const char *text);
bool hwm_parse_control(const char *json, size_t length, hwm_control_message *out);
bool hwm_control_init(hwm_command_control *c, const char *device, const char *boot, bool mqtt_mode);
void hwm_control_probe(hwm_command_control *c, const hwm_session *s, const char *window, uint64_t now);
bool hwm_control_execute(hwm_command_control *c, hwm_session *s, const hwm_control_message *m, uint64_t now);
void hwm_control_released(hwm_command_control *c, const hwm_confirmation *release);
size_t hwm_status_json(hwm_command_control *c, const hwm_session *s, uint64_t now,
        const char *reply_probe, char *out, size_t capacity);
#endif
