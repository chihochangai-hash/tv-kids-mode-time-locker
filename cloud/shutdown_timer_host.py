# -*- coding: utf-8 -*-
"""Home relay for the kids-mode time locker.

Set DEFAULT_HOST in cloud_proto.py, or cloud_host in the agent config, to your
own public hostname. Agents connect outward. Phones talk only to this process.
It does not store the parent password and it does not run shutdown.
Certificates and state.json are created on first run. Do not commit them.
"""
import hashlib, hmac, json, os, secrets, ssl, sys, threading, time
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from cloud_proto import (
    CMD_RESULTS, DEFAULT_HOST, DEFAULT_PORT, ONLINE_SECONDS, PHONE_WAIT_SECONDS,
    SET_MINUTES, ADD_MINUTES, clean_name, fingerprint_of, format_fingerprint, valid_token,
)

HOST_DIR = os.environ.get("SHUTDOWN_TIMER_HOST_DIR") or os.path.join(
    os.environ.get("APPDATA") or os.path.expanduser("~"), "ShutdownTimerHost")
PAGE = r"""<!doctype html><html lang="zh-Hant-TW"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>計時關機 雲端遙控</title><style>
body{margin:0;font-family:-apple-system,"PingFang TC","Microsoft JhengHei",sans-serif;background:#0f172a;color:#e2e8f0}
.w{max-width:460px;margin:auto;padding:20px}h1{font-size:20px;color:#94a3b8;font-weight:500;text-align:center}
#cd{font-size:56px;font-weight:700;margin:8px 0;text-align:center;font-variant-numeric:tabular-nums}#tg{color:#94a3b8;min-height:1.4em;text-align:center}
.g{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:14px}.g4{grid-template-columns:repeat(4,1fr)}
#accounts{display:none;margin-top:12px}#accounts button{width:100%;text-align:left;margin:6px 0}
button{border:0;border-radius:14px;padding:16px 8px;font-size:17px;font-weight:600;background:#1e293b;color:#e2e8f0}
.dev{display:block;width:100%;text-align:left;padding:12px 14px;margin:6px 0}
.on{outline:2px solid #2563eb}.p{background:#2563eb}.c{background:#dc2626;width:100%;margin-top:14px}
input{font-size:28px;width:180px;text-align:center;border-radius:12px;border:0;padding:10px;letter-spacing:6px}
#err,#perr{color:#f87171;min-height:1.4em;text-align:center}.box{background:#1e293b;border-radius:14px;padding:12px;margin:12px 0}
</style></head><body><div class="w">
<div id="login" style="display:none;text-align:center"><h1>請輸入家裡主機上的 PIN</h1>
<input id="pin" inputmode="numeric" maxlength="6"><br><br><button class="p" style="width:180px" onclick="login()">連線</button><div id="err"></div></div>
<div id="main" style="display:none"><h1>計時關機 雲端遙控</h1>
<div class="box"><div>配對新電腦（輸入該電腦畫面上的 6 位數）</div>
<div style="text-align:center;margin-top:8px"><input id="code" inputmode="numeric" maxlength="6">
<button class="p" style="width:110px;margin-left:8px" onclick="pair()">配對</button></div><div id="perr"></div></div>
<div id="list"></div><div id="accounts"></div><div id="cd">--:--</div><div id="tg">請選擇一台電腦</div>
<div class="g"><button class="p" onclick="act('set',30)">30 分鐘</button><button class="p" onclick="act('set',60)">1 小時</button></div>
<div class="g g4"><button onclick="act('add',1)">+1</button><button onclick="act('add',5)">+5</button><button onclick="act('add',10)">+10</button><button onclick="act('add',30)">+30</button></div>
<div class="g g4"><button onclick="act('add',-1)">-1</button><button onclick="act('add',-5)">-5</button><button onclick="act('add',-10)">-10</button><button onclick="act('add',-30)">-30</button></div>
<button class="c" onclick="act('zero',0)">歸零關機</button>
<button style="width:100%;margin-top:8px;background:#334155" onclick="act('cancel',0)">僅取消計時</button></div></div><script>
function f(s){var h=Math.floor(s/3600),m=Math.floor(s%3600/60),x=s%60,p=n=>String(n).padStart(2,'0');return h?h+':'+p(m)+':'+p(x):p(m)+':'+p(x)}
let device=null,account=null;
function needLogin(){document.getElementById('main').style.display='none';document.getElementById('login').style.display='block'}
function showMain(){document.getElementById('login').style.display='none';document.getElementById('main').style.display='block'}
function paintAccounts(d){const box=document.getElementById('accounts');const list=(d&&d.accounts)||[];
 if(!list.length){box.style.display='none';box.replaceChildren();account=null;return}
 box.style.display='block';if(account&&!list.find(a=>a.id==account.id))account=null;
 if(account){const fresh=list.find(a=>a.id==account.id);if(fresh)account=fresh}
 box.replaceChildren();list.forEach(a=>{const b=document.createElement('button');b.className=account&&account.id==a.id?'on':'';
  b.textContent=a.name+'  '+(a.seconds?f(a.seconds):'沒有剩餘');b.onclick=()=>{account=a;select(d)};box.appendChild(b)})}
function select(d){device=d;paintAccounts(d);const kids=(d.accounts||[]).length;
 document.getElementById('cd').textContent=!d.online?'離線':(kids?(account?f(account.seconds):'選擇帳號'):(d.running?f(d.remaining):'未啟動'));
 document.getElementById('tg').textContent=kids?(account?account.name+' 的剩餘時間':d.name+'，請選帳號再加時'):(d.online&&d.running?'預計 '+d.target+' 關機':d.name)}
function paint(list){const box=document.getElementById('list');box.replaceChildren();
 if(!list.length){box.textContent='尚無已配對的電腦';return}
 list.forEach(d=>{const b=document.createElement('button');b.className='dev'+(device&&device.id==d.id?' on':'');
  b.textContent=d.name+(d.online?(d.running?'  '+f(d.remaining):'  未啟動'):'  離線');b.onclick=()=>{account=null;select(d)};box.appendChild(b)});
 const current=list.find(d=>device&&d.id==device.id);if(current)select(current);else if(!device&&list.length==1)select(list[0])}
async function load(){try{const r=await fetch('/v1/devices');if(r.status==401)return needLogin();showMain();paint((await r.json()).devices||[])}catch(e){}}
async function login(){const r=await fetch('/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pin:document.getElementById('pin').value})});
 if(r.ok){document.getElementById('err').textContent='';load()}else document.getElementById('err').textContent=r.status==429?'嘗試太多次，請稍後再試':'PIN 錯誤'}
async function pair(){const r=await fetch('/v1/pair',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:document.getElementById('code').value})});
 document.getElementById('perr').textContent=r.ok?'已配對':'配對碼不對，或那台電腦現在沒有連上來';if(r.ok)load()}
async function act(op,minutes){if(!device){alert('請先選擇電腦');return}
 if((device.accounts||[]).length&&op!='cancel'&&op!='zero'&&!account){alert('請先選擇帳號');return}
 let pw=null;if(device.running&&(op=='set'||op=='cancel')){pw=prompt(op=='zero'?'歸零關機需要家長密碼':(op=='cancel'?'結束計時需要家長密碼':'變更時間需要家長密碼'));if(pw===null)return}
 const r=await fetch('/v1/cmd',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({device_id:device.id,op:op,minutes:minutes,password:pw,account:account?account.id:''})});
 if(r.status==401)return needLogin();
 if(r.status==403){alert('家長密碼錯誤');return}
 if(r.status==429){alert('密碼錯誤太多次，請稍後再試');return}
 if(r.status==409){alert('這台電腦離線，或上一個指令還在處理');return}
 if(r.status==504){alert('電腦沒有即時回應。請看它畫面上的倒數是否已變更');load();return}
 if(!r.ok){alert('指令失敗');return}
 const d=await r.json();d.id=device.id;d.name=device.name;d.online=true;select(d);load()}
load();setInterval(load,1000);
</script></body></html>"""


def _account_id(value):
    text = "".join(ch for ch in str(value or "") if ch.isalnum() or ch in "-_")
    return text[:40]


def _accounts(raw):
    found = []
    if not isinstance(raw, list):
        return found
    for item in raw[:12]:
        if not isinstance(item, dict):
            continue
        ident = _account_id(item.get("id"))
        name = clean_name(item.get("name"))
        if not ident or not str(item.get("name") or "").strip():
            continue
        try:
            seconds = int(item.get("seconds") or 0)
        except (TypeError, ValueError):
            seconds = 0
        if seconds < 0:
            seconds = 0
        found.append({"id": ident, "name": name, "seconds": seconds})
    return found


def _same(left, right):
    left, right = str(left or ""), str(right or "")
    if len(left) != len(right):
        return False
    return hmac.compare_digest(left, right)


def _write_cert(cert_path, key_path):
    raise RuntimeError(
        "cert.pem/key.pem missing; copy existing certs (cryptography not on router)"
    )
    _write_new(cert_path, cert_pem)


def _write_new(path, data):
    tmp = path + ".tmp"
    with open(tmp, "wb") as handle:
        handle.write(data)
    os.replace(tmp, path)


def _fingerprint_pem(cert_path):
    """SHA-256 of cert DER via openssl (no cryptography package)."""
    import subprocess
    der = subprocess.check_output(
        ["openssl", "x509", "-in", cert_path, "-outform", "DER"],
        stderr=subprocess.DEVNULL,
    )
    return fingerprint_of(der)


class HostServer:
    def __init__(self, directory, bind="0.0.0.0", port=DEFAULT_PORT, phone_wait=PHONE_WAIT_SECONDS):
        self.directory = directory
        self.bind = bind
        self.port = int(port)
        self.phone_wait = phone_wait
        self.actual_port = self.port
        self.lock = threading.Lock()
        self.devices = {}
        self.rates = {}
        self.login_fails = {}
        self.httpd = None
        self.thread = None
        os.makedirs(directory, exist_ok=True)
        self.cert_path = os.path.join(directory, "cert.pem")
        self.key_path = os.path.join(directory, "key.pem")
        self.state_path = os.path.join(directory, "state.json")
        self.log_path = os.path.join(directory, "host.log")
        self._load_state()
        if not (os.path.exists(self.cert_path) and os.path.exists(self.key_path)):
            _write_cert(self.cert_path, self.key_path)
            self.log("certificate created")
        self.fingerprint = _fingerprint_pem(self.cert_path)

    def log(self, msg):
        line = "%s %s" % (time.strftime("%Y-%m-%d %H:%M:%S"), msg)
        try:
            with open(self.log_path, "a", encoding="utf-8") as handle:
                handle.write(line + "\n")
        except OSError:
            pass

    def public_url(self):
        host = DEFAULT_HOST or "(set DEFAULT_HOST)"
        return "https://%s:%s/" % (host, self.actual_port)

    def _load_state(self):
        try:
            with open(self.state_path, encoding="utf-8") as handle:
                saved = json.load(handle)
        except (OSError, ValueError):
            saved = {}
        if not isinstance(saved, dict):
            saved = {}
        pin = str(saved.get("pin") or "")
        self.pin = pin if len(pin) == 6 and pin.isdigit() else "%06d" % secrets.randbelow(1000000)
        self.token = saved.get("token") if valid_token(saved.get("token"), 32) else secrets.token_hex(16)
        for device_id, rec in (saved.get("devices") or {}).items():
            if not valid_token(device_id, 32) or not isinstance(rec, dict):
                continue
            if not rec.get("paired") or not valid_token(rec.get("secret_hash"), 64):
                continue
            self.devices[device_id] = self._new_device(clean_name(rec.get("name")), rec["secret_hash"], "", True)
        self._save_unlocked()

    def _new_device(self, name, secret_hash, pair_code, paired):
        return {
            "name": name, "secret_hash": secret_hash, "pair_code": pair_code, "paired": bool(paired),
            "last_seen": 0.0, "status": {}, "pending": None,
        }

    def _save_unlocked(self):
        paired = {}
        for device_id, dev in self.devices.items():
            if dev.get("paired"):
                paired[device_id] = {"name": dev["name"], "secret_hash": dev["secret_hash"], "paired": True}
        payload = {"pin": self.pin, "token": self.token, "devices": paired}
        tmp = self.state_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        os.replace(tmp, self.state_path)

    def _allow_unlocked(self, bucket, ip, limit, window):
        now = time.time()
        key = (bucket, ip)
        hits = [stamp for stamp in self.rates.get(key, []) if now - stamp < window]
        if len(hits) >= limit:
            self.rates[key] = hits
            return False
        hits.append(now)
        self.rates[key] = hits
        return True

    def _prune_unlocked(self):
        now = time.time()
        stale = [device_id for device_id, dev in self.devices.items()
                 if not dev["paired"] and now - dev["last_seen"] > 600]
        for device_id in stale:
            self.devices.pop(device_id, None)

    def device_list(self):
        now = time.time()
        with self.lock:
            rows = []
            for device_id, dev in self.devices.items():
                if not dev["paired"]:
                    continue
                online = now - dev["last_seen"] <= ONLINE_SECONDS
                status = dev.get("status") or {}
                rows.append({
                    "id": device_id, "name": dev["name"], "online": online,
                    "running": bool(status.get("running")) if online else False,
                    "remaining": status.get("remaining") if online else None,
                    "target": status.get("target") if online else None,
                    "accounts": status.get("accounts") if online else [],
                })
        rows.sort(key=lambda row: row["name"])
        return rows

    def device_lines(self):
        now = time.time()
        with self.lock:
            lines = []
            for dev in sorted(self.devices.values(), key=lambda item: item["name"]):
                online = now - dev["last_seen"] <= ONLINE_SECONDS
                if not dev["paired"]:
                    if online:
                        lines.append("%s  待配對" % dev["name"])
                    continue
                if not online:
                    lines.append("%s  離線" % dev["name"])
                elif (dev.get("status") or {}).get("running"):
                    lines.append("%s  在線  剩餘 %s 秒" % (dev["name"], dev["status"].get("remaining")))
                else:
                    lines.append("%s  在線  未啟動" % dev["name"])
            return lines

    def start(self):
        if self.httpd:
            return True
        server = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            timeout = 10

            def handle(self):
                try:
                    super().handle()
                except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError, TimeoutError, ssl.SSLError):
                    return

            def log_message(self, *_args):
                return

            def version_string(self):
                return "ShutdownTimer"

            def _send(self, code, body, ctype="application/json; charset=utf-8", extra=None):
                raw = body.encode("utf-8") if isinstance(body, str) else body
                self.send_response(code)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(raw)))
                self.send_header("Cache-Control", "no-store")
                for key, value in (extra or {}).items():
                    self.send_header(key, value)
                self.end_headers()
                try:
                    self.wfile.write(raw)
                except (BrokenPipeError, ConnectionError, ssl.SSLError):
                    pass

            def _body(self):
                try:
                    length = int(self.headers.get("Content-Length", 0) or 0)
                except ValueError:
                    length = 0
                if length > 8192:
                    return None
                try:
                    payload = json.loads(self.rfile.read(length) or b"{}")
                except (ValueError, OSError):
                    return {}
                return payload if isinstance(payload, dict) else {}

            def _authed(self):
                cookie = SimpleCookie(self.headers.get("Cookie", ""))
                return "sth" in cookie and _same(cookie["sth"].value, server.token)

            def do_GET(self):
                path = urlparse(self.path).path
                if path == "/":
                    return self._send(200, PAGE, "text/html; charset=utf-8")
                if path == "/v1/health":
                    return self._send(200, '{"ok":true}')
                if path == "/v1/devices":
                    if not self._authed():
                        return self._send(401, '{"error":"pin"}')
                    return self._send(200, json.dumps({"devices": server.device_list()}))
                return self._send(404, '{"error":"not found"}')

            def do_POST(self):
                path = urlparse(self.path).path
                payload = self._body()
                if payload is None:
                    return self._send(413, '{"error":"too large"}')
                ip = self.client_address[0]
                if path == "/v1/login":
                    return server.handle_login(self, payload, ip)
                if path == "/v1/pair":
                    if not self._authed():
                        return self._send(401, '{"error":"pin"}')
                    return server.handle_pair(self, payload, ip)
                if path == "/v1/cmd":
                    if not self._authed():
                        return self._send(401, '{"error":"pin"}')
                    return server.handle_cmd(self, payload)
                if path == "/v1/agent/poll":
                    return server.handle_poll(self, payload, ip)
                return self._send(404, '{"error":"not found"}')

        try:
            self.httpd = ThreadingHTTPServer((self.bind, self.port), Handler)
        except OSError as exc:
            self.log("listen failed: %s" % exc)
            self.httpd = None
            raise
        self.actual_port = self.httpd.server_address[1]
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(self.cert_path, self.key_path)
        self.httpd.socket = context.wrap_socket(self.httpd.socket, server_side=True)
        self.thread = threading.Thread(target=self.httpd.serve_forever, name="cloud-host", daemon=True)
        self.thread.start()
        self.log("listening on %s:%s" % (self.bind, self.actual_port))
        return True

    def stop(self):
        httpd = self.httpd
        if not httpd:
            return
        self.httpd = None
        httpd.shutdown()
        httpd.server_close()
        self.log("stopped")

    def handle_login(self, handler, payload, ip):
        now = time.time()
        with self.lock:
            count, stamp = self.login_fails.get(ip, (0, 0))
            if now - stamp >= 60:
                count = 0
            locked = count >= 5
        if locked:
            return handler._send(429, '{"error":"too many"}')
        if _same(payload.get("pin"), self.pin):
            with self.lock:
                self.login_fails.pop(ip, None)
            cookie = "sth=%s; Path=/; Max-Age=31536000; HttpOnly; SameSite=Strict; Secure" % self.token
            return handler._send(200, '{"ok":true}', extra={"Set-Cookie": cookie})
        with self.lock:
            count, stamp = self.login_fails.get(ip, (0, 0))
            if now - stamp >= 60:
                count = 0
            self.login_fails[ip] = (count + 1, now)
        return handler._send(403, '{"error":"bad pin"}')

    def handle_pair(self, handler, payload, ip):
        code = str(payload.get("code") or "")
        limited = False
        found = None
        with self.lock:
            if not self._allow_unlocked("pair", ip, 10, 600):
                limited = True
            elif len(code) == 6 and code.isdigit():
                now = time.time()
                for _device_id, dev in self.devices.items():
                    if dev["paired"] or dev.get("pair_code") != code or now - dev["last_seen"] > 120:
                        continue
                    dev["paired"] = True
                    found = dev["name"]
                    self._save_unlocked()
                    break
        if limited:
            return handler._send(429, '{"error":"too many"}')
        if not found:
            return handler._send(404, '{"error":"code"}')
        self.log("paired %s" % found)
        return handler._send(200, '{"ok":true}')

    def handle_poll(self, handler, payload, ip):
        device_id = payload.get("device_id")
        secret = payload.get("secret")
        if not valid_token(device_id, 32) or not valid_token(secret, 64):
            return handler._send(400, '{"error":"bad device"}')
        code = str(payload.get("pair_code") or "")
        if not (len(code) == 6 and code.isdigit()):
            code = ""
        name = clean_name(payload.get("name"))
        raw_status = payload.get("status") if isinstance(payload.get("status"), dict) else {}
        status = {
            "running": bool(raw_status.get("running")),
            "remaining": raw_status.get("remaining") if isinstance(raw_status.get("remaining"), int) else None,
            "target": raw_status.get("target") if isinstance(raw_status.get("target"), str) else None,
            "accounts": _accounts(raw_status.get("accounts")),
        }
        ack = payload.get("ack") if isinstance(payload.get("ack"), dict) else None
        secret_hash = hashlib.sha256(secret.encode("utf-8")).hexdigest()
        with self.lock:
            if not self._allow_unlocked("poll", ip, 600, 60):
                outcome = (429, {"error": "too many"})
            else:
                self._prune_unlocked()
                dev = self.devices.get(device_id)
                if dev is None:
                    if not self._allow_unlocked("new", ip, 10, 600):
                        outcome = (429, {"error": "too many"})
                    else:
                        dev = self._new_device(name, secret_hash, code, False)
                        dev["last_seen"] = time.time()
                        dev["status"] = status
                        self.devices[device_id] = dev
                        self.log("agent seen %s" % name)
                        outcome = self._poll_result_unlocked(dev, ack)
                elif not _same(dev["secret_hash"], secret_hash):
                    outcome = (401, {"error": "secret"})
                else:
                    renamed = dev["paired"] and dev["name"] != name
                    dev["name"] = name
                    dev["last_seen"] = time.time()
                    dev["status"] = status
                    if code and not dev["paired"]:
                        dev["pair_code"] = code
                    if renamed:
                        self._save_unlocked()
                    outcome = self._poll_result_unlocked(dev, ack)
        return handler._send(outcome[0], json.dumps(outcome[1]))

    def _poll_result_unlocked(self, dev, ack):
        ack_ok = False
        if ack and ack.get("result") in CMD_RESULTS:
            pending = dev.get("pending")
            if pending and ack.get("id") == pending["id"] and pending["result"] is None:
                pending["result"] = ack["result"]
                pending["event"].set()
                dev["pending"] = None
                ack_ok = True
                self.log("ack %s %s" % (dev["name"], ack["result"]))
        cmd = None
        pending = dev.get("pending")
        if pending and pending["result"] is None:
            pending["delivered"] = True
            cmd = {
                "id": pending["id"], "op": pending["op"], "minutes": pending["minutes"],
                "password": pending.get("password"), "account": pending.get("account") or "",
            }
        body = {"paired": bool(dev["paired"]), "ack_ok": ack_ok}
        if cmd:
            body["cmd"] = cmd
        return 200, body

    def handle_cmd(self, handler, payload):
        device_id = payload.get("device_id")
        op = payload.get("op")
        try:
            minutes = int(payload.get("minutes") or 0)
        except (TypeError, ValueError):
            minutes = -1
        if op in ("cancel", "zero"):
            minutes = 0
        allowed = (op == "set" and minutes in SET_MINUTES) or (op == "add" and minutes in ADD_MINUTES) or op in ("cancel", "zero")
        if not allowed or not valid_token(device_id, 32):
            return handler._send(400, '{"error":"bad request"}')
        password = payload.get("password")
        password = None if password is None else str(password)[:200]
        account = _account_id(payload.get("account"))
        pending = None
        dev = None
        with self.lock:
            dev = self.devices.get(device_id)
            if not dev or not dev["paired"]:
                early = (404, {"error": "not paired"})
            elif time.time() - dev["last_seen"] > ONLINE_SECONDS:
                early = (409, {"error": "offline"})
            elif dev.get("pending"):
                early = (409, {"error": "busy"})
            else:
                pending = {
                    "id": secrets.token_hex(8), "op": op, "minutes": minutes, "password": password,
                    "account": account, "delivered": False, "result": None, "event": threading.Event(),
                }
                dev["pending"] = pending
                early = None
                self.log("cmd %s %s %s" % (dev["name"], op, minutes))
        if early:
            return handler._send(early[0], json.dumps(early[1]))
        pending["event"].wait(self.phone_wait)
        with self.lock:
            result = pending["result"]
            status = dict(dev.get("status") or {})
            if dev.get("pending") is pending and (result is not None or not pending["delivered"]):
                dev["pending"] = None
        if result is None:
            return handler._send(504, json.dumps({"error": "timeout", "status": status}))
        if result == "password":
            return handler._send(403, json.dumps({"error": "password", "status": status}))
        if result == "locked":
            return handler._send(429, json.dumps({"error": "locked", "status": status}))
        if result != "ok":
            return handler._send(400, json.dumps({"error": "bad", "status": status}))
        return handler._send(200, json.dumps(status))


def run_gui(server):
    import tkinter as tk
    bg, fg, muted, card = "#0f172a", "#e2e8f0", "#94a3b8", "#1e293b"
    font = "Microsoft JhengHei UI" if os.name == "nt" else "Noto Sans CJK TC"
    root = tk.Tk()
    root.title("計時關機雲端")
    root.configure(bg=bg)
    root.resizable(False, False)
    tk.Label(root, text="計時關機雲端", bg=bg, fg=muted, font=(font, 12)).pack(pady=(14, 4))
    tk.Label(root, text="手機開啟（憑證警告時選進階後繼續）", bg=bg, fg=muted, font=(font, 10)).pack()
    tk.Label(root, text=server.public_url(), bg=bg, fg=fg, font=(font, 12, "bold")).pack(pady=(2, 8))
    tk.Label(root, text="PIN", bg=bg, fg=muted, font=(font, 10)).pack()
    tk.Label(root, text=server.pin, bg=bg, fg="#f8fafc", font=("Consolas", 32, "bold")).pack()
    tk.Label(root, text="憑證指紋", bg=bg, fg=muted, font=(font, 10)).pack(pady=(8, 0))
    tk.Label(root, text=format_fingerprint(server.fingerprint), bg=bg, fg=fg, font=("Consolas", 9),
             wraplength=460, justify="center").pack(padx=16)
    tk.Label(root, text="電腦", bg=bg, fg=muted, font=(font, 10)).pack(pady=(10, 0))
    lines = tk.Label(root, text="", bg=card, fg=fg, font=(font, 11), justify="left", anchor="w",
                     width=42, wraplength=420, padx=10, pady=8)
    lines.pack(padx=16, pady=(4, 16))

    def refresh():
        found = server.device_lines()
        lines.config(text="\n".join(found) if found else "尚無電腦連線")
        root.after(1000, refresh)

    def close():
        server.stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", close)
    refresh()
    root.mainloop()


def main():
    try:
        bind = os.environ.get("SHUTDOWN_TIMER_HOST_BIND", "0.0.0.0")
        port = int(os.environ.get("SHUTDOWN_TIMER_HOST_PORT", str(DEFAULT_PORT)))
        server = HostServer(HOST_DIR, bind=bind, port=port)
        server.start()
        if "--no-gui" in sys.argv:
            print(server.public_url(), flush=True)
            print("fingerprint %s" % server.fingerprint, flush=True)
            try:
                threading.Event().wait()
            except KeyboardInterrupt:
                server.stop()
            return
        run_gui(server)
    except Exception:
        import traceback
        os.makedirs(HOST_DIR, exist_ok=True)
        with open(os.path.join(HOST_DIR, "host.log"), "a", encoding="utf-8") as handle:
            handle.write(traceback.format_exc() + "\n")
        raise


if __name__ == "__main__":
    main()
