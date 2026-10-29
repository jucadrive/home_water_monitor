/* Runs the production pure-C sources on a host, without Wi-Fi, GPIO or secrets. */
#include "session.h"
#include "temperature.h"
#include "tds_sensor.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static hwm_session session;
static const char *id = "145abec7-dc82-4ca1-9ced-1f976694a338";
static bool reading(int raw_count, int temperature, hwm_sensor_value *out)
{
    int16_t raw[HWM_SENSOR_WINDOW];
    for (unsigned i = 0; i < HWM_SENSOR_WINDOW; i++) raw[i] = raw_count;
    return hwm_tds_voltage(raw, out) && hwm_tds_compensate(out, temperature);
}
static void begin(unsigned mode)
{
    assert(hwm_init(&session, "esp32-bench"));
    assert(hwm_set_temperature_mode(&session, mode));
    assert(hwm_start(&session, id, 60, 0));
}
static void fill(unsigned count, int temperature)
{
    for (unsigned i = 1; i <= count; i++) {
        hwm_sensor_value v;
        assert(reading(8000, temperature, &v));
        assert(hwm_sample_append_temperature(&session, i*1000, i*1000+10,
            v.tds_hundredths, v.voltage_hundredths, temperature, i*1000-100));
    }
}
static void tests(void)
{
    hwm_temperature t = {.valid = true, .ten_thousandths = 250000, .at_ms = 1000};
    assert(hwm_temperature_valid(&t, 3000, 0));
    assert(!hwm_temperature_valid(&t, 3001, 0));
    assert(!hwm_temperature_valid(&t, 999, 0));
    assert(!hwm_temperature_valid(&t, 1000, 1001));
    t.valid = false; assert(!hwm_temperature_valid(&t, 1000, 0));
    t.valid = true; t.ten_thousandths = -1; assert(!hwm_temperature_valid(&t, 1000, 0));
    t.ten_thousandths = 500001; assert(!hwm_temperature_valid(&t, 1000, 0));
    t.ten_thousandths = 0; assert(hwm_temperature_valid(&t, 1000, 0));
    hwm_sensor_value v; assert(!reading(-1, 250000, &v)); assert(!reading(18401, 250000, &v));
    assert(!reading(8000, -1, &v)); assert(!reading(8000, 500001, &v));
    assert(!reading(8000, 250001, &v)); /* Reject a value outside the emulated sensor's resolution. */
    assert(reading(0, 0, &v) && v.tds_hundredths == 0);
    int16_t raw[HWM_SENSOR_WINDOW];
    for (unsigned i=0;i<HWM_SENSOR_WINDOW;i++) raw[i]=8000;
    hwm_sensor_value legacy;
    assert(hwm_tds_value(raw, &legacy) && reading(8000,250000,&v));
    assert(v.tds_hundredths == legacy.tds_hundredths && v.voltage_hundredths == legacy.voltage_hundredths);
    /* A high raw voltage can be valid after warm-water compensation. */
    assert(!hwm_tds_value((int16_t[HWM_SENSOR_WINDOW]){0}, NULL));
    assert(reading(18400,500000,&v));
    begin(1);
    assert(!hwm_set_temperature_mode(&session,2));
    assert(!hwm_sample_append_observed(&session,1000,1000,100,100));
    assert(!hwm_sample_append_temperature(&session,1000,1000,100,100,250000,1001));
    assert(session.count == 0);
    fill(60,250000);
    assert(session.finalized && session.count == 60 && session.payload_len <= 16384);
    assert(strstr(session.payload,"\"schemaVersion\":2") && strstr(session.payload,"\"temperatureSource\":\"SIMULATED\""));
    assert(session.samples[9].warmup && !session.samples[10].warmup);
    char frozen[HWM_MAX_PAYLOAD+1];strcpy(frozen,session.payload);
    assert(!hwm_sample_append_temperature(&session,61000,61000,100,100,250000,61000));
    session.state=HWM_MANUAL_WAIT;
    assert(hwm_retry(&session,id,61000)); assert(!strcmp(frozen,session.payload));
    const char *ack="{\"schemaVersion\":2,\"eventType\":\"SESSION_PRESERVED\",\"deviceId\":\"esp32-bench\",\"sessionId\":\"145abec7-dc82-4ca1-9ced-1f976694a338\",\"preservationResult\":\"NORMAL\",\"preservedSampleCount\":60,\"comparison\":null}";
    hwm_confirmation c;
    assert(!hwm_parse_confirmation(ack,strlen(ack),&c));
    assert(hwm_parse_confirmation_version(ack,strlen(ack),&c,2));
    assert(hwm_release(&session,&c)); assert(session.state==HWM_IDLE && session.count==0 && !session.payload_len);
    for (unsigned prefix=0;prefix<60;prefix++) {
        begin(1);fill(prefix,200000);
        assert(hwm_finish_incomplete(&session,(prefix+1)*1000));
        assert(session.finalized && session.count==prefix);
    }
    begin(0);
    assert(hwm_sample_append_observed(&session,1000,1000,100,100));
    assert(hwm_finish_incomplete(&session,1100));
    assert(strstr(session.payload,"\"schemaVersion\":1") && strstr(session.payload,"temperatureAssumedC"));
    assert(!strstr(session.payload,"temperatureSource"));
    puts("TEMPERATURE_HOST_INVARIANTS_PASS prefixes=60");
}
int main(int argc, char **argv)
{
    if(argc==2 && !strcmp(argv[1],"--vectors")) {
        int raw,temp;
        while(scanf("%d %d",&raw,&temp)==2) {
            hwm_sensor_value v;bool ok=reading(raw,temp,&v);
            printf("%d %ld %ld\n",ok,ok?(long)v.tds_hundredths:0L,ok?(long)v.voltage_hundredths:0L);
        }
        return 0;
    }
    if(argc==4 && !strcmp(argv[1],"--payload")) {
        unsigned prefix=(unsigned)atoi(argv[3]); if(prefix>60)return 2;
        begin(1);fill(prefix,atoi(argv[2]));
        if(prefix<60)assert(hwm_finish_incomplete(&session,(prefix+1)*1000));
        puts(session.payload);return 0;
    }
    tests();return 0;
}
