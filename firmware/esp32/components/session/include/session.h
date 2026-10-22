#ifndef HWM_SESSION_H
#define HWM_SESSION_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define HWM_MAX_PAYLOAD 16384
#define HWM_MAX_SAMPLES 60
#define HWM_PREPARE_MS 10000
#define HWM_PUBACK_MS 10000
#define HWM_CONFIRM_MS 30000

typedef enum {
    HWM_IDLE, HWM_MEASURING, HWM_PREPARING, HWM_WAIT_PUBACK,
    HWM_WAIT_CONFIRMATION, HWM_BACKOFF, HWM_MANUAL_WAIT
} hwm_state;
typedef struct {
    unsigned seq;
    uint32_t elapsed_ms;
    int32_t tds_hundredths;
    bool warmup;
    int32_t voltage_hundredths;
} hwm_sample;
typedef struct {
    char device_id[65], session_id[37];
    unsigned count, identical, conflicts;
    bool normal, has_comparison;
} hwm_confirmation;
typedef struct {
    hwm_state state;
    char device_id[65], session_id[37], last_session_id[37];
    hwm_sample samples[HWM_MAX_SAMPLES];
    unsigned count, target, attempt, round;
    uint32_t generation;
    int msg_id;
    uint64_t started_ms, deadline_ms;
    bool finalized;
    size_t payload_len;
    char payload[HWM_MAX_PAYLOAD + 1];
} hwm_session;

bool hwm_init(hwm_session *s, const char *device_id);
bool hwm_start(hwm_session *s, const char *uuid, unsigned target, uint64_t now);
bool hwm_sample_simulated(hwm_session *s, uint64_t now);
bool hwm_retry(hwm_session *s, const char *uuid, uint64_t now);
bool hwm_submitted(hwm_session *s, int msg_id, uint64_t now);
bool hwm_puback(hwm_session *s, uint32_t generation, int msg_id, bool success, uint64_t now, unsigned jitter_ms);
void hwm_fail(hwm_session *s, uint64_t now, unsigned jitter_ms);
void hwm_tick(hwm_session *s, uint64_t now, unsigned jitter_ms);
unsigned hwm_retry_cap_ms(const hwm_session *s);
bool hwm_confirmation_matches(const hwm_session *s, const hwm_confirmation *confirmation);
/* Caller must stop/destroy the active transmitter before releasing the retained payload. */
bool hwm_release(hwm_session *s, const hwm_confirmation *confirmation);
size_t hwm_serialize(const hwm_session *s, char *out, size_t capacity);
bool hwm_parse_confirmation(const char *json, size_t length, hwm_confirmation *out);
const char *hwm_state_name(hwm_state state);
#endif
