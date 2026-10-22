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
    if (negative || power < 0 || last - first + 1 + power > 4) return false;
    unsigned number = 0;
    int position = 0;
    for (const char *p = digits; p < digits_end; p++) {
        if (*p == '.') continue;
        if (position >= first && position <= last) number = number * 10 + (unsigned)(*p - '0');
        position++;
    }
    while (power-- > 0) number *= 10;
    if (number > 1000) return false;
    *out = number;
    return true;
}
#include "command_control.h"

bool hwm_uuid4(const char *s)
{
    if (!s || strlen(s) != 36 || s[14] != '4' || !strchr("89abAB", s[19])) return false;
    for (unsigned i = 0; i < 36; i++) {
        bool hyphen = i == 8 || i == 13 || i == 18 || i == 23;
        if (hyphen ? s[i] != '-' : hex(s[i]) < 0) return false;
    }
    return true;
}
bool hwm_parse_control(const char *json, size_t length, hwm_control_message *out)
{
    if (!json || !out || !length || length > HWM_CONTROL_MAX) return false;
    reader r = {json,json+length}; hwm_control_message m = {0};
    char event[32] = {0}; unsigned seen = 0;
    if (!take(&r,'{')) return false;
    do {
        char key[65], value[65];
        if (!string(&r,key,sizeof(key)) || !take(&r,':')) return false;
        static const char *keys[] = {"schemaVersion","eventType","deviceId","probeId","commandId","sessionId",
            "targetBootId","windowId","expectedSamples","samplingIntervalMs"};
        unsigned i; for (i=0;i<10 && strcmp(key,keys[i]);i++) {}
        if (i==10 || (seen & (1U<<i))) return false;
        seen |= 1U<<i;
        if (i==0 || i==8 || i==9) {
            unsigned n;
            if (!integer(&r,&n) || n != (i==0?1:i==8?60:1000)) return false;
        } else {
            if (!string(&r,value,sizeof(value))) return false;
            if (i==1) {if(strlen(value)>=sizeof(event)) return false;strcpy(event,value);}
            else if (i==2) {
                size_t n=strlen(value);if(!n)return false;
                for(size_t k=0;k<n;k++) {
                    char c=value[k];bool alnum=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9');
                    if(!alnum && (k==0 || (c!='_'&&c!='-')))return false;
                }
                strcpy(m.device_id,value);
            } else {
                if(!hwm_uuid4(value))return false;
                for(unsigned k=0;k<36;k++)if(value[k]>='A'&&value[k]<='F')value[k]+='a'-'A';
                char *destination=i==3?m.probe_id:i==4?m.command_id:i==5?m.session_id:i==6?m.boot_id:m.window_id;
                strcpy(destination,value);
            }
        }
    } while(take(&r,','));
    if(!take(&r,'}'))return false;
    whitespace(&r);
    if(r.p!=r.end)return false;
    m.probe=!strcmp(event,"STATE_REQUEST");m.retry=!strcmp(event,"RETRY");
    unsigned expected=m.probe?15:m.retry?247:!strcmp(event,"START")?1015:0;
    if(!expected || seen!=expected)return false;
    *out=m;return true;
}
