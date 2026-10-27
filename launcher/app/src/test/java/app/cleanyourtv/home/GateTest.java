package app.cleanyourtv.home;

import org.junit.Test;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GateTest {
    @Test
    public void clockFormats() {
        assertEquals("30:00", Gate.clock(1800));
        assertEquals("01:05", Gate.clock(65));
        assertEquals("1:00:00", Gate.clock(3600));
        assertEquals("剩餘 30:00", Gate.remainLabel(1800));
        assertEquals("暫停 01:30", Gate.timeLabel(90, true));
        assertFalse(Gate.counting("", "app.cleanyourtv.home"));
        assertFalse(Gate.counting("app.cleanyourtv.home", "app.cleanyourtv.home"));
        assertTrue(Gate.counting("com.netflix.ninja", "app.cleanyourtv.home"));
        assertTrue(Gate.frozen(true));
        assertFalse(Gate.frozen(false));
        assertTrue(Gate.onLauncher(true, true, true));
        assertTrue(Gate.onLauncher(true, false, false));
        assertFalse(Gate.onLauncher(false, false, false));
        assertFalse(Gate.onLauncher(false, true, true));
        assertFalse(Gate.onLauncher(true, false, true));
        assertTrue(Gate.countingNow(false, false, true));
        assertTrue(Gate.countingNow(true, false, true));
        assertFalse(Gate.countingNow(true, true, true));
        assertFalse(Gate.foreignApp("app.cleanyourtv.home", "app.cleanyourtv.home"));
        assertFalse(Gate.foreignApp("com.android.systemui", "app.cleanyourtv.home"));
        assertTrue(Gate.foreignApp("io.gh.reisxd.tizentube.cobalt", "app.cleanyourtv.home"));
        assertTrue(Gate.foreignApp("tv.input", "app.cleanyourtv.home"));
        assertFalse(Gate.slept(1_000L, 1_000L));
        assertFalse(Gate.slept(30_000L, 29_000L));
        assertTrue(Gate.slept(5 * 60_000L, 1_000L));
        assertFalse(Gate.kidAllowed(Calendar.MONDAY, 7));
        assertTrue(Gate.kidAllowed(Calendar.MONDAY, 8));
        assertTrue(Gate.kidAllowed(Calendar.FRIDAY, 20));
        assertFalse(Gate.kidAllowed(Calendar.FRIDAY, 21));
        assertTrue(Gate.kidAllowed(Calendar.SATURDAY, 21));
        assertFalse(Gate.kidAllowed(Calendar.SUNDAY, 22));
        assertEquals("平日 8:00–21:00", Gate.kidWindow(Calendar.WEDNESDAY));
        assertEquals("假日 8:00–22:00", Gate.kidWindow(Calendar.SUNDAY));
        long now = 1_000_000L;
        long frozen = Gate.frozenMs(now, now + 90_000L);
        assertEquals(90_000L, frozen);
        long later = now + 5 * 60_000L;
        assertEquals(later + 90_000L, Gate.resumeAt(later, frozen));
    }

    @Test
    public void bounceRules() {
        Set<String> allowed = new HashSet<>();
        allowed.add("com.google.android.youtube.tv");
        allowed.add("com.foo.hdmi");
        Set<String> pass = new HashSet<>();
        pass.add("com.android.systemui");
        String self = "app.cleanyourtv.home";

        assertTrue(Gate.bounce(Gate.MODE_KID, "com.google.android.youtube.tv", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_KID, "com.google.android.youtube.tvkids", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_KID, "com.google.android.youtube", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_KID, "com.amazon.firetv.youtube", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, "io.gh.reisxd.tizentube.cobalt", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, "org.smarttube.stable", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, "org.smarttube.beta", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, "com.foo.hdmi", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_LOCKED, "io.gh.reisxd.tizentube.cobalt", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_LOCKED, "org.smarttube.stable", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_OPEN, "com.google.android.youtube.tv", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_OPEN, "com.google.android.apps.tv.launcherx", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_KID, "com.google.android.apps.tv.launcherx", self, allowed, pass));
        assertTrue(Gate.bounce(Gate.MODE_OPEN, "com.google.android.tungsten.setupwraith", self, allowed, pass));
        assertFalse(Gate.stockHome("com.netflix.ninja"));
        assertFalse(Gate.bounce(Gate.MODE_KID, self, self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_LOCKED, "com.android.systemui", self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, null, self, allowed, pass));
        assertFalse(Gate.bounce(Gate.MODE_KID, "", self, allowed, pass));
        assertTrue(Gate.player("app.smarttube.fdroid"));
        assertTrue(Gate.player("com.teamsmart.videomanager.tv"));
        assertFalse(Gate.player("com.android.chrome"));
    }

    @Test
    public void cloudCommands() {
        assertEquals("bad", Gate.cloud("add", 5, false, "ok"));
        assertEquals("ok", Gate.cloud("add", 5, true, "bad"));
        assertEquals("ok", Gate.cloud("add", 5, true, "locked"));
        assertEquals("ok", Gate.cloud("add", 5, true, "ok"));
        assertEquals("bad", Gate.cloud("add", 7, true, "ok"));
        assertEquals("ok", Gate.cloud("set", 30, true, "ok"));
        assertEquals("ok", Gate.cloud("set", 60, true, "ok"));
        assertEquals("bad", Gate.cloud("set", 15, true, "ok"));
        assertEquals("ok", Gate.cloud("cancel", 0, true, "ok"));
        assertEquals("bad", Gate.cloud("cancel", 0, false, null));
        assertEquals("ok", Gate.cloud("add", 5, false, null, true));
        assertEquals("ok", Gate.cloud("set", 30, false, null, true));
        assertEquals("bad", Gate.cloud("add", 7, false, null, true));
        assertEquals("ok", Gate.cloud("add", 5, true, "bad", true));
        assertEquals("ok", Gate.cloud("add", -1, true, "ok"));
        assertEquals("ok", Gate.cloud("add", -5, true, "ok"));
        assertEquals("ok", Gate.cloud("add", -10, true, "ok"));
        assertEquals("ok", Gate.cloud("add", -30, true, "ok", true));
        assertEquals("bad", Gate.cloud("add", -7, true, "ok"));
        assertEquals("bad", Gate.cloud("add", -4, false, null, true));
        assertEquals("ok", Gate.cloud("zero", 0, true, "ok"));
        assertEquals("ok", Gate.cloud("zero", 0, true, "bad"));
        assertEquals("ok", Gate.cloud("zero", 0, false, "locked", true));
        assertEquals("ok", Gate.cloud("zero", 0, false, "ok", true));
        assertEquals("bad", Gate.cloud("zero", 0, false, "ok"));
    }

    @Test
    public void usedRoundDoesNotRefill() {
        long half = 30 * 60_000L;
        assertEquals(half, Gate.roundSpan(false, 0, 30));
        assertEquals(0L, Gate.roundSpan(true, 0, 30));
        assertEquals(0L, Gate.roundSpan(true, 0, 60));
        assertEquals(20 * 60_000L, Gate.roundSpan(true, 20 * 60_000L, 30));
        assertEquals(90 * 60_000L, Gate.roundSpan(true, 90 * 60_000L, 30));
    }

    @Test
    public void addStacksPastHalfHour() {
        long half = 30 * 60_000L;
        long hour = Gate.shift(half, "add", 30);
        assertEquals(60 * 60_000L, hour);
        assertEquals(90 * 60_000L, Gate.shift(hour, "add", 30));
        assertEquals(60 * 60_000L, Gate.shift(90 * 60_000L, "add", -30));
        assertEquals(25 * 60_000L, Gate.shift(half, "add", -5));
        assertEquals(0L, Gate.shift(10 * 60_000L, "add", -30));
        assertEquals(60 * 60_000L, Gate.shift(half, "set", 60));
    }

    @Test
    public void pickerAddStepsAreNotCloudAdds() {
        assertTrue(Gate.pickerAdd(10));
        assertTrue(Gate.pickerAdd(20));
        assertTrue(Gate.pickerAdd(30));
        assertTrue(Gate.pickerAdd(60));
        assertFalse(Gate.pickerAdd(1));
        assertFalse(Gate.pickerAdd(5));
        assertFalse(Gate.pickerAdd(40));
        assertFalse(Gate.pickerAdd(-10));
        assertFalse(Gate.addMinutes(20));
        assertFalse(Gate.addMinutes(60));
        assertEquals("＋10 分鐘", Gate.pickerAddLabel(10));
        assertEquals("＋20 分鐘", Gate.pickerAddLabel(20));
        assertEquals("＋30 分鐘", Gate.pickerAddLabel(30));
        assertEquals("＋1 小時", Gate.pickerAddLabel(60));
        assertEquals("", Gate.pickerAddLabel(15));
        assertEquals(20 * 60_000L, Gate.shift(0, "add", 20));
        assertEquals(80 * 60_000L, Gate.shift(20 * 60_000L, "add", 60));
        assertEquals(20 * 60_000L, Gate.roundSpan(true, 20 * 60_000L, 30));
    }

    @Test
    public void unlimitedLastsUntilNextMidnight() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.OCTOBER, 27, 21, 30, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long evening = calendar.getTimeInMillis();
        assertEquals(20261027, Gate.dayKey(evening));
        long midnight = Gate.nextMidnight(evening);
        assertTrue(midnight > evening);
        assertEquals(20261028, Gate.dayKey(midnight));
        assertEquals(20261027, Gate.dayKey(midnight - 1));
        assertEquals(Gate.PICKER_FREE, Gate.pickerChoice(Gate.pickerSteps() - 1));
        assertEquals("無限制", Gate.pickerChoiceLabel(Gate.pickerSteps() - 1));
        assertEquals("＋1 小時", Gate.pickerChoiceLabel(3));
        assertFalse(Gate.pickerAdd(Gate.PICKER_FREE));
        assertEquals("今天不限", Gate.watchLabel(true, 30, false));
        assertEquals("剩餘 00:30", Gate.watchLabel(false, 30, false));
        assertTrue(Gate.stillSpent(20261027, 0, true, evening));
        assertFalse(Gate.stillSpent(20261027, 0, true, midnight));
        assertFalse(Gate.stillSpent(0, 0, true, evening));
        assertFalse(Gate.stillSpent(20261027, 5 * 60_000L, true, evening));
        assertEquals(30 * 60_000L, Gate.roundSpan(false, 0, 30));
        assertEquals(0L, Gate.roundSpan(true, 0, 30));
        long leftover = 12 * 60_000L;
        assertEquals(leftover, Gate.daySpan(20261027, leftover, true, 30, evening));
        assertEquals(30 * 60_000L, Gate.daySpan(20261027, leftover, true, 30, midnight));
        assertEquals(0L, Gate.daySpan(20261027, 0, true, 30, evening));
        assertEquals(60 * 60_000L, Gate.daySpan(20261026, leftover, true, 60, evening));
    }

    @Test
    public void unlimitedStopsWhenTheWindowCloses() {
        Calendar day = Calendar.getInstance();
        day.set(2026, Calendar.OCTOBER, 26, 20, 30, 0);
        day.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 7; i++) {
            long at = day.getTimeInMillis();
            boolean weekend = day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
            long end = Gate.windowEnd(at);
            Calendar got = Calendar.getInstance();
            got.setTimeInMillis(end);
            assertEquals(weekend ? 22 : 21, got.get(Calendar.HOUR_OF_DAY));
            assertEquals(0, got.get(Calendar.MINUTE));
            assertEquals(day.get(Calendar.DAY_OF_YEAR), got.get(Calendar.DAY_OF_YEAR));
            assertTrue(end > at);
            assertFalse(Gate.kidAllowedNow(end));
            assertTrue(Gate.kidAllowedNow(end - 60_000L));
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    @Test
    public void countdownCornerMovesEachMinute() {
        assertEquals(0, Gate.nextCorner(0, 59_999L));
        assertEquals(1, Gate.nextCorner(0, 60_000L));
        assertEquals(2, Gate.nextCorner(0, 120_000L));
        assertEquals(0, Gate.nextCorner(3, 60_000L));
        assertEquals(1, Gate.nextCorner(3, 120_000L));
    }
}
