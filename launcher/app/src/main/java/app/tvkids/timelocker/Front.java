package app.tvkids.timelocker;

/**
 * Which page is in front. Resume state comes from the system, not from a screen redraw.
 * An app seen after the last launcher resume means the child has left the menu.
 */
public final class Front {
    public static volatile String pkg = "";
    private static volatile long homeAt;
    private static volatile long frontAt;
    private static volatile String self = "";

    private Front() {
    }

    /** A launcher page really came forward. */
    public static void home(String packageName, long when) {
        if (packageName == null || packageName.isEmpty()) return;
        self = packageName;
        pkg = packageName;
        homeAt = when;
        if (when > frontAt) frontAt = when;
    }

    /** An app or input source came forward. An older window event must not cover home. */
    public static void seen(String packageName, long when) {
        if (packageName == null || packageName.isEmpty() || when < frontAt) return;
        pkg = packageName;
        frontAt = when;
    }

    /**
     * True only while a launcher page still has the screen.
     * Otherwise the countdown runs, and the next check starts it again if it was frozen.
     */
    public static boolean onLauncher() {
        boolean foreignNewer = Gate.foreignApp(pkg, self) && frontAt > homeAt;
        return Gate.onLauncher(Pages.showing(), Pages.focused(), foreignNewer);
    }
}
