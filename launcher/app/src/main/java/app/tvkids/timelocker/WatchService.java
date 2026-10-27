package app.tvkids.timelocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Wall-clock countdown. HDMI and other apps do not pause it.
 * Started from the activities and boot receiver, not from Application.onCreate.
 */
public class WatchService extends Service {
    private static final String CHANNEL = "watch";
    private Handler handler;
    private boolean ending;
    private boolean overlayFailed;
    private TextView overlay;
    private WindowManager windows;
    private BroadcastReceiver power;
    private long lastWall;
    private long lastUptime;
    private boolean openedPower;
    private boolean needPicker;
    private int corner;
    private long cornerMovedAt;

    public static void ensure(Context context) {
        try {
            Context app = context.getApplicationContext();
            app.startForegroundService(new Intent(app, WatchService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        promote("收看時間");
        handler = new Handler(Looper.getMainLooper());
        handler.post(this::step);
        CloudLoop.start(this);
        watchPower();
    }

    private void watchPower() {
        power = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SHUTDOWN.equals(action)) {
                    openedPower = false;
                    Store.get(WatchService.this).powerOff();
                    hideOverlay();
                    return;
                }
                if (!Intent.ACTION_SCREEN_ON.equals(action)) return;
                if (!Store.get(WatchService.this).powerOffPending()) return;
                showPicker();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(power, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(power, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (power != null) {
            try {
                unregisterReceiver(power);
            } catch (Exception ignored) {
            }
            power = null;
        }
        if (handler != null) handler.removeCallbacksAndMessages(null);
        hideOverlay();
        CloudLoop.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void step() {
        try {
            Store store = Store.get(this);
            long now = System.currentTimeMillis();
            long uptime = SystemClock.uptimeMillis();
            if (lastWall > 0 && Gate.slept(now - lastWall, uptime - lastUptime)) {
                openedPower = false;
                store.powerOffAt(lastWall);
                hideOverlay();
            }
            lastWall = now;
            lastUptime = uptime;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean screenOn = pm == null || pm.isInteractive();
            if (store.rollover(now)) hideOverlay();
            if (screenOn) store.noteAwake(now);
            if (screenOn && store.powerOffPending()) showPicker();
            if (screenOn && store.consumeOpenPicker()) openPicker();
            if (store.lockIfClosed(now)) {
                if (!ending) {
                    ending = true;
                    Intent picker = new Intent(this, PickerActivity.class);
                    picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(picker);
                    hideOverlay();
                }
            }
            // Every second: a child who is not on a launcher page keeps counting, including after add-time.
            if (Front.onLauncher()) store.hold(now);
            else store.release(now);
            Store.Session session = store.session(now);
            if (session.active && !session.expired) ending = false;
            if (!store.lockIfClosed(now) && session.active && !session.adult && session.expired) {
                store.end(session.free ? "今天的不限時結束了" : "時間到了");
                needPicker = true;
                hideOverlay();
                session = store.session(now);
            }
            if (needPicker) {
                if (Front.onLauncher()) needPicker = false;
                else {
                    Intent picker = new Intent(this, PickerActivity.class);
                    picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    try {
                        startActivity(picker);
                    } catch (Exception ignored) {
                    }
                }
            }
            String label = session.active && !session.adult && !session.expired
                    ? Gate.watchLabel(session.free, session.left, session.paused) : "收看時間";
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(1, notification(label));
            updateOverlay(session);
        } catch (Exception ignored) {
        }
        if (handler != null) handler.postDelayed(this::step, 1000);
    }

    private void showPicker() {
        if (openedPower) return;
        openedPower = true;
        if (!openPicker()) openedPower = false;
    }

    /** Time hit zero from the phone. The set stays on; only the account picker comes forward. */
    private boolean openPicker() {
        Intent picker = new Intent(this, PickerActivity.class);
        picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(picker);
        } catch (Exception ignored) {
            return false;
        }
        hideOverlay();
        return true;
    }

    private void updateOverlay(Store.Session session) {
        boolean show = session.active && !session.adult && !session.expired && !Front.onLauncher()
                && Settings.canDrawOverlays(this);
        if (!show || overlayFailed) {
            hideOverlay();
            return;
        }
        long uptime = SystemClock.uptimeMillis();
        if (cornerMovedAt == 0) cornerMovedAt = uptime;
        int next = Gate.nextCorner(corner, uptime - cornerMovedAt);
        boolean relocate = next != corner;
        if (relocate) {
            long steps = (uptime - cornerMovedAt) / 60_000L;
            cornerMovedAt += steps * 60_000L;
            corner = next;
        }
        if (overlay == null) {
            TextView view = new TextView(this);
            view.setTextSize(14);
            view.setTypeface(Typeface.MONOSPACE);
            view.setTextColor(Color.WHITE);
            view.setBackgroundColor(0x66000000);
            int pad = Ui.dp(this, 8);
            view.setPadding(pad, pad / 2, pad, pad / 2);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            applyCorner(params);
            try {
                windows = getSystemService(WindowManager.class);
                windows.addView(view, params);
                overlay = view;
            } catch (Exception e) {
                overlayFailed = true;
                overlay = null;
                return;
            }
        } else if (relocate && windows != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlay.getLayoutParams();
            applyCorner(params);
            try {
                windows.updateViewLayout(overlay, params);
            } catch (Exception e) {
                hideOverlay();
                return;
            }
        }
        overlay.setText(Gate.watchLabel(session.free, session.left, session.paused));
    }

    /** Right-top, right-bottom, left-bottom, left-top. A fixed corner burns the panel. */
    private void applyCorner(WindowManager.LayoutParams params) {
        params.gravity = switch (corner) {
            case 1 -> Gravity.BOTTOM | Gravity.END;
            case 2 -> Gravity.BOTTOM | Gravity.START;
            case 3 -> Gravity.TOP | Gravity.START;
            default -> Gravity.TOP | Gravity.END;
        };
        params.x = Ui.dp(this, 28);
        params.y = Ui.dp(this, 20);
    }

    private void hideOverlay() {
        if (overlay != null && windows != null) {
            try {
                windows.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
    }

    private void promote(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "收看計時", NotificationManager.IMPORTANCE_MIN);
            manager.createNotificationChannel(channel);
        }
        Notification notification = notification(text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("收看時間")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
