#!/usr/bin/env bash
# Pinggy tunnel with automatic gatewayUri sync (homolog).
#
# Replaces the manual routine "start tunnel -> copy URL -> PATCH certifier ->
# PATCH local store". Run it in Git Bash INSTEAD of the bare ssh command:
#
#   ./scripts/tunnel-sync.sh
#
# It keeps the tunnel alive (restarts when the free 60-min session expires),
# and every time pinggy prints a fresh https://*.free.pinggy.net URL it
# PATCHes the announced gatewayUri on the certifier (mTLS) and on the local
# TalqGatewayStore. Identity values below must match homolog/README.md.
set -uo pipefail

export OPENSSL_CONF="$(cygpath -w /usr/ssl/openssl.cnf)"
export MSYS_NO_PATHCONV=1

CMS_HOST=iotcertifier.exati.com.br
CMS_PORT=8443
BASE=/cms/9yTZDDpe4H13mObY6AkfXnIxBGdOdfXxOqNawjg235Y
GW=6df4b4cd-da48-4448-bfd7-bba3f5216bf2
CMS_ADDR=10000000-0000-0000-0000-000000000001
CERTS="$(cd "$(dirname "$0")/../homolog/certs" && pwd)"
LOCAL=http://localhost:8080

uuid() { powershell.exe -NoProfile -Command "[guid]::NewGuid().Guid" | tr -d '\r'; }

sync_gateway_uri() {
    local url=$1 rid body status
    # 1) certifier CMS (mTLS via openssl; curl here is Schannel and can't)
    rid=$(uuid)
    body='{"name":"Nansen TALQ Gateway - Iluminacao Publica (homolog)","class":"NansenGatewayClass","functions":[{"id":"gateway-function-01","type":"GatewayFunction","gatewayUri":{"type":"AttributeUri","value":"'"$url"'"}}]}'
    status=$(printf 'PATCH %s/devices/%s?clientAddress=%s&talqRequestId=%s HTTP/1.1\r\nHost: %s:%s\r\ntalq-api-version: 2.6.0\r\nContent-Type: application/json\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' \
        "$BASE" "$GW" "$GW" "$rid" "$CMS_HOST" "$CMS_PORT" "${#body}" "$body" \
      | openssl s_client -connect "$CMS_HOST:$CMS_PORT" -servername "$CMS_HOST" \
          -cert "$CERTS/gw-homolog.crt" -key "$CERTS/gw-homolog.key" -quiet 2>/dev/null | head -1)
    echo ">>> certifier gatewayUri <- $url : ${status:-NO RESPONSE}"
    # 2) local store
    rid=$(uuid)
    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 -X PATCH \
        "$LOCAL/devices/$GW/gateway-function-01/gatewayUri?clientAddress=$CMS_ADDR&talqRequestId=$rid" \
        -H "talq-api-version: 2.6.0" -H "Content-Type: application/json" \
        -d '{"type":"AttributeUri","value":"'"$url"'"}')
    echo ">>> local store gatewayUri <- $url : HTTP $status"
    if [ "$status" != "200" ]; then
        echo ">>> AVISO: app local nao respondeu 200 — esta rodando? (gradlew bootRun)"
    fi
}

while true; do
    echo ">>> starting pinggy tunnel (Ctrl+C twice to stop for good)..."
    # -T: no pseudo-terminal — pinggy then prints the URLs as plain text lines
    # instead of its interactive TUI (whose screen-drawing never completes a
    # line, so nothing shows up through a pipe). sed -u strips \r unbuffered.
    ssh -T -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30 -o ExitOnForwardFailure=yes \
        -p 443 -R0:localhost:8080 a.pinggy.io 2>&1 | sed -u 's/\r//g' | while IFS= read -r line; do
        printf '%s\n' "$line"
        if [[ "$line" =~ (https://[a-z0-9-]+\.free\.pinggy\.net) ]]; then
            echo ""
            echo "==============================================================="
            echo ">>> LINK ATUAL: ${BASH_REMATCH[1]}"
            echo "==============================================================="
            sync_gateway_uri "${BASH_REMATCH[1]}"
        fi
    done
    echo ">>> tunnel ended (free session lasts ~60 min); restarting in 5s..."
    sleep 5
done
