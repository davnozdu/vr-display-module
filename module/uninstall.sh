#!/system/bin/sh
# Вызывается менеджером при удалении модуля.
settings put global force_desktop_mode_on_external_displays 0
pkill -f vrdisplayd.sh
