package app.tvkids.timelocker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class WeatherTest {
    @Test
    public void words() {
        assertEquals("晴", Weather.phrase(0));
        assertEquals("晴時多雲", Weather.phrase(1));
        assertEquals("陰", Weather.phrase(3));
        assertEquals("霧", Weather.phrase(45));
        assertEquals("小雨", Weather.phrase(61));
        assertEquals("雷雨", Weather.phrase(95));
        assertEquals("天氣", Weather.phrase(123));
        assertEquals("今天", Weather.dayLabel("2026-10-26", 0));
        assertEquals("明天", Weather.dayLabel("2026-10-27", 1));
        assertEquals("週三", Weather.dayLabel("2026-10-28", 2));
        assertEquals("良好", Weather.aqiWord(42));
        assertEquals("普通", Weather.aqiWord(66));
        assertEquals("", Weather.aqiWord(-1));
    }

    @Test
    public void readsWeek() {
        String json = "{\"current\":{\"temperature_2m\":26.5,\"apparent_temperature\":27.7,\"weather_code\":1},"
                + "\"daily\":{\"time\":[\"2026-10-26\",\"2026-10-27\",\"2026-10-28\"],"
                + "\"weather_code\":[3,1,2],\"temperature_2m_max\":[27.2,26.4,25.2],"
                + "\"temperature_2m_min\":[21.3,20.7,19.6]}}";
        Weather.Report report = Weather.read(json, 50L);
        assertNotNull(report);
        assertEquals(27, report.temp);
        assertEquals(28, report.feels);
        assertEquals("晴時多雲", report.condition);
        assertEquals(-1, report.aqi);
        assertEquals(3, report.days.size());
        assertEquals("今天", report.days.get(0).label);
        assertEquals("陰", report.days.get(0).condition);
        assertEquals(27, report.days.get(0).high);
        assertEquals(21, report.days.get(0).low);
        assertEquals("週三", report.days.get(2).label);
        assertEquals("多雲", report.days.get(2).condition);
        assertEquals(66, Weather.readAqi("{\"current\":{\"us_aqi\":66}}"));
        assertEquals(-1, Weather.readAqi("{\"current\":{}}"));
        assertNull(Weather.read("{}", 1L));
    }
}
