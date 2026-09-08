#!/system/bin/sh
# Вызывается менеджером при удалении модуля.
settings put global force_desktop_mode_on_external_displays 0
PIDFILE=/data/adb/vr_display_mode.pid
[ -f "$PIDFILE" ] && kill -9 "$(cat "$PIDFILE")" 2>/dev/null
rm -f "$PIDFILE"
