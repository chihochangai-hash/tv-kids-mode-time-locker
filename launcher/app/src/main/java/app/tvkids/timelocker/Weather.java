package app.tvkids.timelocker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Open-Meteo weather. No key and no location permission. Leave PLACE blank to hide it. */
public final class Weather {
    public static final long FRESH_MS = 30L * 60L * 1000L;
    /** City label. Empty hides the weather band. */
    public static final String PLACE = "";
    /**
     * Fill these with your own Open-Meteo URLs if you want a forecast.
     * Example shape: latitude, longitude, timezone, forecast_days=7.
     */
    public static final String FORECAST_URL = "";
    public static final String AIR_URL = "";

    public static final class Day {
        public final String label;
        public final String condition;
        public final int high;
        public final int low;

        public Day(String label, String condition, int high, int low) {
            this.label = label;
            this.condition = condition;
            this.high = high;
            this.low = low;
        }
    }

    public static final class Report {
        public final int temp;
        public final int feels;
        public final String condition;
        public final int aqi;
        public final String aqiLabel;
        public final List<Day> days;
        public final long fetchedAt;

        public Report(int temp, int feels, String condition, int aqi, String aqiLabel, List<Day> days, long fetchedAt) {
            this.temp = temp;
            this.feels = feels;
            this.condition = condition;
            this.aqi = aqi;
            this.aqiLabel = aqiLabel == null ? "" : aqiLabel;
            this.days = days;
            this.fetchedAt = fetchedAt;
        }

        public Report withAir(int index, long at) {
            return new Report(temp, feels, condition, index, aqiWord(index), days, at);
        }
    }

    private Weather() {
    }

    public static Report read(String json, long fetchedAt) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.getJSONObject("current");
            int temp = (int) Math.round(current.getDouble("temperature_2m"));
            int feels = (int) Math.round(current.getDouble("apparent_temperature"));
            String condition = phrase((int) Math.round(current.getDouble("weather_code")));
            JSONObject daily = root.getJSONObject("daily");
            JSONArray times = daily.getJSONArray("time");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray highs = daily.getJSONArray("temperature_2m_max");
            JSONArray lows = daily.getJSONArray("temperature_2m_min");
            int count = Math.min(7, Math.min(times.length(), Math.min(codes.length(), Math.min(highs.length(), lows.length()))));
            List<Day> days = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (times.isNull(i) || codes.isNull(i) || highs.isNull(i) || lows.isNull(i)) continue;
                days.add(new Day(
                        dayLabel(times.getString(i), i),
                        phrase((int) Math.round(codes.getDouble(i))),
                        (int) Math.round(highs.getDouble(i)),
                        (int) Math.round(lows.getDouble(i))));
            }
            if (days.isEmpty()) return null;
            return new Report(temp, feels, condition, -1, "", days, fetchedAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int readAqi(String json) {
        try {
            JSONObject current = new JSONObject(json).getJSONObject("current");
            if (!current.has("us_aqi") || current.isNull("us_aqi")) return -1;
            return (int) Math.round(current.getDouble("us_aqi"));
        } catch (Exception ignored) {
            return -1;
        }
    }

    /** Short Traditional Chinese for an Open-Meteo WMO code. */
    public static String phrase(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "晴時多雲";
            case 2 -> "多雲";
            case 3 -> "陰";
            case 45, 48 -> "霧";
            case 51, 53, 55 -> "毛雨";
            case 56, 57 -> "凍毛雨";
            case 61 -> "小雨";
            case 63 -> "雨";
            case 65 -> "大雨";
            case 66, 67 -> "凍雨";
            case 71 -> "小雪";
            case 73, 77 -> "雪";
            case 75 -> "大雪";
            case 80, 81 -> "陣雨";
            case 82 -> "強陣雨";
            case 85, 86 -> "陣雪";
            case 95, 96, 99 -> "雷雨";
            default -> "天氣";
        };
    }

    public static String dayLabel(String date, int index) {
        if (index == 0) return "今天";
        if (index == 1) return "明天";
        if (date == null) return "";
        String[] parts = date.split("-");
        if (parts.length != 3) return date;
        try {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Taipei"));
            calendar.clear();
            calendar.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            String[] weeks = {"", "週日", "週一", "週二", "週三", "週四", "週五", "週六"};
            return weeks[calendar.get(Calendar.DAY_OF_WEEK)];
        } catch (NumberFormatException e) {
            return date;
        }
    }

    public static String aqiWord(int aqi) {
        if (aqi < 0) return "";
        if (aqi <= 50) return "良好";
        if (aqi <= 100) return "普通";
        if (aqi <= 150) return "敏感";
        if (aqi <= 200) return "不健康";
        if (aqi <= 300) return "非常不健康";
        return "危害";
    }

    public static int aqiColor(int aqi) {
        if (aqi <= 50) return 0xFF3DDC97;
        if (aqi <= 100) return 0xFFFFD60A;
        if (aqi <= 150) return 0xFFFF9F0A;
        if (aqi <= 200) return 0xFFFF453A;
        if (aqi <= 300) return 0xFFBF5AF2;
        return 0xFF8E3A4B;
    }
}
