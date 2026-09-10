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
#
# От режима очков (/data/adb/vr_mode) включение не зависит намеренно:
# без успешного линка очки не поднимают аудиоусилитель, и в режиме
# гарнитуры не будет звука. Картинку сразу после этого гасит модуль
# VR Headset Mode.
enable_external_display() {
    CLASSPATH="$MODDIR/vrdisplay.dex" app_process /system/bin com.davnozdu.vrdisplay.DisplayCtl enable 2>&1
}

# Привязка указателя к внешнему дисплею. Без неё курсор пульта остаётся на
# экране телефона, и перебросить его удавалось только передёргиванием пульта.
bind_pointers() {
    CLASSPATH="$MODDIR/vrdisplay.dex" app_process /system/bin com.davnozdu.vrdisplay.DisplayCtl pointer 2>&1
}

# Поднимает сервис приложения. Нужно потому, что модуль ставит и обновляет
# APK уже после BOOT_COMPLETED: своего автозапуска приложение в этот момент
# не получит и молчало бы до следующей перезагрузки. Выключенный тумблер
# мониторинга сервис проверяет сам и в этом случае сразу останавливается.
start_app_service() {
    if pm path "$PKG" >/dev/null 2>&1; then
        if am start-foreground-service -n "$PKG/.UsbMonitorService" >/dev/null 2>&1; then
            log "сервис приложения запущен"
        else
            log "не удалось запустить сервис приложения"
        fi
    else
        log "приложение $PKG не установлено"
    fi
}
