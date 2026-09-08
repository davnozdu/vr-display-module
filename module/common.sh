#!/system/bin/sh
# Общее для скриптов модуля.

MODDIR=${MODDIR:-/data/adb/modules/vr_display_mode}
LOG=/data/adb/vr_display_mode.log
PKG=com.davnozdu.vrapp

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"
    # Лог не должен расти бесконечно: телефон работает месяцами без перезагрузки.
    if [ "$(wc -c < "$LOG" 2>/dev/null || echo 0)" -gt 262144 ]; then
        tail -c 131072 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
    fi
}

# Узлы DisplayPort. Имя коннектора зависит от платформы (на OnePlus 15 это
# card0-DP-1), поэтому берём все подходящие, а не один зашитый путь.
dp_nodes() {
    for f in /sys/class/drm/*-DP-*/status; do
        [ -f "$f" ] && echo "$f"
    done
}

dp_connected() {
    for f in $(dp_nodes); do
        [ "$(cat "$f" 2>/dev/null)" = "connected" ] && return 0
    done
    return 1
}

# Включение внешнего дисплея. Системный вызов живёт в dex и требует root:
# enableConnectedDisplay защищён signature-правом MANAGE_DISPLAYS, которое
# обычному приложению не выдать, но проверка прав пропускает uid 0.
enable_external_display() {
    CLASSPATH="$MODDIR/vrdisplay.dex" app_process /system/bin com.davnozdu.vrdisplay.DisplayCtl enable 2>&1
}
