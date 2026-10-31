# 옆자리 코치 (yeopjari-coach)

**한 줄 소개:** 연수를 마친 초보 운전자가 자주 다니는 경로를 반복 연습할 때, 회전 구간별로 깜빡이 준비를 예고하고 행동이 유지된 구간부터 코치 안내를 줄여 가는 차량용 앱. 현대차·기아 차량용 앱 개발 공모전 출품작.

**APK:** `apk/yeopjari_옆자리코치_release.apk` (release 빌드, 디버그 키 서명)

현재 단계: **P1** — P0 연동 검증 완료, 평가 규칙·안내 단계 엔진 + 시나리오 재생 구현.

## 구조

- `domain/` 순수 Kotlin. `CoachEvent`(입력) → `CoachEngine`(판정·안내 단계) → `CoachOutput`. 단위 테스트 `app/src/test`.
- `probe/` Pleos SDK 어댑터 (Vehicle, NaviHelper) → `CoachEvent`. 모든 이벤트를 `files/probe.log`에 남김.
- `scenario/` `assets/scenarios/*.json` 재생 — 에뮬레이터는 차량 이동을 못 만들어 주행 중 TBT는 이걸로 대신한다.
- `store/` 구간별 안내 단계를 `files/practice.json`에 저장.
- `tools/vconsole.sh` Virtual Console(8088) CLI: `gear Drive|Park`, `turn BlinkLeft|BlinkRight|Off`.

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (API 34, arm64, Automotive AVD · Cold boot · RAM 4 GB). 에뮬레이터 유저는 10.
- 기본 내비 앱(`ai.umos.maps.android.navigation.app`)이 목적지를 갖고 있어야 "연습 시작"이 열린다. 개발자 화면의 "경로 요청"으로 샘플 경로를 넣을 수 있다.
- 에뮬레이터는 차량 이동을 만들 수 없다(내비가 NMEA만 쓰고 에뮬 GNSS HAL 체크섬이 틀림). 주행 중 TBT 갱신은 개발자 화면의 시나리오 재생(▶ 1~5)으로 대신한다. 방향지시등·기어는 `tools/vconsole.sh`로 실제 신호를 넣는다.
- 안전벨트 신호는 에뮬레이터에서 항상 미착용이다. 선택 화면의 시작 버튼을 길게 누르면 개발자 우회.

## 실행

- Pleos Connect Emulator v2.1.0 (API 34, Automotive AVD, Cold boot, RAM 4 GB)
- JDK 17, Android Studio 최신

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest                               # 엔진 단위 테스트
adb shell run-as dev.seobew.yeopjari --user 10 cat files/probe.log   # 이벤트 로그 (에뮬레이터 유저는 10)
```

## SDK

- `ai.pleos.playground:Vehicle:2.3.2` — TurnSignal(CAR_LIGHTS), Gear(CAR_DRIVE), Seat(CAR_SEATS)
- `ai.pleos.playground:NaviHelper:3.0.5` — NAVI_ROUTE, NAVI_ROUTE_SEARCH
- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 불필요)

## P0에서 확인할 것

| # | 항목 | 로그 소스 |
|---|---|---|
| 0 | 에뮬레이터에 기본 내비가 있어 NaviHelper 이벤트가 오는가 | `[Navi]` |
| 1 | 기어 P 전환 이벤트(주차 확인 후보) | `[Gear]` |
| 2 | TBT 이벤트 주기와 `distance` 단위 | `[Navi] onTBTInfo` |
| 4 | 재탐색 시 어떤 콜백이 오는가 | `[Navi] onReRouteEnded` |
| 5 | 방향지시등 변경 구독 vs 상태 조회 | `[TurnSignal]` |
