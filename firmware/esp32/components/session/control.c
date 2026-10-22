#include "control.h"
#include <ctype.h>
#include <stddef.h>
#include <string.h>

bool hwm_parse_command(const char *line, hwm_command *command)
{
    if (!command) return false;
    memset(command, 0, sizeof(*command));
    if (!line) return false;
    if (strcmp(line, "STATUS") == 0) {
        command->type = HWM_COMMAND_STATUS;
        return true;
    }
    const char *uuid;
    if (strncmp(line, "START ", 6) == 0) {
        command->type = HWM_COMMAND_START;
        uuid = line + 6;
    } else if (strncmp(line, "RETRY ", 6) == 0) {
        command->type = HWM_COMMAND_RETRY;
        uuid = line + 6;
    } else return false;
    if (strlen(uuid) != 36) goto invalid;
    for (size_t i = 0; i < 36; i++) {
        bool hyphen = i == 8 || i == 13 || i == 18 || i == 23;
        if ((hyphen && uuid[i] != '-') || (!hyphen && !isxdigit((unsigned char)uuid[i]))) goto invalid;
        command->session_id[i] = (char)tolower((unsigned char)uuid[i]);
    }
    return true;
invalid:
    memset(command, 0, sizeof(*command));
    return false;
}
