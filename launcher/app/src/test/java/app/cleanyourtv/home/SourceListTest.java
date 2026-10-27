package app.cleanyourtv.home;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SourceListTest {
    @Test
    public void onlyHdmi123() {
        SourceList.In hdmi1 = new SourceList.In("com.mediatek.tis/.HdmiInputService/HW2", "", "HDMI 1", "", true, true, false);
        SourceList.In hdmi2 = new SourceList.In("com.mediatek.tis/.HdmiInputService/HW3", "", "HDMI 2", "PlayStation", true, false, false);
        SourceList.In hdmi3 = new SourceList.In("com.mediatek.tis/.HdmiInputService/HW4", "", "HDMI 3", "", true, true, true);
        SourceList.In child = new SourceList.In("ps", "com.mediatek.tis/.HdmiInputService/HW3", "HDMI 2", "PlayStation", true, true, false);
        SourceList.In tuner = new SourceList.In("dvb", "", "數位電視", "", false, true, false);
        SourceList.In air = new SourceList.In("air", "", "AirPlay", "", false, false, false);
        List<SourceList.Row> rows = SourceList.rows(Arrays.asList(hdmi3, tuner, hdmi2, air, child, hdmi1));
        assertEquals(3, rows.size());
        assertEquals("HDMI 1", rows.get(0).name);
        assertEquals("com.mediatek.tis/.HdmiInputService/HW2", rows.get(0).id);
        assertEquals("已連線", rows.get(0).detail);
        assertEquals("HDMI 2", rows.get(1).name);
        assertEquals("com.mediatek.tis/.HdmiInputService/HW3", rows.get(1).id);
        assertTrue(rows.get(1).detail.contains("沒有裝置"));
        assertEquals("HDMI 3", rows.get(2).name);
        assertTrue(rows.get(2).detail.contains("已連線，裝置待命"));
        assertEquals(1, SourceList.hdmiPort("HDMI 1"));
        assertEquals(0, SourceList.hdmiPort("HW2"));
        assertEquals(4, SourceList.hdmiPort("HDMI 4"));
    }
}
