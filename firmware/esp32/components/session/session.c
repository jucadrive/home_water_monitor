#include "session.h"
#include "control.h"
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

static bool normalized_uuid(const char *uuid, char out[37])
{
    if (!uuid || strlen(uuid) != 36) return false;
    char line[43];
    snprintf(line, sizeof(line), "START %s", uuid);
    hwm_command cmd;
    if (!hwm_parse_command(line, &cmd)) return false;
    memcpy(out, cmd.session_id, 37);
    return true;
}

bool hwm_init(hwm_session *s, const char *device_id)
{
    if (!s || !device_id) return false;
    size_t len = strlen(device_id);
    if (len < 1 || len > 64) return false;
    for (size_t i = 0; i < len; i++) {
        char c = device_id[i];
        bool alnum = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
        if (!alnum && (i == 0 || (c != '-' && c != '_'))) return false;
    }
    memset(s, 0, sizeof(*s));
    memcpy(s->device_id, device_id, len + 1);
    return true;
}

static void prepare(hwm_session *s, uint64_t now)
{
    s->state = HWM_PREPARING;
    s->attempt++;
    s->generation++;
    s->msg_id = 0;
    s->deadline_ms = now + HWM_PREPARE_MS;
}

static bool finalize(hwm_session *s, uint64_t now)
{
    s->payload_len = hwm_serialize(s, s->payload, sizeof(s->payload));
    if (!s->payload_len) return false;
    s->finalized = true;
    prepare(s, now);
    return true;
}

bool hwm_start(hwm_session *s, const char *uuid, unsigned target, uint64_t now)
{
    char canonical[37];
    if (!s || s->state != HWM_IDLE || target > 60 || !normalized_uuid(uuid, canonical)
        || strcmp(canonical, s->last_session_id) == 0) return false;
    memcpy(s->session_id, canonical, 37);
    s->target = target;
    s->count = 0;
    s->attempt = 0;
    s->round = 1;
    s->started_ms = now;
    s->state = HWM_MEASURING;
    s->deadline_ms = now + 1000;
    return target != 0 || finalize(s, now);
}

bool hwm_sample_simulated(hwm_session *s, uint64_t now)
{
    if (!s || s->state != HWM_MEASURING || now < s->deadline_ms || now < s->started_ms
        || now - s->started_ms > INT32_MAX || s->count >= s->target) return false;
    unsigned i = s->count;
    s->samples[i] = (hwm_sample){ .seq = i + 1, .elapsed_ms = (uint32_t)(now - s->started_ms),
        .tds_hundredths = 20000 + (int32_t)i, .warmup = i < 10, .voltage_hundredths = 100000 + (int32_t)i };
    s->count++;
    s->deadline_ms = now + 1000;
    return s->count != s->target || finalize(s, now);
}

bool hwm_retry(hwm_session *s, const char *uuid, uint64_t now)
{
    char canonical[37];
    if (!s || s->state != HWM_MANUAL_WAIT || s->round >= HWM_COUNTER_MAX || !normalized_uuid(uuid, canonical)
        || strcmp(canonical, s->session_id) != 0) return false;
    s->round++;
    s->attempt = 0;
    prepare(s, now);
    return true;
}

bool hwm_submitted(hwm_session *s, int msg_id, uint64_t now)
{
    if (!s || s->state != HWM_PREPARING || msg_id <= 0 || now >= s->deadline_ms) return false;
    s->msg_id = msg_id;
    s->state = HWM_WAIT_PUBACK;
    s->deadline_ms = now + HWM_PUBACK_MS;
    return true;
}

unsigned hwm_retry_cap_ms(const hwm_session *s)
{
    return !s || s->attempt == 0 || s->attempt >= 5 ? 0 : 1000U << (s->attempt - 1);
}

void hwm_fail(hwm_session *s, uint64_t now, unsigned jitter_ms)
{
    if (!s || (s->state != HWM_PREPARING && s->state != HWM_WAIT_PUBACK && s->state != HWM_WAIT_CONFIRMATION)) return;
    if (s->attempt >= 5) { s->state = HWM_MANUAL_WAIT; return; }
    unsigned cap = hwm_retry_cap_ms(s);
    s->state = HWM_BACKOFF;
    s->deadline_ms = now + (jitter_ms <= cap ? jitter_ms : cap);
}

bool hwm_puback(hwm_session *s, uint32_t generation, int msg_id, bool success, uint64_t now, unsigned jitter_ms)
{
    if (!s || s->state != HWM_WAIT_PUBACK || s->generation != generation || s->msg_id != msg_id) return false;
    if (now >= s->deadline_ms) { hwm_fail(s, now, jitter_ms); return true; }
    if (success) {
        s->state = HWM_WAIT_CONFIRMATION;
        s->deadline_ms = now + HWM_CONFIRM_MS;
    } else hwm_fail(s, now, jitter_ms);
    return true;
}

void hwm_tick(hwm_session *s, uint64_t now, unsigned jitter_ms)
{
    if (!s || now < s->deadline_ms) return;
    if (s->state == HWM_BACKOFF) prepare(s, now);
    else hwm_fail(s, now, jitter_ms);
}

bool hwm_confirmation_matches(const hwm_session *s, const hwm_confirmation *c)
{
    return s && c && s->finalized && s->state != HWM_IDLE && s->state != HWM_MEASURING
        && strcmp(s->device_id, c->device_id) == 0 && strcmp(s->session_id, c->session_id) == 0
        && c->count == s->count && c->normal == (s->count == 60)
        && (!c->has_comparison || (c->identical <= 60 && c->conflicts <= 60 && c->identical + c->conflicts == c->count));
}

bool hwm_release(hwm_session *s, const hwm_confirmation *c)
{
    if (!hwm_confirmation_matches(s, c)) return false;
    memcpy(s->last_session_id, s->session_id, 37);
    memset(s->samples, 0, sizeof(s->samples));
    memset(s->payload, 0, sizeof(s->payload));
    memset(s->session_id, 0, sizeof(s->session_id));
    s->payload_len = 0;
    s->count = s->target = s->attempt = s->round = 0;
    s->msg_id = 0;
    s->deadline_ms = 0;
    s->finalized = false;
    s->generation++;
    s->state = HWM_IDLE;
    return true;
}

static bool append(char *out, size_t capacity, size_t *used, const char *format, ...)
{
    if (*used >= capacity) return false;
    va_list args;
    va_start(args, format);
    int n = vsnprintf(out + *used, capacity - *used, format, args);
    va_end(args);
    if (n < 0 || (size_t)n >= capacity - *used) return false;
    *used += (size_t)n;
    return true;
}

size_t hwm_serialize(const hwm_session *s, char *out, size_t capacity)
{
    if (!s || !out || !capacity || s->count > 60) return 0;
    size_t used = 0;
    if (!append(out, capacity, &used, "{\"schemaVersion\":1,\"eventType\":\"SESSION_RESULT\",\"deviceId\":\"%s\",\"sessionId\":\"%s\","
        "\"startedAt\":null,\"expectedSamples\":60,\"samplingIntervalMs\":1000,\"temperatureAssumedC\":25.00,"
        "\"calibrationVersion\":\"simulated-v1\",\"measurements\":[", s->device_id, s->session_id)) return 0;
    for (unsigned i = 0; i < s->count; i++) {
        const hwm_sample *m = &s->samples[i];
        /* Simulated source uses nonnegative integer hundredths; no binary-float conversion. */
        if (m->tds_hundredths < 0 || m->voltage_hundredths < 0) return 0;
        if (!append(out, capacity, &used, "%s{\"seq\":%u,\"elapsedMs\":%lu,\"tdsPpm\":%ld.%02ld,\"isWarmup\":%s,\"rawVoltageMv\":%ld.%02ld}",
            i ? "," : "", m->seq, (unsigned long)m->elapsed_ms, (long)(m->tds_hundredths / 100),
            (long)(m->tds_hundredths % 100), m->warmup ? "true" : "false", (long)(m->voltage_hundredths / 100),
            (long)(m->voltage_hundredths % 100))) return 0;
    }
    return append(out, capacity, &used, "]}") && used <= HWM_MAX_PAYLOAD ? used : 0;
}

const char *hwm_state_name(hwm_state state)
{
    static const char *names[] = {"IDLE", "MEASURING", "PREPARING", "WAIT_PUBACK", "WAIT_CONFIRMATION", "BACKOFF", "MANUAL_WAIT"};
    return state >= HWM_IDLE && state <= HWM_MANUAL_WAIT ? names[state] : "INVALID";
}
