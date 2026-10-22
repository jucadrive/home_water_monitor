#ifndef HWM_FRAME_H
#define HWM_FRAME_H
#include "session.h"

typedef struct {
    char body[HWM_MAX_PAYLOAD + 1];
    size_t total, used;
    bool active;
} hwm_frame;
/* First fragment's metadata is already checked against MQTT 5/topic/qos/retain/PFI/content type.
 * Returns -1 invalid, 0 incomplete, 1 complete. No unbounded allocation or stale assembly. */
int hwm_frame_feed(hwm_frame *frame, size_t total, size_t offset, const char *data, size_t length, bool metadata_ok);
#endif
