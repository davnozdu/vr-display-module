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
            if ("pointer".equals(cmd)) {
                System.exit(bindPointers() ? 0 : 1);
            } else if ("methods".equals(cmd)) {
                methods(args.length > 1 ? args[1] : "input",
                        args.length > 2 ? args[2] : "android.hardware.input.IInputManager");
            } else if ("list".equals(cmd)) {
                list();
            } else if ("enable".equals(cmd)) {
                System.exit(enableExternal() ? 0 : 1);
            } else {
                System.err.println("usage: DisplayCtl [enable|pointer|list|methods <service> <iface>]");
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

    // Источники указывающих устройств. Тачскрин сюда намеренно не входит:
    // он тоже относится к классу POINTER, но привязывать экран телефона
    // к внешнему дисплею нельзя.
    private static final int SOURCE_MOUSE          = 0x00002002;
    private static final int SOURCE_MOUSE_RELATIVE = 0x00008002;
    private static final int SOURCE_TOUCHPAD       = 0x00100008;
    private static final int SOURCE_TRACKBALL      = 0x00010004;

    private static boolean isPointer(int sources) {
        return (sources & SOURCE_MOUSE) == SOURCE_MOUSE
                || (sources & SOURCE_MOUSE_RELATIVE) == SOURCE_MOUSE_RELATIVE
                || (sources & SOURCE_TOUCHPAD) == SOURCE_TOUCHPAD
                || (sources & SOURCE_TRACKBALL) == SOURCE_TRACKBALL;
    }

    /** Встроенные устройства не трогаем — уводить их на внешний экран незачем. */
    private static boolean isExternal(Class<?> inputDevice, Object dev) {
        try {
            return (Boolean) inputDevice.getMethod("isExternal").invoke(dev);
        } catch (Throwable t) {
            // Метод скрытый; если его нет — считаем внешним и полагаемся на фильтр источников.
            return true;
        }
    }

    private static Object inputManager() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Class<?> iBinder = Class.forName("android.os.IBinder");
        Object binder = serviceManager.getMethod("getService", String.class).invoke(null, "input");
        if (binder == null) throw new IllegalStateException("input service missing");
        Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
        return stub.getMethod("asInterface", iBinder).invoke(null, binder);
    }

    /** uniqueId первого внешнего дисплея, или null. */
    private static String externalDisplayUniqueId() throws Exception {
        Object dm = displayManager();
        for (int id : displayIds(dm)) {
            if (id == 0) continue;
            Object info = displayInfo(dm, id);
            if (typeOf(info) != TYPE_EXTERNAL) continue;
            try {
                Object u = info.getClass().getField("uniqueId").get(info);
                if (u != null) return u.toString();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Привязывает указывающие устройства к внешнему дисплею.
     *
     * Без привязки курсор от пульта живёт на встроенном экране (dumpsys input
     * показывает Pointer Display ID: 0) и попадает в очки только после
     * передёргивания пульта. Привязка по дескриптору переживает
     * переподключение устройства, в отличие от привязки по порту.
     */
    private static boolean bindPointers() throws Exception {
        String uniqueId = externalDisplayUniqueId();
        if (uniqueId == null) {
            System.err.println("внешнего дисплея нет — привязывать не к чему");
            return false;
        }

        Object im = inputManager();
        Method bind = null;
        for (Method m : im.getClass().getMethods()) {
            if ("addUniqueIdAssociationByDescriptor".equals(m.getName())
                    && m.getParameterTypes().length == 2) {
                bind = m;
                break;
            }
        }
        if (bind == null) throw new NoSuchMethodException("addUniqueIdAssociationByDescriptor");

        Class<?> inputDevice = Class.forName("android.view.InputDevice");
        int[] ids = (int[]) inputDevice.getMethod("getDeviceIds").invoke(null);
        boolean any = false;
        for (int id : ids) {
            Object dev = inputDevice.getMethod("getDevice", int.class).invoke(null, id);
            if (dev == null) continue;
            int sources = (Integer) inputDevice.getMethod("getSources").invoke(dev);
            if (!isPointer(sources)) continue;
            if (!isExternal(inputDevice, dev)) continue;
            String descriptor = (String) inputDevice.getMethod("getDescriptor").invoke(dev);
            String name = String.valueOf(inputDevice.getMethod("getName").invoke(dev));
            try {
                bind.invoke(im, descriptor, uniqueId);
                System.out.println("pointer bound: " + name + " -> " + uniqueId);
                any = true;
            } catch (Throwable t) {
                System.err.println("не удалось привязать " + name + ": " + t.getCause());
            }
        }
        if (!any) System.err.println("указывающих устройств не найдено");
        return any;
    }

    /**
     * Разведка: печатает методы binder-интерфейса системного сервиса.
     * Нужна, чтобы искать подходящий вызов, не угадывая имена вслепую.
     */
    private static void methods(String service, String iface) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Class<?> iBinder = Class.forName("android.os.IBinder");
        Object binder = serviceManager.getMethod("getService", String.class).invoke(null, service);
        if (binder == null) {
            System.out.println("нет сервиса " + service);
            return;
        }
        Object proxy = Class.forName(iface + "$Stub")
                .getMethod("asInterface", iBinder).invoke(null, binder);
        java.util.TreeSet<String> names = new java.util.TreeSet<String>();
        for (Method m : proxy.getClass().getMethods()) {
            StringBuilder sb = new StringBuilder(m.getName()).append("(");
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(ps[i].getSimpleName());
            }
            names.add(sb.append(")").toString());
        }
        for (String n : names) System.out.println(n);
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
