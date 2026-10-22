#ifndef HWM_CONTROL_H
#define HWM_CONTROL_H
#include <stdbool.h>

typedef enum { HWM_COMMAND_INVALID, HWM_COMMAND_STATUS, HWM_COMMAND_START, HWM_COMMAND_RETRY } hwm_command_type;
typedef struct { hwm_command_type type; char session_id[37]; } hwm_command;

/* Parse one complete bounded line; UUID comparisons use normalized lowercase text. */
bool hwm_parse_command(const char *line, hwm_command *command);
#endif
