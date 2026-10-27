package app.cleanyourtv.home;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows the last weather report at once, then refreshes it after half an hour. */
public final class WeatherBoard {
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static volatile Weather.Report cached;

    private WeatherBoard() {
    }

    public static void attach(Activity activity, HomeBoard board) {
        if (Weather.FORECAST_URL == null || Weather.FORECAST_URL.isEmpty()) {
            board.showWeatherUnconfigured();
            return;
        }
        long now = System.currentTimeMillis();
        Weather.Report shown = cached;
        if (shown == null) {
            shown = read(activity);
            if (shown != null) cached = shown;
        }
        if (shown != null) board.showWeather(shown);
        else board.showWeather(null);
        if (shown != null && now - shown.fetchedAt < Weather.FRESH_MS) return;
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                Weather.Report report = WeatherNet.fetch(System.currentTimeMillis());
                if (report == null) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && cached == null) board.showWeatherMissing();
                    });
                    return;
                }
                cached = report;
                save(activity, report);
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) board.showWeather(report);
                });
            } finally {
                busy.set(false);
            }
        }, "weather").start();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    static void save(Context context, Weather.Report report) {
        StringBuilder text = new StringBuilder();
        text.append(report.fetchedAt).append('\n');
        text.append(report.temp).append('\n');
        text.append(report.feels).append('\n');
        text.append(report.condition).append('\n');
        text.append(report.aqi).append('\n');
        for (int i = 0; i < report.days.size(); i++) {
            Weather.Day day = report.days.get(i);
            if (i > 0) text.append('|');
            text.append(day.label).append(',').append(day.condition).append(',').append(day.high).append(',').append(day.low);
        }
        prefs(context).edit().putString("report", text.toString()).commit();
    }

    static Weather.Report read(Context context) {
        String text = prefs(context).getString("report", "");
        if (text == null || text.isEmpty()) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length < 6) return null;
        try {
            long at = Long.parseLong(lines[0]);
            int temp = Integer.parseInt(lines[1]);
            int feels = Integer.parseInt(lines[2]);
            String condition = lines[3];
            int aqi = Integer.parseInt(lines[4]);
            List<Weather.Day> days = new ArrayList<>();
            if (!lines[5].isEmpty()) {
                for (String part : lines[5].split("\\|")) {
                    String[] bits = part.split(",", -1);
                    if (bits.length != 4) return null;
                    days.add(new Weather.Day(bits[0], bits[1], Integer.parseInt(bits[2]), Integer.parseInt(bits[3])));
                }
            }
            if (days.isEmpty()) return null;
            return new Weather.Report(temp, feels, condition, aqi, Weather.aqiWord(aqi), days, at);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
