#include "session.h"
#include "control.h"
#include <stdio.h>
#include <string.h>

/* Schema-specific bounded reader: all permitted strings decode to ASCII identifiers.
 * Avoid permissive JSON number parsing, duplicate decoded keys and embedded NUL aliases. */
typedef struct { const char *p, *end; } reader;
static void whitespace(reader *r) { while (r->p < r->end && strchr(" \t\r\n", *r->p) && *r->p) r->p++; }
static bool take(reader *r, char c)
{
    whitespace(r);
    if (r->p == r->end || *r->p != c) return false;
    r->p++;
    return true;
}
static int hex(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}
static bool string(reader *r, char *out, size_t capacity)
{
    if (!take(r, '"')) return false;
    size_t n = 0;
    while (r->p < r->end) {
        unsigned char c = (unsigned char)*r->p++;
        if (c == '"') { out[n] = 0; return true; }
        if (c < 32 || c > 127) return false;
        if (c == '\\') {
            if (r->p == r->end) return false;
            c = (unsigned char)*r->p++;
            if (c == 'u') {
                if (r->end - r->p < 4) return false;
                unsigned code = 0;
                for (int i = 0; i < 4; i++) {
                    int digit = hex(*r->p++);
                    if (digit < 0) return false;
                    code = code * 16 + (unsigned)digit;
                }
                if (!code || code > 127) return false;
                c = (unsigned char)code;
            } else if (c == 'b') c = '\b';
            else if (c == 'f') c = '\f';
            else if (c == 'n') c = '\n';
            else if (c == 'r') c = '\r';
            else if (c == 't') c = '\t';
            else if (c != '"' && c != '\\' && c != '/') return false;
        }
        if (n + 1 >= capacity) return false;
        out[n++] = (char)c;
    }
    return false;
}
static bool digit(reader *r) { return r->p < r->end && *r->p >= '0' && *r->p <= '9'; }
static bool integer(reader *r, unsigned *out)
{
    whitespace(r);
    bool negative = r->p < r->end && *r->p == '-';
    if (negative) r->p++;
    const char *digits = r->p;
    if (!digit(r)) return false;
    if (*r->p == '0') r->p++;
    else while (digit(r)) r->p++;
    int fraction = 0;
    if (r->p < r->end && *r->p == '.') {
        r->p++;
        if (!digit(r)) return false;
        while (digit(r)) { r->p++; fraction++; }
    }
    const char *digits_end = r->p;
    int exponent = 0;
    if (r->p < r->end && (*r->p == 'e' || *r->p == 'E')) {
        r->p++;
        bool minus = r->p < r->end && *r->p == '-';
        if (r->p < r->end && (*r->p == '+' || minus)) r->p++;
        if (!digit(r)) return false;
        while (digit(r)) {
            if (exponent < 65536) exponent = exponent * 10 + (*r->p - '0');
            r->p++;
        }
        if (minus) exponent = -exponent;
    }
    int count = 0, first = -1, last = -1;
    for (const char *p = digits; p < digits_end; p++) {
        if (*p == '.') continue;
        if (*p != '0') { if (first < 0) first = count; last = count; }
        count++;
    }
    if (first < 0) { *out = 0; return true; }
    int power = exponent - fraction + count - last - 1;
    if (negative || power < 0 || last - first + 1 + power > 2) return false;
    unsigned number = 0;
    int position = 0;
    for (const char *p = digits; p < digits_end; p++) {
        if (*p == '.') continue;
        if (position >= first && position <= last) number = number * 10 + (unsigned)(*p - '0');
        position++;
    }
    while (power-- > 0) number *= 10;
    if (number > 60) return false;
    *out = number;
    return true;
}
static bool comparison(reader *r, hwm_confirmation *c)
{
    whitespace(r);
    if (r->end - r->p >= 4 && memcmp(r->p, "null", 4) == 0) { r->p += 4; return true; }
    if (!take(r, '{')) return false;
    c->has_comparison = true;
    unsigned seen = 0;
    do {
        char key[65];
        if (!string(r, key, sizeof(key)) || !take(r, ':')) return false;
        unsigned bit = !strcmp(key, "identicalCount") ? 1 : !strcmp(key, "conflictCount") ? 2 : 0;
        if (!bit || (seen & bit)) return false;
        seen |= bit;
        if (!integer(r, bit == 1 ? &c->identical : &c->conflicts)) return false;
    } while (take(r, ','));
    return seen == 3 && take(r, '}');
}

bool hwm_parse_confirmation_version(const char *json, size_t length, hwm_confirmation *out, unsigned version)
{
    if (!json || !out || length == 0 || length > HWM_MAX_PAYLOAD) return false;
    if (version != 1 && version != 2) return false;
    hwm_confirmation c = {0};
    reader r = { json, json + length };
    if (!take(&r, '{')) return false;
    unsigned seen = 0;
    do {
        char key[65], value[65];
        if (!string(&r, key, sizeof(key)) || !take(&r, ':')) return false;
        static const char *keys[] = {"schemaVersion", "eventType", "deviceId", "sessionId", "preservationResult", "preservedSampleCount", "comparison"};
        unsigned i;
        for (i = 0; i < 7 && strcmp(key, keys[i]); i++) {}
        if (i == 7 || (seen & (1U << i))) return false;
        seen |= 1U << i;
        if (i == 0 || i == 5) {
            unsigned number;
            if (!integer(&r, &number) || (i == 0 && number != version)) return false;
            if (i == 5) c.count = number;
        } else if (i == 6) {
            if (!comparison(&r, &c)) return false;
        } else {
            if (!string(&r, value, sizeof(value))) return false;
            if (i == 1 && strcmp(value, "SESSION_PRESERVED")) return false;
            if (i == 2) {
                size_t n = strlen(value);
                if (!n) return false;
                for (size_t k = 0; k < n; k++) {
                    char ch = value[k];
                    bool alnum = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
                    if (!alnum && (k == 0 || (ch != '_' && ch != '-'))) return false;
                }
                memcpy(c.device_id, value, n + 1);
            }
            if (i == 3) {
                char line[72];
                snprintf(line, sizeof(line), "START %s", value);
                hwm_command command;
                if (!hwm_parse_command(line, &command)) return false;
                memcpy(c.session_id, command.session_id, 37);
            }
            if (i == 4) {
                if (strcmp(value, "NORMAL") && strcmp(value, "INCOMPLETE")) return false;
                c.normal = !strcmp(value, "NORMAL");
            }
        }
    } while (take(&r, ','));
    if (seen != 127 || !take(&r, '}')) return false;
    whitespace(&r);
    if (r.p != r.end || c.normal != (c.count == 60)
        || (c.has_comparison && c.identical + c.conflicts != c.count)) return false;
    *out = c;
    return true;
}

bool hwm_parse_confirmation(const char *json, size_t length, hwm_confirmation *out)
{ return hwm_parse_confirmation_version(json, length, out, 1); }
