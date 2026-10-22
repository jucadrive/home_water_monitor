#include "frame.h"
#include <string.h>

int hwm_frame_feed(hwm_frame *f, size_t total, size_t offset, const char *data, size_t length, bool metadata_ok)
{
    if (!f) return -1;
    if (offset == 0) {
        bool overlap = f->active;
        f->active = false;
        f->used = 0;
        if (overlap || !metadata_ok || !total || total > HWM_MAX_PAYLOAD) return -1;
        f->total = total;
        f->active = true;
    }
    if (!f->active || !data || !length || f->total != total || offset != f->used || length > f->total - f->used) {
        f->active = false;
        return -1;
    }
    memcpy(f->body + f->used, data, length);
    f->used += length;
    if (f->used != f->total) return 0;
    f->body[f->used] = 0;
    f->active = false;
    return 1;
}
