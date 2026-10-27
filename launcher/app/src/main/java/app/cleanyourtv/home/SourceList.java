package app.cleanyourtv.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * HDMI 1–3 only.
 * On some MediaTek sets the hardware id is not the port number, so match the HDMI label.
 */
public final class SourceList {
    private SourceList() {
    }

    public static final class In {
        public final String id;
        public final String parentId;
        public final String label;
        public final String custom;
        public final boolean hdmi;
        public final boolean connected;
        public final boolean standby;

        public In(String id, String parentId, String label, String custom, boolean hdmi, boolean connected, boolean standby) {
            this.id = id == null ? "" : id;
            this.parentId = parentId == null ? "" : parentId;
            this.label = label == null ? "" : label;
            this.custom = custom == null ? "" : custom;
            this.hdmi = hdmi;
            this.connected = connected;
            this.standby = standby;
        }

        public String name() {
            return custom.isEmpty() ? label : custom;
        }
    }

    public static final class Row {
        public final String id;
        public final String name;
        public final String detail;
        public final boolean connected;

        public Row(String id, String name, String detail, boolean connected) {
            this.id = id;
            this.name = name;
            this.detail = detail;
            this.connected = connected;
        }
    }

    public static String homeName(int port) {
        return switch (port) {
            case 1 -> "HDMI 1";
            case 2 -> "HDMI 2";
            case 3 -> "HDMI 3";
            default -> "";
        };
    }

    /** Port 1, 2, or 3 from a label such as "HDMI 1". Hardware ids like HW2 are not ports. */
    public static int hdmiPort(String label) {
        if (label == null) return 0;
        String lower = label.toLowerCase(Locale.ROOT);
        int at = lower.indexOf("hdmi");
        if (at < 0) return 0;
        for (int i = at + 4; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= '1' && c <= '9') return c - '0';
            if (c >= '0' && c <= '9') return 0;
        }
        return 0;
    }

    public static List<Row> rows(Collection<In> inputs) {
        List<In> ports = new ArrayList<>();
        for (In in : inputs) {
            if (!in.parentId.isEmpty()) continue;
            int port = hdmiPort(in.label);
            if (port < 1 || port > 3) continue;
            ports.add(in);
        }
        ports.sort((a, b) -> Integer.compare(hdmiPort(a.label), hdmiPort(b.label)));
        List<Row> out = new ArrayList<>();
        for (In port : ports) {
            int number = hdmiPort(port.label);
            String name = homeName(number);
            out.add(new Row(port.id, name, detail(port.label, name, port.connected, port.standby), port.connected));
        }
        return out;
    }

    static String detail(String portLabel, String name, boolean connected, boolean standby) {
        String state = !connected ? "沒有裝置" : standby ? "已連線，裝置待命" : "已連線";
        if (portLabel == null || portLabel.isEmpty() || portLabel.equalsIgnoreCase(name)) return state;
        return portLabel + " · " + state;
    }
}
