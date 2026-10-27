package app.cleanyourtv.home;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One Open-Meteo read. Called off the main thread. */
public final class WeatherNet {
    private WeatherNet() {
    }

    public static Weather.Report fetch(long now) {
        if (Weather.FORECAST_URL == null || Weather.FORECAST_URL.isEmpty()) return null;
        String body = get(Weather.FORECAST_URL);
        if (body == null) return null;
        Weather.Report report = Weather.read(body, now);
        if (report == null) return null;
        String air = get(Weather.AIR_URL);
        int aqi = air == null ? -1 : Weather.readAqi(air);
        return report.withAir(aqi, now);
    }

    private static String get(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "cleanyourtv-home/0.1");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
