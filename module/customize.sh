#!/system/bin/sh
# Вызывается установщиком модуля. Распаковщик не всегда сохраняет бит
# исполнения, а service.sh стартует до того, как это можно заметить.
set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in "$MODPATH"/*.sh; do
    [ -f "$f" ] && set_perm "$f" 0 0 0755
done
ui_print "- Скрипты модуля готовы"
ui_print "- Приложение VR Monitor будет установлено при загрузке"
