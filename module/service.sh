#!/system/bin/sh
# late_start service: система уже загружена, PackageManager доступен.

MODDIR=${0%/*}
export MODDIR
. "$MODDIR/common.sh"

# Ждём полной загрузки, иначе pm и dumpsys ещё не отвечают.
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 5

log "=== модуль запущен (версия $(grep '^version=' "$MODDIR/module.prop" | cut -d= -f2)) ==="

sh "$MODDIR/install-app.sh"
start_app_service

# Desktop mode на внешних дисплеях. Настройка читается системой в момент
# подключения дисплея, поэтому выставляем её до того, как воткнут очки.
settings put global force_desktop_mode_on_external_displays 1
log "force_desktop_mode_on_external_displays = $(settings get global force_desktop_mode_on_external_displays)"

nohup sh "$MODDIR/vrdisplayd.sh" >/dev/null 2>&1 &
sleep 2
if pgrep -f vrdisplayd.sh >/dev/null 2>&1; then
    log "демон запущен"
else
    log "ОШИБКА: демон не поднялся"
fi
