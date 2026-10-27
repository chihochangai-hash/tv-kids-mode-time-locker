package app.cleanyourtv.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Collator;

/** Installed apps, ad-free players, and HDMI rows. Nothing here is bundled inside this apk. */
public final class Devices {
    public static final class Item {
        public final String pkg;
        public final String label;
        public final Drawable art;

        public Item(String pkg, String label) {
            this(pkg, label, null);
        }

        public Item(String pkg, String label, Drawable art) {
            this.pkg = pkg;
            this.label = label;
            this.art = art;
        }
    }

    public static final class Source {
        public final String name;
        public final String detail;
        public final String uri;
        public final boolean connected;

        public Source(String name, String detail, String uri, boolean connected) {
            this.name = name;
            this.detail = detail;
            this.uri = uri;
            this.connected = connected;
        }
    }

    private Devices() {
    }

    public static List<Item> list(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
        String self = context.getPackageName();
        Set<String> seen = new HashSet<>();
        List<Item> out = new ArrayList<>();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(self) || hidden(pkg) || Gate.youtube(pkg) || Gate.player(pkg) || !seen.add(pkg)) continue;
            CharSequence label = info.loadLabel(pm);
            out.add(new Item(pkg, label == null || label.length() == 0 ? pkg : label.toString(), art(pm, info)));
        }
        Collator collator = Collator.getInstance(Locale.TRADITIONAL_CHINESE);
        out.sort((a, b) -> collator.compare(a.label, b.label));
        return out;
    }

    public static List<Item> players(Context context) {
        PackageManager pm = context.getPackageManager();
        List<Item> out = new ArrayList<>();
        try {
            for (PackageInfo info : pm.getInstalledPackages(0)) {
                if (info.packageName == null || !Gate.player(info.packageName)) continue;
                CharSequence label = info.applicationInfo == null ? info.packageName : info.applicationInfo.loadLabel(pm);
                String name = label == null || label.length() == 0 ? info.packageName : label.toString();
                Drawable banner = info.applicationInfo == null ? null : info.applicationInfo.loadBanner(pm);
                if (banner == null && info.applicationInfo != null) banner = info.applicationInfo.loadIcon(pm);
                out.add(new Item(info.packageName, name, banner));
            }
        } catch (Exception ignored) {
            addIfInstalled(pm, out, "io.gh.reisxd.tizentube.cobalt");
            addIfInstalled(pm, out, "org.smarttube.stable");
            addIfInstalled(pm, out, "org.smarttube.beta");
        }
        out.sort((a, b) -> {
            int rank = Integer.compare(Gate.playerRank(a.pkg), Gate.playerRank(b.pkg));
            return rank != 0 ? rank : a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    /** The only app tile on the home. Other installed apps stay off this screen. */
    public static List<Item> shown(Context context) {
        Item best = null;
        for (Item item : players(context)) {
            if (!Gate.tizen(item.pkg)) continue;
            if (best == null || Gate.playerRank(item.pkg) < Gate.playerRank(best.pkg)) best = item;
        }
        List<Item> out = new ArrayList<>();
        if (best != null) out.add(best);
        return out;
    }

    public static void launch(Context context, String pkg) {
        start(context, pkg, false);
    }

    /** Adult home can open official YouTube. A child session still cannot. */
    public static void launchAny(Context context, String pkg) {
        start(context, pkg, true);
    }

    public static List<Item> catalog(Context context) {
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item item : players(context)) {
            if (item.pkg != null && seen.add(item.pkg)) out.add(item);
        }
        String[] tubes = {
                "com.google.android.youtube.tv",
                "com.google.android.youtube.tvkids",
                "com.google.android.youtube",
                "com.amazon.firetv.youtube"
        };
        PackageManager pm = context.getPackageManager();
        for (String pkg : tubes) {
            if (!seen.add(pkg)) continue;
            addIfInstalled(pm, out, pkg);
        }
        for (Item item : list(context)) {
            if (item.pkg != null && seen.add(item.pkg)) out.add(item);
        }
        return out;
    }

    /** Fills the dock from prefer, then the rest of all, and leaves everyone else in rest. */
    public static void dock(List<Item> all, List<String> prefer, int slots, List<Item> dock, List<Item> rest) {
        Set<String> used = new HashSet<>();
        if (prefer != null) {
            for (String pkg : prefer) {
                if (dock.size() >= slots) break;
                for (Item item : all) {
                    if (pkg.equals(item.pkg) && used.add(item.pkg)) {
                        dock.add(item);
                        break;
                    }
                }
            }
        }
        for (Item item : all) {
            if (item.pkg == null || !used.add(item.pkg)) continue;
            if (dock.size() < slots) dock.add(item);
            else rest.add(item);
        }
    }

    private static void start(Context context, String pkg, boolean allowYoutube) {
        if (pkg == null || (!allowYoutube && Gate.youtube(pkg))) return;
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) return;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            Front.seen(pkg, SystemClock.uptimeMillis());
            Store.get(context).release(System.currentTimeMillis());
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    public static boolean open(Context context, String uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Front.seen("tv.input", SystemClock.uptimeMillis());
            Store.get(context).release(System.currentTimeMillis());
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    public static void openSettings(Context context) {
        if (fire(context, new Intent(Settings.ACTION_SETTINGS))) return;
        Intent xiaomi = new Intent();
        xiaomi.setClassName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity");
        if (fire(context, xiaomi)) return;
        fire(context, new Intent(Settings.ACTION_SETTINGS).setPackage("com.android.tv.settings"));
    }

    public static void openWifi(Context context) {
        if (fire(context, new Intent(Settings.ACTION_WIFI_SETTINGS))) return;
        fire(context, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
    }

    private static boolean fire(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            Front.seen(intent.getPackage() == null ? "android.settings" : intent.getPackage(), SystemClock.uptimeMillis());
            Store.get(context).release(System.currentTimeMillis());
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    public static Set<String> pass(Context context) {
        Set<String> packages = new HashSet<>();
        packages.add("com.android.systemui");
        packages.add("android");
        packages.add("com.android.permissioncontroller");
        packages.add("com.google.android.permissioncontroller");
        try {
            String imes = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
            if (imes != null) {
                for (String part : imes.split(":")) {
                    int slash = part.indexOf('/');
                    String pkg = slash < 0 ? part : part.substring(0, slash);
                    if (!pkg.isEmpty()) packages.add(pkg);
                }
            }
        } catch (SecurityException ignored) {
            // This TV rejects enabled_input_methods for targetSdk 34. The D-pad pad does not need an IME.
        }
        return packages;
    }

    public static Set<String> inputPackages(Context context) {
        Set<String> packages = new HashSet<>();
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager != null) {
            for (TvInputInfo info : manager.getTvInputList()) {
                boolean useful = info.isPassthroughInput() || info.getType() == TvInputInfo.TYPE_TUNER;
                if (useful && info.getServiceInfo() != null && info.getServiceInfo().packageName != null) {
                    packages.add(info.getServiceInfo().packageName);
                }
            }
        }
        for (Source source : sources(context)) {
            ResolveInfo resolved = context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source.uri)), 0);
            if (resolved != null && resolved.activityInfo != null && resolved.activityInfo.packageName != null) {
                packages.add(resolved.activityInfo.packageName);
            }
        }
        return packages;
    }

    public static List<Source> sources(Context context) {
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        List<Source> out = new ArrayList<>();
        if (manager == null) return out;
        List<SourceList.In> inputs = new ArrayList<>();
        for (TvInputInfo info : manager.getTvInputList()) {
            if (info.getType() == TvInputInfo.TYPE_TUNER) continue;
            if (!info.isPassthroughInput()) continue;
            int state;
            try {
                state = manager.getInputState(info.getId());
            } catch (IllegalArgumentException e) {
                state = TvInputManager.INPUT_STATE_DISCONNECTED;
            }
            boolean connected = state != TvInputManager.INPUT_STATE_DISCONNECTED;
            boolean standby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
            CharSequence label = info.loadLabel(context);
            CharSequence custom = info.loadCustomLabel(context);
            inputs.add(new SourceList.In(
                    info.getId(),
                    info.getParentId(),
                    label == null ? "" : label.toString(),
                    custom == null ? "" : custom.toString(),
                    info.getType() == TvInputInfo.TYPE_HDMI,
                    connected,
                    standby));
        }
        for (SourceList.Row row : SourceList.rows(inputs)) {
            out.add(new Source(row.name, row.detail, TvContract.buildChannelUriForPassthroughInput(row.id).toString(), row.connected));
        }
        return out;
    }

    public static boolean guardOn(Context context) {
        String enabled;
        try {
            enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        } catch (SecurityException e) {
            return false;
        }
        if (enabled == null) return false;
        String flat = new android.content.ComponentName(context, GuardService.class).flattenToString();
        return enabled.toLowerCase(Locale.US).contains(flat.toLowerCase(Locale.US));
    }

    private static boolean hidden(String pkg) {
        return Gate.stockHome(pkg);
    }

    private static Drawable art(PackageManager pm, ResolveInfo info) {
        Drawable banner = info.activityInfo == null ? null : info.activityInfo.loadBanner(pm);
        if (banner == null) banner = info.loadIcon(pm);
        return banner;
    }

    private static void addIfInstalled(PackageManager pm, List<Item> out, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = info.loadLabel(pm);
            Drawable banner = info.loadBanner(pm);
            if (banner == null) banner = info.loadIcon(pm);
            out.add(new Item(pkg, label == null ? pkg : label.toString(), banner));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }
}
