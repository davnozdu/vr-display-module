#!/system/bin/sh
# Ставит или обновляет VR Monitor из состава модуля.

MODDIR=${MODDIR:-/data/adb/modules/vr_display_mode}
. "$MODDIR/common.sh"

APK="$MODDIR/vr-monitor.apk"
[ -f "$APK" ] || { log "APK в модуле нет — пропускаю установку"; exit 0; }

WANT=$(cat "$MODDIR/apk-versioncode" 2>/dev/null || echo 0)
HAVE=$(dumpsys package "$PKG" 2>/dev/null | grep -m1 versionCode= | sed 's/.*versionCode=\([0-9]*\).*/\1/')
[ -z "$HAVE" ] && HAVE=0

if [ "$HAVE" -ge "$WANT" ] 2>/dev/null; then
    log "VR Monitor уже версии $HAVE (в модуле $WANT) — установка не нужна"
    exit 0
fi

log "устанавливаю VR Monitor: было $HAVE, ставлю $WANT"
# -g выдаёт runtime-разрешения сразу, чтобы уведомление с аварийной кнопкой
# работало без похода в диалог.
out=$(pm install -r -g "$APK" 2>&1)
case "$out" in
    *Success*)
        log "установлено"
        ;;
    *)
        log "установка не удалась: $out"
        # Чаще всего это несовпадение подписи с уже стоящей сборкой.
        case "$out" in
            *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*signatures do not match*)
                log "подпись отличается от установленной — удалите старое приложение вручную"
                ;;
        esac
        ;;
esac
