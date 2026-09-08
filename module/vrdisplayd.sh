#!/system/bin/sh
# Следит за DisplayPort и включает внешний дисплей, как только очки воткнули.

MODDIR=${MODDIR:-${0%/*}}
case "$MODDIR" in ""|.|"$0") MODDIR=/data/adb/modules/vr_display_mode;; esac
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
                # Пульт может подняться позже самого дисплея, поэтому
                # привязываем указатель несколько раз с паузой.
                j=0
                while [ $j -lt 5 ]; do
                    pout=$(bind_pointers)
                    case "$pout" in
                        *"pointer bound"*)
                            log "указатель привязан: $pout"
                            break
                            ;;
                    esac
                    j=$((j + 1))
                    sleep 2
                done
                [ $j -eq 5 ] && log "указатель привязать не удалось: $pout"
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
