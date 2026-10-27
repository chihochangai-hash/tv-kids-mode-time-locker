#!/bin/sh
export PATH=/opt/bin:/opt/sbin:/usr/sbin:/usr/bin:/sbin:/bin
export SHUTDOWN_TIMER_HOST_DIR=/jffs/ShutdownTimerHost
export SHUTDOWN_TIMER_HOST_BIND=0.0.0.0
export SHUTDOWN_TIMER_HOST_PORT=8443
DIR=/jffs/ShutdownTimerHost
LOG=$DIR/host.log
PIDFILE=/var/run/shutdown_timer_host.pid

# Ensure Entware /opt is available
if [ -d /jffs/opt ] && ! mount | grep -q " on /tmp/opt "; then
  mkdir -p /tmp/opt
  mount --bind /jffs/opt /tmp/opt
fi

# Already running?
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
  exit 0
fi

cd "$DIR" || exit 1
# Prefer Entware python3
PY=/opt/bin/python3
[ -x "$PY" ] || exit 1

# Drop stale listen
killall -q shutdown_timer_host.py 2>/dev/null
sleep 1

nohup "$PY" -u "$DIR/shutdown_timer_host.py" --no-gui >> "$LOG" 2>&1 &
echo $! > "$PIDFILE"