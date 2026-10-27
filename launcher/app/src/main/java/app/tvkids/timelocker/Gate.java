package app.tvkids.timelocker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/** Rules that do not touch Android, so the unit tests can run them on a desktop JVM. */
public final class Gate {
    public static final int MODE_LOCKED = 0;
    public static final int MODE_KID = 1;
    public static final int MODE_OPEN = 2;
    public static final int DEFAULT_MINUTES = 30;
    /** Public hostname of your own cloud host. Leave empty until you set one. */
    public static final String CLOUD_HOST = "";
    public static final int CLOUD_PORT = 8443;

    private Gate() {
    }

    public static String clock(int sec) {
        if (sec < 0) sec = 0;
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        if (h > 0) return h + ":" + pad(m) + ":" + pad(s);
        return pad(m) + ":" + pad(s);
    }

    public static String remainLabel(int sec) {
        return timeLabel(sec, false);
    }

    public static String timeLabel(int sec, boolean paused) {
        return (paused ? "暫停 " : "剩餘 ") + clock(sec);
    }

    /** Milliseconds still left when the countdown is frozen. */
    public static long frozenMs(long nowMs, long deadlineMs) {
        long left = deadlineMs - nowMs;
        return left < 0 ? 0 : left;
    }

    public static long resumeAt(long nowMs, long frozenMs) {
        return nowMs + Math.max(0, frozenMs);
    }

    /** Whole seconds, rounded up, so the last partial second still shows 00:01. */
    public static int remainingSec(long nowMs, long deadlineMs) {
        long left = deadlineMs - nowMs;
        if (left <= 0) return 0;
        return (int) ((left + 999) / 1000);
    }

    public static long deadline(long nowMs, int minutes) {
        return nowMs + minutes * 60_000L;
    }

    /**
     * Milliseconds for the next login. A saved remainder is used as-is.
     * A zero bank blocks another round only when that zero was recorded today.
     */
    public static long roundSpan(boolean recorded, long bankMs, int profileMinutes) {
        if (bankMs > 0) return bankMs;
        if (!recorded) return Math.max(0, profileMinutes) * 60_000L;
        return 0;
    }

    /** True when roundDay is the local day of now. A missing day is not today. */
    public static boolean sameDay(int roundDay, long now) {
        return roundDay != 0 && roundDay == dayKey(now);
    }

    /** Used up for the rest of today. The next local day is not used up. */
    public static boolean stillSpent(int roundDay, long bankMs, boolean recorded, long now) {
        if (bankMs > 0 || !recorded) return false;
        return sameDay(roundDay, now);
    }

    /**
     * Time for the next login. Yesterday's remainder is dropped.
     * A zero recorded today stays empty. Any other day starts at the profile length.
     */
    public static long daySpan(int roundDay, long bankMs, boolean marked, int profileMinutes, long now) {
        boolean today = sameDay(roundDay, now);
        long usable = today ? Math.max(0L, bankMs) : 0L;
        boolean recorded = today && marked && usable <= 0;
        return roundSpan(recorded, usable, profileMinutes);
    }

    /**
     * Remote command while a child session is the only running state.
     * pinState is ok, password, or locked. When nothing is running the pin is not judged.
     */
    public static String cloud(String op, int minutes, boolean running, String pinState) {
        return cloud(op, minutes, running, pinState, false);
    }

    /** +1/+5/+10/+30 and the matching subtractions. The total is not limited to 30. */
    public static boolean addMinutes(int minutes) {
        return switch (minutes) {
            case 1, 5, 10, 30, -1, -5, -10, -30 -> true;
            default -> false;
        };
    }

    /** Steps the account page can add. These stay on the TV and are not cloud add amounts. */
    public static final int[] PICKER_ADD = {10, 20, 30, 60};

    /** Account-page choice that means no minute cap until the next local midnight. */
    public static final int PICKER_FREE = -1;

    public static int pickerSteps() {
        return PICKER_ADD.length + 1;
    }

    public static int pickerChoice(int index) {
        if (index < 0 || index >= PICKER_ADD.length) return PICKER_FREE;
        return PICKER_ADD[index];
    }

    public static boolean pickerAdd(int minutes) {
        for (int step : PICKER_ADD) if (step == minutes) return true;
        return false;
    }

    public static String pickerAddLabel(int minutes) {
        if (minutes == 60) return "＋1 小時";
        if (pickerAdd(minutes)) return "＋" + minutes + " 分鐘";
        return "";
    }

    public static String pickerChoiceLabel(int index) {
        int choice = pickerChoice(index);
        if (choice == PICKER_FREE) return "無限制";
        return pickerAddLabel(choice);
    }

    /** Local calendar day as yyyymmdd. The next day does not match. */
    public static int dayKey(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        return calendar.get(Calendar.YEAR) * 10_000
                + (calendar.get(Calendar.MONTH) + 1) * 100
                + calendar.get(Calendar.DAY_OF_MONTH);
    }

    /** The next local midnight. A same-day grant stops here. */
    public static long nextMidnight(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    public static String watchLabel(boolean free, int sec, boolean paused) {
        if (free) return "今天不限";
        return timeLabel(sec, paused);
    }

    /**
     * Stack or replace a remainder. Add keeps going past 30 minutes.
     * A subtraction stops at zero.
     */
    public static long shift(long remainMs, String op, int minutes) {
        long span = minutes * 60_000L;
        long next = "set".equals(op) ? span : Math.max(0L, remainMs) + span;
        return next < 0 ? 0 : next;
    }

    /** Corner 0..3. One step per full minute so a countdown does not sit in one place. */
    public static int nextCorner(int corner, long sinceMs) {
        int at = corner % 4;
        if (at < 0) at += 4;
        if (sinceMs < 60_000L) return at;
        return (int) ((at + sinceMs / 60_000L) % 4);
    }

    /**
     * A targeted account can receive minutes while nobody is watching.
     * A running child session still requires the parent PIN.
     * Zero always requires the parent PIN. With nobody watching and no account, there is nothing to zero.
     */
    public static String cloud(String op, int minutes, boolean running, String pinState, boolean targeted) {
        if ("zero".equals(op)) {
            if (!running && !targeted) return "bad";
            return "ok"; // online host: zero needs no parent PIN
        }
        if (!running && !targeted) return "bad";
        if (running && !"add".equals(op)) { // online host add/subtract needs no parent PIN
            if ("locked".equals(pinState)) return "locked";
            if (!"ok".equals(pinState)) return "password";
        }
        if ("cancel".equals(op)) return running ? "ok" : "bad";
        if ("set".equals(op) && (minutes == 30 || minutes == 60)) return "ok";
        if ("add".equals(op) && addMinutes(minutes)) return "ok";
        return "bad";
    }

    public static boolean youtube(String pkg) {
        return "com.google.android.youtube.tv".equals(pkg)
                || "com.google.android.youtube.tvkids".equals(pkg)
                || "com.google.android.youtube".equals(pkg)
                || "com.amazon.firetv.youtube".equals(pkg);
    }

    public static boolean tizen(String pkg) {
        return pkg != null && pkg.toLowerCase(Locale.US).contains("tizentube");
    }

    /** Ad-free players we will open. Official YouTube is never one of these. */
    public static boolean player(String pkg) {
        if (pkg == null) return false;
        if ("com.teamsmart.videomanager.tv".equals(pkg)) return true;
        String lower = pkg.toLowerCase(Locale.US);
        return lower.contains("tizentube") || lower.contains("smarttube");
    }

    /** Lower sorts first: TizenTube Cobalt, then SmartTube stable, beta, F-Droid. */
    public static int playerRank(String pkg) {
        if ("io.gh.reisxd.tizentube.cobalt".equals(pkg)) return 0;
        if (tizen(pkg)) return 1;
        if ("org.smarttube.stable".equals(pkg)) return 2;
        if ("org.smarttube.beta".equals(pkg)) return 3;
        if ("app.smarttube.fdroid".equals(pkg)) return 4;
        if ("com.teamsmart.videomanager.tv".equals(pkg)) return 5;
        if (player(pkg)) return 6;
        return 100;
    }

    /**
     * True when the foreground package must be sent back.
     * Official YouTube is refused for every child session even if a parent checked it.
     * TizenTube and SmartTube stay available during a child session.
     */
    /** Weekdays 08:00–21:00. Saturday and Sunday 08:00–22:00. The end hour itself is already closed. */
    public static boolean kidAllowed(int dayOfWeek, int hour) {
        boolean weekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
        int end = weekend ? 22 : 21;
        return hour >= 8 && hour < end;
    }

    public static String kidWindow(int dayOfWeek) {
        boolean weekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
        return weekend ? "假日 8:00–22:00" : "平日 8:00–21:00";
    }

    public static boolean kidAllowedNow(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        return kidAllowed(calendar.get(Calendar.DAY_OF_WEEK), calendar.get(Calendar.HOUR_OF_DAY));
    }

    /** Today's lock time: 21:00 on weekdays, 22:00 on Saturday and Sunday. An unlimited round stops here. */
    public static long windowEnd(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        boolean weekend = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
        calendar.set(Calendar.HOUR_OF_DAY, weekend ? 22 : 21);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static String kidWindowNow(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        return kidWindow(calendar.get(Calendar.DAY_OF_WEEK));
    }

    /** Minutes run only while another app is in front. The launcher and an empty front do not count. */
    public static boolean counting(String front, String self) {
        return front != null && !front.isEmpty() && self != null && !front.equals(self);
    }

    /**
     * Freeze only while one of our pages is resumed.
     * The countdown overlay is this same package, so a window event from it must not freeze the clock.
     */
    public static boolean frozen(boolean ourPageResumed) {
        return ourPageResumed;
    }

    /** System chrome and the volume panel are not an app the child opened. */
    public static boolean chrome(String pkg) {
        return "android".equals(pkg)
                || "com.android.systemui".equals(pkg)
                || "com.android.permissioncontroller".equals(pkg)
                || "com.google.android.permissioncontroller".equals(pkg);
    }

    /** Another app, or an HDMI source, is actually in front. */
    public static boolean foreignApp(String front, String self) {
        if (front == null || front.isEmpty() || self == null || front.equals(self)) return false;
        return !chrome(front);
    }

    /**
     * Still on a launcher page. Checked every second.
     * A resumed page with no focus does not count once an app or HDMI source is in front,
     * so a stuck page cannot keep the countdown frozen.
     */
    public static boolean onLauncher(boolean resumed, boolean focused, boolean foreignNewer) {
        if (!resumed) return false;
        return focused || !foreignNewer;
    }

    /** The clock runs whenever the child is not on a launcher page. */
    public static boolean countingNow(boolean resumed, boolean focused, boolean foreignNewer) {
        return !onLauncher(resumed, focused, foreignNewer);
    }

    /** Wall time jumped while the CPU clock did not: the television was powered off or suspended. */
    public static boolean slept(long wallGapMs, long uptimeGapMs) {
        long slept = wallGapMs - uptimeGapMs;
        return wallGapMs > 20_000L && slept > 15_000L;
    }

    /** Stock Google TV home. It must not stay in front, including adult mode. */
    public static boolean stockHome(String pkg) {
        return "com.google.android.apps.tv.launcherx".equals(pkg)
                || "com.google.android.tungsten.setupwraith".equals(pkg);
    }

    public static boolean bounce(int mode, String pkg, String self, Set<String> allowed, Set<String> pass) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(self)) return false;
        if (stockHome(pkg)) return true;
        if (mode == MODE_OPEN) return false;
        if (youtube(pkg)) return true;
        if (pass != null && pass.contains(pkg)) return false;
        if (mode == MODE_KID) {
            if (player(pkg)) return false;
            return allowed == null || !allowed.contains(pkg);
        }
        return true;
    }

    public static String clockTarget(long deadlineMs) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(deadlineMs));
    }

    public static String grouped(String fp) {
        if (fp == null || fp.isEmpty()) return "";
        StringBuilder s = new StringBuilder(fp.length() + fp.length() / 4);
        for (int i = 0; i < fp.length(); i++) {
            if (i > 0 && i % 4 == 0) s.append(' ');
            s.append(fp.charAt(i));
        }
        return s.toString();
    }

    private static String pad(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }
}
