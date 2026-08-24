#!/usr/bin/env bash
# tunnel.sh — run the Pinggy tunnel MANUALLY, everything else syncs itself.
#
#   ./tunnel.sh        ← that's it. Ctrl+C stops the tunnel, like always.
#
# While the tunnel starts, a background watcher grabs the new URL and:
#   1. rewrites application.yml + passo1/passo3 (so future restarts match)
#   2. PATCHes the announced gatewayUri/crlUrn on the certifier CMS
#   3. upserts the gateway device in the RUNNING app via POST /seed/devices
#      (served gatewayUri updates live — no app restart needed)
# Progress appears in this terminal prefixed with [sync].
set -uo pipefail
cd "$(dirname "$0")"

HOST=iotcertifier.exati.com.br
PORT=8443
BASE=/cms/9yTZDDpe4H13mObY6AkfXnIxBGdOdfXxOqNawjg235Y
GW=6df4b4cd-da48-4448-bfd7-bba3f5216bf2
APP_PORT=8080

export OPENSSL_CONF="$(cygpath -w /usr/ssl/openssl.cnf 2>/dev/null || echo "${OPENSSL_CONF:-}")"
export MSYS_NO_PATHCONV=1

uuid() { powershell.exe -NoProfile -Command "[guid]::NewGuid().Guid" | tr -d '\r\n'; }

sync_all() {
  local url="$1" newhost="${1#https://}"
  echo "[sync] tunnel URL: $url"

  for f in ../src/main/resources/application.yml passo1-POST-devices.json passo3-PATCH-devices-gatewayAddress.json; do
    sed -i "s|https://[a-z0-9-]*\.free\.pinggy\.net|https://${newhost}|g" "$f"
  done
  echo "[sync] files updated"

  local rid body status
  rid=$(uuid)
  body=$(cat passo3-PATCH-devices-gatewayAddress.json)
  status=$(printf 'PATCH %s/devices/%s?clientAddress=%s&talqRequestId=%s HTTP/1.1\r\nHost: %s:%s\r\ntalq-api-version: 2.6.0\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
    "$BASE" "$GW" "$GW" "$rid" "$HOST" "$PORT" "${#body}" "$body" \
    | openssl s_client -connect $HOST:$PORT -servername $HOST \
        -cert certs/gw-homolog.crt -key certs/gw-homolog.key -quiet 2>/dev/null | head -1)
  echo "[sync] CMS gatewayUri PATCH: ${status:-NO RESPONSE}"

  # live-update the running app's gateway device (seed route upserts by address)
  python - "$GW" <<'EOF' > .gw-device-live.json
import json, sys
dev = json.load(open("passo3-PATCH-devices-gatewayAddress.json"))
dev["address"] = sys.argv[1]
json.dump([dev], sys.stdout)
EOF
  local app
  app=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:${APP_PORT}/seed/devices" \
        -H "Content-Type: application/json" --data-binary @.gw-device-live.json)
  rm -f .gw-device-live.json
  if [ "$app" = "201" ] || [ "$app" = "200" ]; then
    echo "[sync] running app updated live (no restart needed)"
  else
    echo "[sync] app not reachable on :${APP_PORT} (HTTP $app) — it will pick the URL up from the yml on next start"
  fi
  echo "[sync] DONE — dashboard product URL (if you keep it as reference): $url"
}

: > tunnel.log
(
  url=""
  for _ in $(seq 1 60); do
    url=$(grep -o 'https://[a-z0-9-]*\.free\.pinggy\.net' tunnel.log | head -1 || true)
    [ -n "$url" ] && break
    sleep 1
  done
  if [ -n "$url" ]; then sync_all "$url"; else echo "[sync] no URL captured after 60s — check tunnel.log"; fi
) &

# the tunnel itself, foreground, exactly like running it by hand.
# The password prompt (certificate password) is answered automatically via
# SSH_ASKPASS — the password lives in .tunnel-pass next to this script.
ASKPASS="$(pwd)/.tunnel-askpass.sh"
printf '#!/bin/sh\ncat "%s"\n' "$(pwd)/.tunnel-pass" > "$ASKPASS"
chmod +x "$ASKPASS"
[ -f .tunnel-pass ] || { echo "dev@nsn" > .tunnel-pass; }
SSH_ASKPASS="$ASKPASS" SSH_ASKPASS_REQUIRE=force DISPLAY="${DISPLAY:-:0}" \
ssh -p 443 -o StrictHostKeyChecking=no -o ServerAliveInterval=30 \
    -R0:localhost:${APP_PORT} a.pinggy.io 2>&1 | tee tunnel.log
rm -f "$ASKPASS"
