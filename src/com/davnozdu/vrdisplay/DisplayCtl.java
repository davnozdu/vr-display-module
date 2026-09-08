package com.davnozdu.vrdisplay;

import java.lang.reflect.Method;

/**
 * Включает внешний дисплей и переводит его в desktop mode.
 *
 * Запускается через app_process от root. Обращение к system_server идёт
 * только рефлексией: android.hardware.display.IDisplayManager — скрытый
 * интерфейс, которого нет в публичном android.jar, а имена методов стабильнее
 * номеров binder-транзакций, поэтому service call здесь не используется.
 *
 * Право MANAGE_DISPLAYS, которое требует enableConnectedDisplay, приложению
 * выдать нельзя (оно signature-уровня), но проверка прав в system_server
 * пропускает вызовы от uid 0 — отсюда и запуск от root.
 */
public final class DisplayCtl {

    /** Display.TYPE_EXTERNAL */
    private static final int TYPE_EXTERNAL = 2;

    public static void main(String[] args) {
        String cmd = args.length > 0 ? args[0] : "enable";
        try {
            if ("list".equals(cmd)) {
                list();
            } else if ("enable".equals(cmd)) {
                System.exit(enableExternal() ? 0 : 1);
            } else {
                System.err.println("usage: DisplayCtl [enable|list]");
                System.exit(2);
            }
        } catch (Throwable t) {
            System.err.println("DisplayCtl failed: " + t);
            System.exit(1);
        }
    }

    private static Object displayManager() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Class<?> iBinder = Class.forName("android.os.IBinder");
        Object binder = serviceManager.getMethod("getService", String.class)
                .invoke(null, "display");
        if (binder == null) throw new IllegalStateException("display service missing");
        Class<?> stub = Class.forName("android.hardware.display.IDisplayManager$Stub");
        return stub.getMethod("asInterface", iBinder).invoke(null, binder);
    }

    /**
     * getDisplayIds менял сигнатуру между версиями (с флагом includeDisabled
     * и без него), поэтому берём метод по имени и подставляем аргументы
     * под то, что нашлось.
     */
    private static int[] displayIds(Object dm) throws Exception {
        for (Method m : dm.getClass().getMethods()) {
            if (!"getDisplayIds".equals(m.getName())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 0) return (int[]) m.invoke(dm);
            if (p.length == 1 && p[0] == boolean.class) {
                // true — включая ещё не включённые дисплеи, они нам и нужны
                return (int[]) m.invoke(dm, Boolean.TRUE);
            }
        }
        throw new NoSuchMethodException("getDisplayIds");
    }

    private static Object displayInfo(Object dm, int id) {
        try {
            for (Method m : dm.getClass().getMethods()) {
                if (!"getDisplayInfo".equals(m.getName())) continue;
                if (m.getParameterTypes().length == 1) return m.invoke(dm, id);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int typeOf(Object info) {
        if (info == null) return -1;
        try {
            return info.getClass().getField("type").getInt(info);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String nameOf(Object info) {
        if (info == null) return "?";
        try {
            Object n = info.getClass().getField("name").get(info);
            return n == null ? "?" : n.toString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void list() throws Exception {
        Object dm = displayManager();
        for (int id : displayIds(dm)) {
            Object info = displayInfo(dm, id);
            System.out.println("display " + id + " type=" + typeOf(info) + " name=" + nameOf(info));
        }
    }

    /** @return true, если хотя бы один внешний дисплей удалось включить. */
    private static boolean enableExternal() throws Exception {
        Object dm = displayManager();
        Method enable = null;
        for (Method m : dm.getClass().getMethods()) {
            if ("enableConnectedDisplay".equals(m.getName())
                    && m.getParameterTypes().length == 1) {
                enable = m;
                break;
            }
        }
        if (enable == null) throw new NoSuchMethodException("enableConnectedDisplay");

        boolean any = false;
        for (int id : displayIds(dm)) {
            if (id == 0) continue;                       // встроенный экран не трогаем
            if (typeOf(displayInfo(dm, id)) != TYPE_EXTERNAL) continue;
            try {
                enable.invoke(dm, id);
                System.out.println("enabled display " + id);
                any = true;
            } catch (Throwable t) {
                System.err.println("display " + id + ": " + t.getCause());
            }
        }
        if (!any) System.err.println("no external display to enable");
        return any;
    }

    private DisplayCtl() {}
}
