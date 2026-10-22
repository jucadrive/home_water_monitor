#ifndef HWM_TRANSPORT_H
#define HWM_TRANSPORT_H
#include "session.h"

typedef struct {
    const char *ssid, *wifi_password, *broker_uri, *device_id, *username, *password;
    bool open_wifi;
} hwm_transport_config;
typedef enum {
    HWM_NET_NONE, HWM_NET_WIFI_UP, HWM_NET_WIFI_DOWN, HWM_NET_RX_CONNECTED, HWM_NET_RX_READY,
    HWM_NET_RX_DOWN, HWM_NET_TX_CONNECTED, HWM_NET_TX_DOWN, HWM_NET_PUBACK,
    HWM_NET_CONFIRMATION, HWM_NET_INVALID_CONFIRMATION, HWM_NET_OVERFLOW
} hwm_net_kind;
typedef struct {
    hwm_net_kind kind;
    uint32_t generation;
    int msg_id;
    bool success;
    hwm_confirmation confirmation;
} hwm_net_event;

bool hwm_transport_init(const hwm_transport_config *config);
/* Called only by the controller task. SDK callbacks never own application state. */
void hwm_transport_poll(uint64_t now);
bool hwm_transport_receive(hwm_net_event *event, uint64_t now);
bool hwm_transport_rx_ready(void);
bool hwm_transport_wifi_up(void);
unsigned hwm_transport_wifi_reason(void);
bool hwm_transport_tx_active(void);
bool hwm_transport_start_tx(uint32_t generation);
int hwm_transport_publish(const char *payload, size_t length);
bool hwm_transport_stop_tx(void);
unsigned hwm_random_jitter(unsigned cap);
#endif
