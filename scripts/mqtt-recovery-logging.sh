#!/usr/bin/env bash
# Run manually in the existing server deployment directory. Never recreate a container.
set -euo pipefail
umask 077
root="$(pwd -P)"
config="$root/mosquitto-lan.conf"
state="$root/.mqtt-recovery-logging-directory"
action="${1:-}"
[[ "$action" =~ ^(enable|capture|restore)$ ]] || { echo 'Usage: bash mqtt-recovery-logging.sh enable|capture|restore'; exit 2; }
broker="$(sudo docker ps -q --filter label=com.docker.compose.project=ppossak-personal --filter label=com.docker.compose.service=broker)"
[[ -n "$broker" && "$broker" != *$'\n'* ]] || { echo 'Expected exactly one running broker'; exit 2; }
mounted="$(sudo docker inspect --format '{{range .Mounts}}{{if eq .Destination "/mosquitto/config/mosquitto-lan.conf"}}{{.Source}}{{end}}{{end}}' "$broker")"
[[ -f "$config" && "$(readlink -f "$config")" == "$mounted" ]] || { echo 'Config bind source does not match this directory'; exit 2; }
if [[ "$action" == enable ]]; then
    [[ ! -e "$state" ]] || { echo 'Diagnostics already active; capture/restore first'; exit 2; }
    mkdir -p "$root/diagnostics"
    directory="$(mktemp -d "$root/diagnostics/mqtt-retry.XXXXXX")"
    cp -p "$config" "$directory/mosquitto-lan.conf.original"
    awk 'BEGIN { print "connection_messages true"; print "log_type all" }
         !/^[[:space:]]*(connection_messages|log_type)[[:space:]]/' "$config" > "$directory/mosquitto-lan.conf.enabled"
    date -u +%Y-%m-%dT%H:%M:%SZ > "$directory/since.txt"
    sudo docker inspect --format '{{.State.StartedAt}} {{.RestartCount}}' "$broker" > "$directory/before.txt"
    printf '%s\n' "$directory" > "$state"
    rollback_enable() {
        trap - ERR
        echo 'ENABLE_FAILED: restoring original config; do not run RETRY'
        if cat "$directory/mosquitto-lan.conf.original" > "$config" && sudo docker kill --signal=HUP "$broker" >/dev/null; then
            rm -- "$state"
            echo 'ORIGINAL_CONFIG_RESTORED'
        else
            echo "RESTORE_NEEDS_REVIEW: backup at $directory"
        fi
        exit 2
    }
    trap rollback_enable ERR
    # Preserve the inode: rename/sed -i can leave a single-file bind mount pointing at old data.
    cat "$directory/mosquitto-lan.conf.enabled" > "$config"
    sudo docker kill --signal=HUP "$broker" >/dev/null
    sleep 2
    sudo docker inspect --format '{{.State.StartedAt}} {{.RestartCount}}' "$broker" > "$directory/after.txt"
    cmp "$directory/before.txt" "$directory/after.txt"
    trap - ERR
    echo "DIAGNOSTICS_ENABLED $directory"
    echo 'Now run the PC read-only preflight, then --apply once. Capture and restore even on failure.'
else
    [[ -f "$state" ]] || { echo 'No active diagnostic state'; exit 2; }
    directory="$(cat "$state")"
    [[ "$directory" == "$root/diagnostics/mqtt-retry."* && -d "$directory" && ! -L "$directory" ]] || exit 2
    if [[ "$action" == capture ]]; then
        backend="$(sudo docker ps -q --filter label=com.docker.compose.project=ppossak-personal --filter label=com.docker.compose.service=backend)"
        [[ -n "$backend" && "$backend" != *$'\n'* ]] || exit 2
        since="$(cat "$directory/since.txt")"
        sudo docker logs --timestamps --since "$since" "$broker" > "$directory/broker.log" 2>&1
        sudo docker logs --timestamps --since "$since" "$backend" > "$directory/backend.log" 2>&1
        echo "CAPTURED $directory/broker.log $directory/backend.log"
        grep -Ei 'esp32-bench|home/water/v2/|OpenSSL|denied|error|memory|closed|disconnect' "$directory/broker.log" | tail -n 100 || true
        grep -Ei 'MQTT|ERROR|WARN|Exception' "$directory/backend.log" | tail -n 50 || true
    else
        # Do not overwrite an unrelated concurrent config edit.
        cmp "$config" "$directory/mosquitto-lan.conf.enabled"
        cat "$directory/mosquitto-lan.conf.original" > "$config"
        sudo docker kill --signal=HUP "$broker" >/dev/null
        rm -- "$state"
        echo "DIAGNOSTICS_RESTORED; evidence kept at $directory"
    fi
fi
