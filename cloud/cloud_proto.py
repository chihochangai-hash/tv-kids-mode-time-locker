# -*- coding: utf-8 -*-
"""Cloud remote protocol shared by the home host and each shutdown-timer agent.

The agent dials out. The host only forwards a command to an agent that has
polled recently. Parent passwords are checked on the agent and are never written
to disk here.
"""
import hashlib, json, socket, ssl, threading, time, http.client

DEFAULT_HOST = ""
DEFAULT_PORT = 8443
POLL_SECONDS = 2.0
ONLINE_SECONDS = 6.0
PHONE_WAIT_SECONDS = 8.0
SET_MINUTES = (30, 60)
ADD_MINUTES = (1, 5, 10, 30, -1, -5, -10, -30)
CMD_RESULTS = ("ok", "password", "locked", "bad")


def fingerprint_of(der):
    return hashlib.sha256(der).hexdigest()


def format_fingerprint(fp):
    return " ".join(fp[i:i + 4] for i in range(0, len(fp), 4))


def clean_name(name):
    text = "".join(ch for ch in str(name or "") if ch.isprintable() and ch not in "<>")
    text = " ".join(text.split())
    return (text or "電腦")[:40]


def valid_token(value, length):
    return isinstance(value, str) and len(value) == length and all(c in "0123456789abcdef" for c in value)


def apply_command(timer, auth, cmd, on_zero=None):
    """Apply one remote command. Returns ok, password, locked, or bad.

    add minutes may be negative (subtract). zero triggers immediate shutdown via on_zero
    (or set_minutes(0) so the local tick fires shutdown).
    """
    op = cmd.get("op")
    try:
        minutes = int(cmd.get("minutes") or 0)
    except (TypeError, ValueError):
        minutes = 0
    needs_pw = op in ("set", "cancel") and (  # cloud add/subtract/zero: no parent password
        timer.remaining() is not None
    )
    if needs_pw:
        verdict = auth.verify(cmd.get("password"))
        if verdict == "locked":
            return "locked"
        if verdict != "ok":
            return "password"
    if op == "set" and minutes in SET_MINUTES:
        timer.set_minutes(minutes)
        return "ok"
    if op == "add" and minutes in ADD_MINUTES:
        if minutes < 0 and timer.remaining() is None:
            return "bad"
        timer.add_minutes(minutes)
        rem = timer.remaining()
        if rem is not None and rem <= 0:
            if on_zero:
                on_zero(True)
            else:
                timer.set_minutes(0)
            return "ok"
        return "ok"
    if op == "cancel":
        timer.cancel()
        return "ok"
    if op == "zero":
        if on_zero:
            on_zero(True)
        else:
            timer.set_minutes(0)
        return "ok"
    return "bad"


class CloudHTTP:
    """HTTPS JSON client that checks the certificate fingerprint before sending."""

    def __init__(self, host, port, fingerprint=None, timeout=10):
        self.host = host
        self.port = int(port)
        self.fingerprint = fingerprint or None
        self.timeout = timeout
        self.cookies = {}

    def request(self, method, path, body=None):
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        conn = http.client.HTTPSConnection(self.host, self.port, context=ctx, timeout=self.timeout)
        try:
            conn.connect()
            fp = fingerprint_of(conn.sock.getpeercert(True))
            if not self.fingerprint:
                return 0, {"need_trust": True, "fingerprint": fp}
            if fp != self.fingerprint:
                return 0, {"fingerprint_mismatch": True, "fingerprint": fp}
            data = None if body is None else json.dumps(body).encode("utf-8")
            headers = {}
            if data is not None:
                headers["Content-Type"] = "application/json"
            if self.cookies:
                headers["Cookie"] = "; ".join("%s=%s" % item for item in self.cookies.items())
            conn.request(method, path, body=data, headers=headers)
            resp = conn.getresponse()
            raw = resp.read()
            cookie = resp.getheader("Set-Cookie")
            if cookie and "=" in cookie.split(";", 1)[0]:
                key, value = cookie.split(";", 1)[0].split("=", 1)
                self.cookies[key.strip()] = value.strip()
            try:
                parsed = json.loads(raw.decode("utf-8") or "{}")
            except ValueError:
                parsed = {"raw": raw[:200].decode("utf-8", "replace")}
            if not isinstance(parsed, dict):
                parsed = {}
            return resp.status, parsed
        finally:
            conn.close()


class CloudAgent:
    """Outbound poller. Timer changes and zero-shutdown happen on this agent."""

    def __init__(self, timer, auth, cfg_copy, cfg_update, log=None, on_zero=None):
        self.timer = timer
        self.auth = auth
        self.cfg_copy = cfg_copy
        self.cfg_update = cfg_update
        self.log = log or (lambda _msg: None)
        self.on_zero = on_zero
        self.lock = threading.Lock()
        self.seen_fp = None
        self.text = "雲端遙控未啟動"
        self._stop = threading.Event()
        self._thread = None
        self._mute = False
        self._last_cmd = None
        self._last_result = None

    def snapshot(self):
        with self.lock:
            return {"text": self.text, "fingerprint": self.seen_fp}

    def trust(self):
        with self.lock:
            fp = self.seen_fp
        if not fp:
            return False
        self.cfg_update(cloud_fingerprint=fp)
        self.log("cloud certificate trusted")
        return True

    def start(self):
        previous = self._stop
        previous.set()
        self._stop = threading.Event()
        with self.lock:
            self._mute = False
        self._ensure_identity()
        self._set("正在連上雲端…")
        self._thread = threading.Thread(target=self._run, args=(self._stop,), name="cloud-agent", daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        with self.lock:
            self._mute = True
            self.text = "雲端遙控已關閉"

    def take_cmd(self, cmd):
        cmd_id = cmd.get("id") if isinstance(cmd, dict) else None
        if not cmd_id:
            return None
        with self.lock:
            if cmd_id == self._last_cmd:
                return self._last_result
            result = apply_command(self.timer, self.auth, cmd, on_zero=self.on_zero)
            self._last_cmd = cmd_id
            self._last_result = result
        self.log("cloud %s %s -> %s" % (cmd.get("op"), cmd.get("minutes"), result))
        return result

    def _ensure_identity(self):
        import secrets
        cfg = self.cfg_copy()
        updates = {}
        if not valid_token(cfg.get("cloud_device_id"), 32):
            updates["cloud_device_id"] = secrets.token_hex(16)
        if not valid_token(cfg.get("cloud_secret"), 64):
            updates["cloud_secret"] = secrets.token_hex(32)
        code = str(cfg.get("cloud_pair_code") or "")
        if len(code) != 6 or not code.isdigit():
            updates["cloud_pair_code"] = "%06d" % secrets.SystemRandom().randint(0, 999999)
        if not cfg.get("cloud_name"):
            updates["cloud_name"] = clean_name(socket.gethostname())
        if updates:
            self.cfg_update(**updates)

    def _set(self, text, fp="keep"):
        with self.lock:
            if self._mute:
                return
            self.text = text
            if fp != "keep":
                self.seen_fp = fp

    def _run(self, stop):
        while not stop.is_set():
            try:
                self._once()
            except (OSError, ssl.SSLError, http.client.HTTPException, TimeoutError):
                if not stop.is_set():
                    self._set("雲端重連中")
            except Exception as exc:
                self.log("cloud poll error: %s" % exc)
                if not stop.is_set():
                    self._set("雲端重連中")
            stop.wait(POLL_SECONDS)

    def _once(self):
        cfg = self.cfg_copy()
        host = cfg.get("cloud_host") or DEFAULT_HOST
        port = int(cfg.get("cloud_port") or DEFAULT_PORT)
        fp = cfg.get("cloud_fingerprint") or None
        client = CloudHTTP(host, port, fp, timeout=8)
        if not fp:
            _status, data = client.request("GET", "/v1/health")
            if data.get("need_trust"):
                self._set("尚未信任憑證，請與主機核對後按「信任憑證」\n" + format_fingerprint(data["fingerprint"]), data["fingerprint"])
            else:
                self._set("雲端連線失敗")
            return
        with self.lock:
            ack = {"id": self._last_cmd, "result": self._last_result} if self._last_cmd else None
        for _ in range(2):
            status, data = self._post(client, cfg, ack)
            if data.get("fingerprint_mismatch"):
                self._set("憑證已變更，請再確認後按「信任憑證」\n" + format_fingerprint(data["fingerprint"]), data["fingerprint"])
                return
            if status != 200:
                self._set("雲端拒絕連線 (%s)" % status)
                return
            self._show_link(cfg, host, data)
            if ack and data.get("ack_ok"):
                with self.lock:
                    if self._last_cmd == ack["id"]:
                        self._last_cmd = None
            cmd = data.get("cmd")
            if not cmd:
                return
            result = self.take_cmd(cmd)
            if result is None:
                return
            ack = {"id": cmd["id"], "result": result}

    def _show_link(self, cfg, host, data):
        paired = bool(data.get("paired"))
        if bool(cfg.get("cloud_paired")) != paired:
            self.cfg_update(cloud_paired=paired)
            cfg["cloud_paired"] = paired
        if paired:
            self._set("已連上 %s" % host)
        else:
            self._set("已連上 %s\n請在手機輸入配對碼 %s" % (host, cfg.get("cloud_pair_code")))

    def _post(self, client, cfg, ack):
        body = {
            "device_id": cfg["cloud_device_id"],
            "secret": cfg["cloud_secret"],
            "name": cfg.get("cloud_name") or socket.gethostname(),
            "pair_code": cfg.get("cloud_pair_code"),
            "status": self.timer.status(),
        }
        if ack:
            body["ack"] = ack
        return client.request("POST", "/v1/agent/poll", body)
