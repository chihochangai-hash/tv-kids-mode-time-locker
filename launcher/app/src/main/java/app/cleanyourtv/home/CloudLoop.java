package app.cleanyourtv.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Dials out to the existing shutdown-timer host. The certificate fingerprint is checked
 * before the device secret is sent. Passwords are not written to the log.
 */
public final class CloudLoop {
    public static volatile String text = "";
    public static volatile String seenFp = "";
    private static volatile boolean run;
    private static Thread thread;
    private static String doneId = "";
    private static String doneResult = "";

    private CloudLoop() {
    }

    public static synchronized void start(android.content.Context context) {
        if (Gate.CLOUD_HOST == null || Gate.CLOUD_HOST.isEmpty()) {
            text = "尚未設定雲端位址";
            return;
        }
        run = true;
        if (thread != null && thread.isAlive()) return;
        android.content.Context app = context.getApplicationContext();
        thread = new Thread(() -> loop(app), "cloud");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        run = false;
    }

    private static void loop(android.content.Context app) {
        Map<String, String> cookies = new LinkedHashMap<>();
        while (true) {
            if (!run) {
                sleep(500);
                continue;
            }
            try {
                Store store = Store.get(app);
                store.ensureCloud();
                if (!store.cloudOn()) {
                    text = "雲端遙控已關閉";
                } else {
                    poll(store, cookies);
                }
            } catch (Exception e) {
                text = "雲端連線失敗".equals(text) ? "雲端重連中" : "雲端連線失敗";
            }
            sleep(2000);
        }
    }

    private static void poll(Store store, Map<String, String> cookies) throws Exception {
        String seen = fingerprint();
        seenFp = seen;
        String stored = store.fingerprint();
        if (stored == null || stored.isEmpty()) {
            text = "尚未信任憑證，請與主機核對後按「信任憑證」\n" + Gate.grouped(seen);
            return;
        }
        if (!stored.equalsIgnoreCase(seen)) {
            text = "憑證已變更，請再確認後按「信任憑證」\n" + Gate.grouped(seen);
            return;
        }
        String ackId = null;
        String ackResult = null;
        for (int n = 0; n < 2; n++) {
            JSONObject body = body(store, ackId, ackResult);
            JSONObject reply = post(body, cookies);
            int status = reply.optInt("status");
            if (status != 200) {
                text = "雲端拒絕連線 (" + status + ")";
                return;
            }
            JSONObject json = reply.optJSONObject("json");
            if (json == null) json = new JSONObject();
            if (json.optBoolean("paired")) text = "已連上 " + Gate.CLOUD_HOST;
            else text = "已連上 " + Gate.CLOUD_HOST + "\n請在手機輸入配對碼 " + store.pairCode();
            if (ackId != null && json.optBoolean("ack_ok") && ackId.equals(doneId)) {
                ackId = null;
                ackResult = null;
            }
            JSONObject cmd = json.optJSONObject("cmd");
            if (cmd == null) return;
            String id = cmd.optString("id");
            if (id.isEmpty()) return;
            String result;
            if (id.equals(doneId)) result = doneResult;
            else {
                result = store.cloud(cmd.optString("op"), cmd.optInt("minutes"), cmd.optString("password", ""),
                        cmd.optString("account", ""), System.currentTimeMillis());
                doneId = id;
                doneResult = result;
            }
            ackId = id;
            ackResult = result;
        }
    }

    private static JSONObject body(Store store, String ackId, String ackResult) throws Exception {
        long now = System.currentTimeMillis();
        Store.Session session = store.session(now);
        boolean running = session.active && !session.adult && !session.expired;
        JSONObject status = new JSONObject();
        status.put("running", running);
        if (running) {
            status.put("remaining", session.left);
            if (session.paused) status.put("target", JSONObject.NULL);
            else status.put("target", Gate.clockTarget(session.deadline));
        } else {
            status.put("remaining", JSONObject.NULL);
            status.put("target", JSONObject.NULL);
        }
        JSONArray accounts = new JSONArray();
        for (Store.Person person : store.people()) {
            if (person.adult) continue;
            int seconds = running && person.id.equals(session.id)
                    ? session.left
                    : (int) ((store.bankToday(person.id, now) + 999) / 1000);
            JSONObject account = new JSONObject();
            account.put("id", person.id);
            account.put("name", person.name);
            account.put("seconds", seconds);
            accounts.put(account);
        }
        status.put("accounts", accounts);
        JSONObject body = new JSONObject();
        body.put("device_id", store.deviceId());
        body.put("secret", store.secret());
        body.put("name", store.cloudName());
        body.put("pair_code", store.pairCode());
        body.put("status", status);
        if (ackId != null) {
            JSONObject ack = new JSONObject();
            ack.put("id", ackId);
            ack.put("result", ackResult);
            body.put("ack", ack);
        }
        return body;
    }

    private static String fingerprint() throws Exception {
        SSLContext context = trustAll();
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(Gate.CLOUD_HOST, Gate.CLOUD_PORT), 8000);
            socket.setSoTimeout(8000);
            socket.startHandshake();
            byte[] der = socket.getSession().getPeerCertificates()[0].getEncoded();
            return PinBook.hex(MessageDigest.getInstance("SHA-256").digest(der));
        }
    }

    private static JSONObject post(JSONObject body, Map<String, String> cookies) throws Exception {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        java.net.URL url = new java.net.URL("https://" + Gate.CLOUD_HOST + ":" + Gate.CLOUD_PORT + "/v1/agent/poll");
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(trustAll().getSocketFactory());
        conn.setHostnameVerifier((host, session) -> true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (!cookies.isEmpty()) {
            StringBuilder header = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (header.length() > 0) header.append("; ");
                header.append(entry.getKey()).append('=').append(entry.getValue());
            }
            conn.setRequestProperty("Cookie", header.toString());
        }
        try (java.io.OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }
        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String raw = read(in);
        String setCookie = conn.getHeaderField("Set-Cookie");
        if (setCookie != null) {
            String pair = setCookie.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq > 0) cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        conn.disconnect();
        JSONObject json;
        try {
            json = new JSONObject(raw.isEmpty() ? "{}" : raw);
        } catch (Exception e) {
            json = new JSONObject();
        }
        JSONObject wrap = new JSONObject();
        wrap.put("status", status);
        wrap.put("json", json);
        return wrap;
    }

    private static SSLContext trustAll() throws Exception {
        TrustManager[] trust = new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String auth) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String auth) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust, new SecureRandom());
        return context;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream stream = in) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = stream.read(chunk)) >= 0) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
