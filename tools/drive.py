#!/usr/bin/env python3
"""에뮬레이터 내비에 체크섬이 맞는 NMEA(GPRMC+GPGGA)를 `adb emu geo nmea`로 넣어 차를 움직인다.

`adb emu geo fix`가 만드는 GPGGA는 체크섬이 틀려 Pleos 내비(NmeaParser)가 버린다(P0 실측).

  tools/drive.py LON1,LAT1 LON2,LAT2 ... [--speed-kph 30] [--hz 1]
경유점 사이를 속도에 맞춰 보간하며 1초마다 한 점씩 보낸다.
"""
import argparse
import math
import subprocess
import sys
import time
from datetime import datetime, timezone


def checksum(body: str) -> str:
    c = 0
    for ch in body:
        c ^= ord(ch)
    return f"{c:02X}"


def sentence(body: str) -> str:
    return f"${body}*{checksum(body)}"


def dm(value: float, is_lat: bool) -> tuple[str, str]:
    hemi = ("N" if value >= 0 else "S") if is_lat else ("E" if value >= 0 else "W")
    v = abs(value)
    deg = int(v)
    minutes = (v - deg) * 60
    width = 2 if is_lat else 3
    return f"{deg:0{width}d}{minutes:07.4f}", hemi


def nmea_pair(lat: float, lon: float, speed_kph: float, heading: float) -> list[str]:
    now = datetime.now(timezone.utc)
    hms = now.strftime("%H%M%S.00")
    dmy = now.strftime("%d%m%y")
    la, ns = dm(lat, True)
    lo, ew = dm(lon, False)
    knots = speed_kph / 1.852
    rmc = f"GPRMC,{hms},A,{la},{ns},{lo},{ew},{knots:.1f},{heading:.1f},{dmy},,,A"
    gga = f"GPGGA,{hms},{la},{ns},{lo},{ew},1,08,0.9,30.0,M,0.0,M,,"
    return [sentence(rmc), sentence(gga)]


def send(line: str) -> None:
    subprocess.run(["adb", "emu", "geo", "nmea", line], check=False, capture_output=True)


def bearing(lat1, lon1, lat2, lon2) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    x = math.sin(dl) * math.cos(p2)
    y = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
    return (math.degrees(math.atan2(x, y)) + 360) % 360


def dist_m(lat1, lon1, lat2, lon2) -> float:
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("points", nargs="+", help="LON,LAT")
    ap.add_argument("--speed-kph", type=float, default=30)
    ap.add_argument("--hz", type=float, default=1)
    a = ap.parse_args()
    pts = [tuple(map(float, p.split(","))) for p in a.points]  # (lon, lat)
    step_m = a.speed_kph * 1000 / 3600 / a.hz
    for (lon1, lat1), (lon2, lat2) in zip(pts, pts[1:]):
        d = dist_m(lat1, lon1, lat2, lon2)
        n = max(1, int(d / step_m))
        hd = bearing(lat1, lon1, lat2, lon2)
        for i in range(n + 1):
            t = i / n
            lat, lon = lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t
            for s in nmea_pair(lat, lon, a.speed_kph, hd):
                send(s)
            print(f"{lon:.6f},{lat:.6f} hd={hd:.0f}", flush=True)
            time.sleep(1 / a.hz)
    return 0


if __name__ == "__main__":
    sys.exit(main())
