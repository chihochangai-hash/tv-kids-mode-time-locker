package app.cleanyourtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Profiles, pins, and the one active watching session. Pins and the session use commit(). */
public final class Store {
    private static Store instance;
    private final SharedPreferences prefs;

    public static final class Person {
        public final String id;
        public final String name;
        public final boolean adult;
        public final boolean hasPin;
        public final int minutes;

        Person(String id, String name, boolean adult, boolean hasPin, int minutes) {
            this.id = id;
            this.name = name;
            this.adult = adult;
            this.hasPin = hasPin;
            this.minutes = minutes;
        }
    }

    public static final class Session {
        public static final Session NONE = new Session(false, false, false, "", "", 0, 0);
        public final boolean active;
        public final boolean adult;
        public final boolean expired;
        public final String id;
        public final String name;
        public final int left;
        public final long deadline;
        public final boolean paused;
        public final boolean free;

        Session(boolean active, boolean adult, boolean expired, String id, String name, int left, long deadline) {
            this(active, adult, expired, id, name, left, deadline, false, false);
        }

        Session(boolean active, boolean adult, boolean expired, String id, String name, int left, long deadline, boolean paused) {
            this(active, adult, expired, id, name, left, deadline, paused, false);
        }

        Session(boolean active, boolean adult, boolean expired, String id, String name, int left, long deadline, boolean paused, boolean free) {
            this.active = active;
            this.adult = adult;
            this.expired = expired;
            this.id = id;
            this.name = name;
            this.left = left;
            this.deadline = deadline;
            this.paused = paused;
            this.free = free;
        }
    }

    public static synchronized Store get(Context context) {
        if (instance == null) instance = new Store(context.getApplicationContext());
        return instance;
    }

    private Store(Context context) {
        prefs = context.getSharedPreferences("state", Context.MODE_PRIVATE);
        ensureSeed();
        renameAdult();
    }

    public synchronized void ensureSeed() {
        if (prefs.getBoolean("seeded", false)) return;
        JSONArray people = new JSONArray();
        people.put(blank("adult", "家長", true));
        prefs.edit().putString("profiles", people.toString()).putBoolean("seeded", true).commit();
    }

    public synchronized boolean hasAdultPin() {
        JSONObject adult = find(profiles(), "adult");
        return adult != null && !adult.optString("hash").isEmpty();
    }

    /** Children in saved order, then the adult. */
    public synchronized List<Person> people() {
        JSONArray all = profiles();
        List<Person> kids = new ArrayList<>();
        Person adult = null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            Person person = person(o);
            if (person.adult) adult = person;
            else kids.add(person);
        }
        if (adult != null) kids.add(adult);
        return kids;
    }

    public synchronized Person person(String id) {
        JSONObject o = find(profiles(), id);
        return o == null ? null : person(o);
    }

    public synchronized boolean setPin(String id, String pin) {
        PinBook created = PinBook.create(pin);
        if (created == null) return false;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null) return false;
        try {
            o.put("salt", created.saltHex);
            o.put("hash", created.hashHex);
            o.put("iter", created.iterations);
            o.put("fails", 0);
            o.put("lockUntil", 0);
        } catch (Exception e) {
            return false;
        }
        save(all);
        return true;
    }

    public synchronized String verify(String id, String pin, long now) {
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null) return "bad";
        PinBook book = book(o);
        String verdict = book.verify(pin, now);
        try {
            o.put("fails", book.fails);
            o.put("lockUntil", book.lockUntil);
        } catch (Exception e) {
            return verdict;
        }
        save(all);
        return verdict;
    }

    public synchronized int lockLeft(String id, long now) {
        JSONObject o = find(profiles(), id);
        if (o == null) return 0;
        long until = o.optLong("lockUntil");
        if (until <= now) return 0;
        return (int) ((until - now + 999) / 1000);
    }

    public synchronized boolean addKid(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 1 || trimmed.length() > 16) return false;
        JSONArray all = profiles();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && trimmed.equalsIgnoreCase(o.optString("name"))) return false;
        }
        String id = "k" + PinBook.randomHex(8);
        while (find(all, id) != null) id = "k" + PinBook.randomHex(8);
        all.put(blank(id, trimmed, false));
        save(all);
        return true;
    }

    public synchronized void remove(String id) {
        if (id == null || "adult".equals(id)) return;
        JSONArray all = profiles();
        JSONArray next = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) next.put(o);
        }
        save(next);
        if (id.equals(prefs.getString("last_kid", ""))) prefs.edit().remove("last_kid").commit();
        if (id.equals(prefs.getString("sess_id", ""))) end("");
    }

    public synchronized List<String> apps(String id) {
        List<String> out = new ArrayList<>();
        JSONObject o = find(profiles(), id);
        if (o == null) return out;
        JSONArray apps = o.optJSONArray("apps");
        if (apps == null) return out;
        for (int i = 0; i < apps.length(); i++) {
            String pkg = apps.optString(i);
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    public synchronized void setApps(String id, List<String> packages) {
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null) return;
        JSONArray apps = new JSONArray();
        if (packages != null) {
            for (String pkg : packages) {
                if (pkg != null && !pkg.isEmpty() && !Gate.youtube(pkg) && !Gate.player(pkg)) apps.put(pkg);
            }
        }
        try {
            o.put("apps", apps);
        } catch (Exception ignored) {
            return;
        }
        save(all);
    }

    public synchronized Session session(long now) {
        String id = prefs.getString("sess_id", "");
        if (id.isEmpty()) return Session.NONE;
        String savedBoot = prefs.getString("sess_boot", "");
        long mark = prefs.getLong("sess_mark", 0);
        long elapsed = SystemClock.elapsedRealtime();
        if (!bootId().equals(savedBoot) || elapsed + 2000 < mark) {
            powerOffAt(prefs.getLong("sess_awake", 0));
            return Session.NONE;
        }
        JSONObject o = find(profiles(), id);
        if (o == null) {
            end("");
            return Session.NONE;
        }
        long deadline = prefs.getLong("sess_deadline", 0);
        boolean adult = o.optBoolean("adult");
        String name = o.optString("name");
        boolean free = prefs.getBoolean("sess_free", false);
        if (!adult && (prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false))) {
            long remain = prefs.getLong("sess_remain", 0);
            if (remain <= 0) return new Session(true, false, true, id, name, 0, now, false, free);
            int left = (int) ((remain + 999) / 1000);
            return new Session(true, false, false, id, name, left, now + remain, true, free);
        }
        if (!adult && deadline <= now) {
            return new Session(true, false, true, id, name, 0, deadline, false, free);
        }
        return new Session(true, adult, false, id, name, Gate.remainingSec(now, deadline), deadline, false, free);
    }

    /** Freezes a child countdown while the launcher menu is in front. */
    public synchronized void hold(long now) {
        if (prefs.getBoolean("sess_free", false)) return;
        if (prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false)) return;
        String id = prefs.getString("sess_id", "");
        if (id.isEmpty()) return;
        JSONObject o = find(profiles(), id);
        if (o == null || o.optBoolean("adult")) return;
        long deadline = prefs.getLong("sess_deadline", 0);
        if (deadline <= now) return;
        long remain = Gate.frozenMs(now, deadline);
        if (remain <= 0) return;
        prefs.edit().putBoolean("sess_hold", true).putLong("sess_remain", remain).commit();
    }

    /** Starts the wall clock again after the child leaves the launcher. A leftover manual pause starts too. */
    public synchronized void release(long now) {
        boolean paused = prefs.getBoolean("sess_pause", false);
        boolean held = prefs.getBoolean("sess_hold", false);
        if (!paused && !held) return;
        long remain = prefs.getLong("sess_remain", 0);
        prefs.edit()
                .putBoolean("sess_pause", false)
                .putBoolean("sess_hold", false)
                .remove("sess_remain")
                .putLong("sess_deadline", Gate.resumeAt(now, remain))
                .commit();
    }

    public synchronized long bank(String id) {
        JSONObject o = find(profiles(), id);
        if (o == null || o.optBoolean("adult")) return 0;
        return Math.max(0L, o.optLong("bank", 0));
    }

    /** Saved minutes that still count. A previous day's remainder is already over. */
    public synchronized long bankToday(String id, long now) {
        JSONObject o = find(profiles(), id);
        if (o == null || o.optBoolean("adult")) return 0;
        if (!Gate.sameDay(o.optInt("roundDay", 0), now)) return 0;
        return Math.max(0L, o.optLong("bank", 0));
    }

    private long savedToday(JSONObject o, long now) {
        if (o == null || !Gate.sameDay(o.optInt("roundDay", 0), now)) return 0;
        return Math.max(0L, o.optLong("bank", 0));
    }

    /** This child used up today's round. Tomorrow is not spent, and a reboot today does not refill it. */
    public synchronized boolean spent(String id) {
        JSONObject o = find(profiles(), id);
        if (o == null || o.optBoolean("adult")) return false;
        long now = System.currentTimeMillis();
        if (freeToday(o, now)) return false;
        boolean recorded = o.optBoolean("started", false) || o.has("bank");
        return Gate.stillSpent(o.optInt("roundDay", 0), o.optLong("bank", 0), recorded, now);
    }

    /** This child may watch without a minute cap until the next local midnight. */
    public synchronized boolean freeToday(String id, long now) {
        return freeToday(find(profiles(), id), now);
    }

    private boolean freeToday(JSONObject o, long now) {
        return o != null && !o.optBoolean("adult") && o.optInt("freeDay", 0) == Gate.dayKey(now);
    }

    /** Ends a child session once the allowed hours have passed, keeping whatever time is left. */
    public synchronized boolean lockIfClosed(long now) {
        Session current = session(now);
        if (!current.active || current.adult || current.expired) return false;
        if (Gate.kidAllowedNow(now)) return false;
        end("已過可看時間");
        return true;
    }

    /** Starts a round. A child whose saved time is already used up does not get another one. */
    public synchronized boolean begin(String id, long now, boolean timed) {
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null) return false;
        boolean adult = o.optBoolean("adult");
        int minutes = o.optInt("minutes", Gate.DEFAULT_MINUTES);
        long span = minutes * 60_000L;
        boolean free = timed && !adult && freeToday(o, now);
        long window = Gate.windowEnd(now);
        if (free && window <= now) return false;
        if (timed && !adult && !free) {
            boolean marked = o.optBoolean("started", false) || o.has("bank");
            long bank = Math.max(0L, o.optLong("bank", 0));
            span = Gate.daySpan(o.optInt("roundDay", 0), bank, marked, minutes, now);
            if (span <= 0) return false;
            try {
                o.put("started", true);
                o.put("bank", 0);
                o.put("roundDay", Gate.dayKey(now));
            } catch (Exception ignored) {
            }
        }
        long deadline = !timed || adult
                ? now + 10L * 365 * 24 * 60 * 60 * 1000
                : (free ? window : now + span);
        SharedPreferences.Editor editor = prefs.edit()
                .putString("profiles", all.toString())
                .putString("sess_boot", bootId())
                .putLong("sess_mark", SystemClock.elapsedRealtime())
                .putString("sess_id", id)
                .putLong("sess_deadline", deadline)
                .putBoolean("sess_free", free)
                .putBoolean("sess_pause", false)
                .putBoolean("sess_hold", false)
                .remove("sess_remain")
                .putLong("sess_awake", now)
                .remove("ended");
        if (!adult) {
            editor.putInt("sess_day", Gate.dayKey(now));
            editor.remove("last_kid");
        } else {
            editor.remove("sess_day");
        }
        editor.commit();
        return true;
    }

    /** A child round does not continue after the local day it started. Yesterday's remainder is dropped. */
    public synchronized boolean rollover(long now) {
        String id = prefs.getString("sess_id", "");
        if (id.isEmpty()) return false;
        int day = prefs.getInt("sess_day", 0);
        if (day == 0 || day == Gate.dayKey(now)) return false;
        JSONObject o = find(profiles(), id);
        if (o == null || o.optBoolean("adult")) return false;
        endAt("新的一天，昨天的剩餘不保留", now, false);
        return true;
    }

    public synchronized void end(String note) {
        endAt(note, System.currentTimeMillis());
    }

    private void endAt(String note, long now) {
        endAt(note, now, true);
    }

    /** keep is false when the round is zeroed: the leftover is dropped, and the picker should open. */
    private void endAt(String note, long now, boolean keep) {
        String id = prefs.getString("sess_id", "");
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        boolean free = prefs.getBoolean("sess_free", false);
        if (o != null && !o.optBoolean("adult") && !(free && keep)) {
            long remain = 0;
            if (keep) {
                remain = prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false)
                        ? prefs.getLong("sess_remain", 0)
                        : Gate.frozenMs(now, prefs.getLong("sess_deadline", now));
            }
            int day = prefs.getInt("sess_day", 0);
            if (day == 0) day = Gate.dayKey(now);
            try {
                o.put("started", true);
                o.put("bank", Math.max(0L, remain));
                o.put("roundDay", day);
                if (!keep) o.remove("freeDay");
            } catch (Exception ignored) {
            }
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (o != null) editor.putString("profiles", all.toString());
        editor.remove("sess_boot").remove("sess_mark").remove("sess_id").remove("sess_deadline").remove("sess_awake").remove("sess_day");
        editor.putBoolean("sess_free", false).putBoolean("sess_pause", false).putBoolean("sess_hold", false).remove("sess_remain");
        if (o != null && !o.optBoolean("adult")) editor.putString("last_kid", id);
        if (note != null && !note.isEmpty()) editor.putString("ended", note);
        if (!keep) editor.putBoolean("open_picker", true);
        editor.commit();
    }

    public synchronized boolean consumeOpenPicker() {
        if (!prefs.getBoolean("open_picker", false)) return false;
        prefs.edit().remove("open_picker").commit();
        return true;
    }

    /** Power off ends the round and asks for an account again. A child's leftover stays on that account. */
    public synchronized void powerOff() {
        powerOffAt(0);
    }

    /** Ends the round as of an earlier moment, so time passed while the TV was off is not counted. */
    public synchronized void powerOffAt(long at) {
        long now = System.currentTimeMillis();
        long when = at > 0 && at < now ? at : now;
        endAt("", when);
        prefs.edit().putBoolean("power_off", true).commit();
    }

    public synchronized void noteAwake(long now) {
        if (prefs.getString("sess_id", "").isEmpty()) return;
        long prev = prefs.getLong("sess_awake", 0);
        if (prev > 0 && now - prev < 10_000L) return;
        SharedPreferences.Editor editor = prefs.edit().putLong("sess_awake", now);
        mirrorInto(editor, now);
        editor.commit();
    }

    /** Writes the live remainder onto the child, so a restart cannot look like a fresh 30 minutes. */
    private void mirrorInto(SharedPreferences.Editor editor, long now) {
        if (prefs.getBoolean("sess_free", false)) return;
        String id = prefs.getString("sess_id", "");
        if (id.isEmpty()) return;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return;
        boolean frozen = prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false);
        long remain = frozen
                ? prefs.getLong("sess_remain", 0)
                : Gate.frozenMs(now, prefs.getLong("sess_deadline", now));
        try {
            o.put("started", true);
            o.put("bank", Math.max(0L, remain));
            if (remain <= 0) o.put("roundDay", Gate.dayKey(now));
        } catch (Exception ignored) {
            return;
        }
        editor.putString("profiles", all.toString());
    }

    public synchronized boolean powerOffPending() {
        return prefs.getBoolean("power_off", false);
    }

    public synchronized boolean consumePowerOff() {
        if (!powerOffPending()) return false;
        prefs.edit().remove("power_off").commit();
        return true;
    }

    private void apply(String op, int minutes, long now) {
        if ("cancel".equals(op)) {
            end("這一輪已結束");
            return;
        }
        boolean frozen = prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false);
        long have = frozen
                ? prefs.getLong("sess_remain", 0)
                : Gate.frozenMs(now, prefs.getLong("sess_deadline", now));
        long next = Gate.shift(have, op, minutes);
        if (next <= 0) {
            endAt("時間到了", now, false);
            return;
        }
        SharedPreferences.Editor editor = prefs.edit().putLong("sess_deadline", now + next);
        if (frozen) editor.putLong("sess_remain", next);
        editor.commit();
    }

    public synchronized String takeEnded() {
        String note = prefs.getString("ended", "");
        if (!note.isEmpty()) prefs.edit().remove("ended").commit();
        return note;
    }

    public synchronized int mode(long now) {
        Session session = session(now);
        if (!session.active || session.expired) return Gate.MODE_LOCKED;
        if (session.adult) return Gate.MODE_OPEN;
        return Gate.MODE_KID;
    }

    /** Applies one cloud command. A running round, and 歸零, both check the parent PIN. */
    public synchronized String cloud(String op, int minutes, String password, long now) {
        return cloud(op, minutes, password, "", now);
    }

    public synchronized String cloud(String op, int minutes, String password, String account, long now) {
        Session session = session(now);
        boolean running = session.active && !session.adult && !session.expired;
        String id = account == null ? "" : account.trim();
        boolean targeted = !id.isEmpty();
        String pinState = null;
        if (running && ("set".equals(op) || "cancel".equals(op))) pinState = verify("adult", password, now);
        String result = Gate.cloud(op, minutes, running, pinState, targeted);
        if (!"ok".equals(result)) return result;
        if ("cancel".equals(op)) {
            end("這一輪已結束");
            return "ok";
        }
        if ("zero".equals(op)) return zeroOut(id, targeted, running, session);
        if (targeted && running && id.equals(session.id)) {
            if (prefs.getBoolean("sess_free", false) && ("add".equals(op) || "set".equals(op))) {
                return credit(id, op, minutes, now) ? "ok" : "bad";
            }
            apply(op, minutes, now);
            return "ok";
        }
        if (targeted) return credit(id, op, minutes, now) ? "ok" : "bad";
        apply(op, minutes, now);
        return "ok";
    }

    /** Clears one round. The television stays on and the account picker opens. */
    private String zeroOut(String id, boolean targeted, boolean running, Session session) {
        if (running && (!targeted || id.equals(session.id))) {
            endAt("時間到了", System.currentTimeMillis(), false);
            return "ok";
        }
        if (targeted) return clearBank(id) ? "ok" : "bad";
        return "bad";
    }

    private boolean clearBank(String id) {
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return false;
        try {
            o.put("started", true);
            o.put("bank", 0);
            o.put("roundDay", Gate.dayKey(System.currentTimeMillis()));
            o.remove("freeDay");
        } catch (Exception e) {
            return false;
        }
        save(all);
        return true;
    }

    /** Puts minutes on one child's saved remainder. The account page reads this bank. */
    private boolean credit(String id, String op, int minutes, long now) {
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return false;
        long have = savedToday(o, now);
        if ("add".equals(op) && minutes < 0 && have <= 0) return false;
        long next = Gate.shift(have, op, minutes);
        try {
            o.put("started", true);
            o.put("bank", next);
            o.put("roundDay", Gate.dayKey(now));
        } catch (Exception e) {
            return false;
        }
        save(all);
        return true;
    }

    /** Local add or set. The adult pin was already checked on this TV. */
    public synchronized String grant(String op, int minutes, long now) {
        Session session = session(now);
        boolean running = session.active && !session.adult && !session.expired;
        String result = Gate.cloud(op, minutes, running, "ok");
        if ("ok".equals(result)) apply(op, minutes, now);
        return result;
    }

    public synchronized boolean setMinutes(String id, int minutes) {
        if (minutes != 30 && minutes != 60) return false;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return false;
        try {
            o.put("minutes", minutes);
        } catch (Exception e) {
            return false;
        }
        save(all);
        return true;
    }

    /**
     * Adds one account-page step onto a child's bank. Watching does not start.
     * A live round for that same child is extended too, so the next mirror keeps the grant.
     */
    public synchronized boolean topUp(String id, int minutes, long now) {
        if (id == null || !Gate.pickerAdd(minutes)) return false;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return false;
        long have = savedToday(o, now);
        try {
            o.put("started", true);
            o.put("bank", Gate.shift(have, "add", minutes));
            o.put("roundDay", Gate.dayKey(now));
        } catch (Exception e) {
            return false;
        }
        save(all);
        Session session = session(now);
        if (session.active && !session.expired && !session.adult && id.equals(session.id)
                && !prefs.getBoolean("sess_free", false)) {
            apply("add", minutes, now);
        }
        return true;
    }

    /** Lets one child watch without a minute cap until today's window closes. The bank stays as it is. */
    public synchronized boolean grantToday(String id, long now) {
        if (id == null) return false;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return false;
        Session session = session(now);
        boolean live = session.active && !session.expired && !session.adult && id.equals(session.id);
        boolean already = prefs.getBoolean("sess_free", false);
        try {
            o.put("freeDay", Gate.dayKey(now));
            if (live && !already) {
                long remain = prefs.getBoolean("sess_pause", false) || prefs.getBoolean("sess_hold", false)
                        ? prefs.getLong("sess_remain", 0)
                        : Gate.frozenMs(now, prefs.getLong("sess_deadline", now));
                o.put("started", true);
                o.put("bank", Math.max(0L, remain));
                o.put("roundDay", Gate.dayKey(now));
            }
        } catch (Exception e) {
            return false;
        }
        save(all);
        if (live) {
            prefs.edit()
                    .putBoolean("sess_free", true)
                    .putLong("sess_deadline", Gate.windowEnd(now))
                    .putBoolean("sess_pause", false)
                    .putBoolean("sess_hold", false)
                    .remove("sess_remain")
                    .putLong("sess_awake", now)
                    .commit();
        }
        return true;
    }

    /** Starts a fresh block after the previous one ended. Length is the granted minutes. */
    public synchronized void beginMinutes(String id, long now, int minutes) {
        if (minutes != 30 && minutes != 60) return;
        JSONArray all = profiles();
        JSONObject o = find(all, id);
        if (o == null || o.optBoolean("adult")) return;
        try {
            o.put("bank", minutes * 60_000L);
            o.put("roundDay", Gate.dayKey(now));
            o.put("started", true);
        } catch (Exception ignored) {
            return;
        }
        save(all);
        if (!Gate.kidAllowedNow(now)) return;
        begin(id, now, true);
    }

    public synchronized Person lastKid() {
        JSONObject o = find(profiles(), prefs.getString("last_kid", ""));
        if (o == null || o.optBoolean("adult")) return null;
        return person(o);
    }

    public synchronized void ensureCloud() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!hex(prefs.getString("cloud_device_id", ""), 32)) {
            editor.putString("cloud_device_id", PinBook.randomHex(32));
            changed = true;
        }
        if (!hex(prefs.getString("cloud_secret", ""), 64)) {
            editor.putString("cloud_secret", PinBook.randomHex(64));
            changed = true;
        }
        String code = prefs.getString("cloud_pair_code", "");
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            editor.putString("cloud_pair_code", String.format(Locale.US, "%06d", new java.security.SecureRandom().nextInt(1_000_000)));
            changed = true;
        }
        if (changed) editor.commit();
    }

    public synchronized String deviceId() {
        return prefs.getString("cloud_device_id", "");
    }

    public synchronized String secret() {
        return prefs.getString("cloud_secret", "");
    }

    public synchronized String pairCode() {
        return prefs.getString("cloud_pair_code", "");
    }

    public String cloudName() {
        return "電視";
    }

    public synchronized String fingerprint() {
        return prefs.getString("cloud_fp", "");
    }

    public synchronized void setFingerprint(String fp) {
        prefs.edit().putString("cloud_fp", fp == null ? "" : fp).commit();
    }

    public synchronized boolean cloudOn() {
        return prefs.getBoolean("cloud_on", true);
    }

    public synchronized void setCloudOn(boolean on) {
        prefs.edit().putBoolean("cloud_on", on).commit();
    }

    private void renameAdult() {
        JSONArray all = profiles();
        JSONObject adult = find(all, "adult");
        if (adult == null || !"大人".equals(adult.optString("name"))) return;
        try {
            adult.put("name", "家長");
            save(all);
        } catch (Exception ignored) {
        }
    }

    private static String bootId() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("/proc/sys/kernel/random/boot_id"));
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private JSONArray profiles() {
        try {
            return new JSONArray(prefs.getString("profiles", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void save(JSONArray people) {
        prefs.edit().putString("profiles", people.toString()).commit();
    }

    private static JSONObject find(JSONArray people, String id) {
        for (int i = 0; i < people.length(); i++) {
            JSONObject o = people.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private static JSONObject blank(String id, String name, boolean adult) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("adult", adult);
            o.put("minutes", Gate.DEFAULT_MINUTES);
            o.put("apps", new JSONArray());
            o.put("salt", "");
            o.put("hash", "");
            o.put("iter", 0);
            o.put("fails", 0);
            o.put("lockUntil", 0);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static Person person(JSONObject o) {
        return new Person(
                o.optString("id"),
                o.optString("name"),
                o.optBoolean("adult"),
                !o.optString("hash").isEmpty(),
                o.optInt("minutes", Gate.DEFAULT_MINUTES));
    }

    private static PinBook book(JSONObject o) {
        PinBook book = new PinBook();
        book.fails = o.optInt("fails");
        book.lockUntil = o.optLong("lockUntil");
        book.saltHex = o.optString("salt");
        book.hashHex = o.optString("hash");
        book.iterations = o.optInt("iter");
        return book;
    }

    private static boolean hex(String value, int length) {
        if (value == null || value.length() != length) return false;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }
}
