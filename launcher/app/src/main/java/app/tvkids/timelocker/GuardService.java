package app.tvkids.timelocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/** Sends a disallowed foreground app back. It reads only the package name. */
public class GuardService extends AccessibilityService {
    private long last;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            onWindow(event);
        } catch (Exception ignored) {
        }
    }

    private void onWindow(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence name = event.getPackageName();
        if (name == null) return;
        String pkg = name.toString();
        if (Gate.foreignApp(pkg, getPackageName())) {
            long when = event.getEventTime();
            if (when <= 0) when = SystemClock.uptimeMillis();
            Front.seen(pkg, when);
        }
        long now = System.currentTimeMillis();
        Store store = Store.get(this);
        int mode = store.mode(now);
        Set<String> allowed = new HashSet<>();
        if (mode == Gate.MODE_KID) {
            Store.Session session = store.session(now);
            allowed.addAll(store.apps(session.id));
            allowed.addAll(Devices.inputPackages(this));
            allowed.add("com.google.android.gms");
            allowed.add("com.mitv.livetv");
            allowed.add("com.android.tv");
        }
        if (!Gate.bounce(mode, pkg, getPackageName(), allowed, Devices.pass(this))) return;
        long elapsed = SystemClock.elapsedRealtime();
        if (elapsed - last < 500) return;
        last = elapsed;
        Class<?> dest = Gate.stockHome(pkg)
                ? HomeActivity.class
                : (mode == Gate.MODE_KID ? KidsActivity.class : PickerActivity.class);
        Intent intent = new Intent(this, dest);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
    }
}
