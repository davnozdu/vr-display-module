#!/system/bin/sh
# Следит за DisplayPort и включает внешний дисплей, как только очки воткнули.

MODDIR=${MODDIR:-/data/adb/modules/vr_display_mode}
. "$MODDIR/common.sh"

POLL_INTERVAL=2

# Логический дисплей появляется не мгновенно после того, как коннектор
# отрапортовал connected, поэтому пробуем несколько раз.
try_enable() {
    i=0
    while [ $i -lt 10 ]; do
        out=$(enable_external_display)
        case "$out" in
            *"enabled display"*)
                log "дисплей включён: $out"
                return 0
                ;;
        esac
        i=$((i + 1))
        sleep 1
    done
    log "не удалось включить дисплей за 10 попыток: $out"
    return 1
}

log "демон: старт"
prev=""

while true; do
    if dp_connected; then
        cur=connected
    else
        cur=disconnected
    fi

    if [ "$cur" != "$prev" ]; then
        log "DisplayPort: $cur"
        [ "$cur" = "connected" ] && try_enable
    fi

    prev=$cur
    sleep "$POLL_INTERVAL"
done
