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

# Отпечаток набора устройств ввода. По его изменению видно, что мышь или
# пульт воткнули (или переподключили) уже после того, как дисплей поднялся.
input_fingerprint() {
    grep -c . /proc/bus/input/devices 2>/dev/null
    grep "^N: Name=" /proc/bus/input/devices 2>/dev/null | sort | cksum
}

# Один демон на систему. pkill по имени здесь ненадёжен: он не всегда
# срабатывает из-за SELinux, а после ручных перезапусков экземпляры копились.
PIDFILE=/data/adb/vr_display_mode.pid
if [ -f "$PIDFILE" ]; then
    OLD=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$OLD" ] && [ "$OLD" != "$$" ] && [ -d "/proc/$OLD" ]; then
        kill -9 "$OLD" 2>/dev/null && log "остановлен прежний демон ($OLD)"
    fi
fi
echo $$ > "$PIDFILE"

log "демон: старт"
prev=""
prev_inputs=""

while true; do
    if dp_connected; then
        cur=connected
    else
        cur=disconnected
    fi

    if [ "$cur" != "$prev" ]; then
        log "DisplayPort: $cur"
        if [ "$cur" = "connected" ]; then
            # В режиме гарнитуры дисплей не включаем: иначе он и модуль
            # VR Headset Mode тянули бы коннектор в разные стороны, а
            # пользователь получал бы диалог "делать ли каст" на каждое
            # подключение очков.
            if [ "$(vr_mode)" = headset ]; then
                log "режим гарнитуры — дисплей не включаю"
            else
                try_enable
            fi
        fi
        prev_inputs=$(input_fingerprint)
    elif [ "$cur" = "connected" ]; then
        # Дисплей на месте: следим за появлением новых указывающих устройств,
        # чтобы мышь, воткнутая позже очков, тоже попала на внешний экран.
        inputs=$(input_fingerprint)
        if [ "$inputs" != "$prev_inputs" ]; then
            log "набор устройств ввода изменился — перепривязка указателя"
            pout=$(bind_pointers)
            case "$pout" in
                *"pointer bound"*) log "указатель привязан: $pout" ;;
                *)                 log "привязка не выполнена: $pout" ;;
            esac
            prev_inputs=$inputs
        fi
    fi

    prev=$cur
    sleep "$POLL_INTERVAL"
done
