#!/usr/bin/env bash
# Pleos Connect Virtual Console(에뮬레이터 8088)을 CLI로 조작한다.
# 콘솔 서버는 브라우저 heartbeat(POST /browser/session, 1초)가 없으면 emulator_inactive(503)를 돌려주므로
# 명령 전에 heartbeat를 백그라운드로 띄운다.
#
#   tools/vconsole.sh gear Drive|Park|Reverse|Neutral
#   tools/vconsole.sh turn Off|BlinkLeft|BlinkRight|FlashLeft|FlashRight
#   tools/vconsole.sh seat driver true|false        # 착좌 여부
#   tools/vconsole.sh state                          # 현재 상태 JSON
set -euo pipefail

PORT=${VCONSOLE_PORT:-18088}
V="http://127.0.0.1:$PORT"
CLIENT="cli-$$"

adb forward "tcp:$PORT" tcp:8088 >/dev/null

heartbeat() {
  while true; do
    curl -s -m 2 -X POST "$V/browser/session?client=$CLIENT" -o /dev/null || true
    sleep 1
  done
}
heartbeat &
HB=$!
disown $HB
trap 'kill $HB 2>/dev/null; curl -s -m 2 -X POST "$V/browser/session?client=$CLIENT&closing=1" -o /dev/null || true' EXIT
sleep 1.5

put() { curl -s -m 5 -X PUT "$V$1" -w " [%{http_code}]\n"; }

case "${1:-}" in
  gear)  put "/vehicle/gear?gear=$2" ;;
  turn)  put "/vehicle/turn-signal?mode=$2" ;;
  seat)  put "/vehicle/seat?kind=occupied&area=$2&value=$3" ;;
  state) curl -s -m 5 "$V/vehicle/state" ;;
  *) echo "usage: $0 gear|turn|seat|state ..." >&2; exit 2 ;;
esac
