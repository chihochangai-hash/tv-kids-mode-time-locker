package app.cleanyourtv.home;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Launcher pages the system has actually resumed. Redrawing a page does not add another. */
public final class Pages {
    private static final List<WeakReference<Activity>> open = new ArrayList<>();

    private Pages() {
    }

    public static void resumed(Activity activity) {
        if (activity == null) return;
        paused(activity);
        open.add(new WeakReference<>(activity));
    }

    public static void paused(Activity activity) {
        open.removeIf(ref -> {
            Activity page = ref.get();
            return page == null || page == activity;
        });
    }

    /** True only while the system still has one of our pages resumed. */
    public static boolean showing() {
        for (WeakReference<Activity> ref : open) {
            if (ref.get() != null) return true;
        }
        return false;
    }

    /** True when that page also has the window, not only a resume callback. */
    public static boolean focused() {
        for (WeakReference<Activity> ref : open) {
            Activity page = ref.get();
            if (page != null && page.hasWindowFocus()) return true;
        }
        return false;
    }
}
