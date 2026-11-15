# 옆자리 코치 (yeopjari-coach)

**한 줄 소개:** 연수를 마친 초보 운전자가 자주 다니는 경로를 반복 연습할 때, 회전 구간별로 깜빡이 준비를 예고하고 행동이 유지된 구간부터 코치 안내를 줄여 가는 차량용 앱. 현대차·기아 차량용 앱 개발 공모전 출품작.

**APK:** `apk/스튜디오빛_옆자리코치_release.apk` (release 빌드, 디버그 키 서명)

**테스트 환경:** Pleos Connect Emulator v2.1.0 (AVD `pleos_connect_2_1_0`, 화면 1024x768, Android Automotive, 에뮬레이터 유저 10). 자체 서버·API 키 없음.

**시연 영상:** 접수 메일 첨부 (장면 5개 화면 녹화 3분, 음성 없음). 장면 구성은 `docs/video-plan.md`.

## 심사용 실행 절차 (Pleos Connect Emulator v2.1.0)

1. 설치: `adb install -r apk/스튜디오빛_옆자리코치_release.apk` 후 런처에서 "옆자리 코치" 실행.
2. 선택 화면 하단 **개발자 화면** → **경로 요청**. 기본 내비에 샘플 경로(42dot)가 잡히고, 돌아오면 "내비 목적지: 42dot"이 보인다.
3. 시작 조건은 **정차(기어 P) + 안전벨트**다. 에뮬레이터는 벨트 신호가 항상 미착용이라 **시작 버튼을 길게 누르면** 조건을 우회한다.
4. 에뮬레이터는 차량 이동을 만들 수 없어(내비가 NMEA만 쓰고 에뮬 GNSS HAL 체크섬이 틀림) 주행 중 회전 안내(TBT)는 개발자 화면의 **▶ 1~5 시나리오**로 대신한다. 방향지시등·기어는 Virtual Console(또는 `tools/vconsole.sh turn BlinkRight`, `gear Park`)로 실제 신호를 넣는다.
5. 기어 P를 넣으면(또는 주행 화면의 "연습 종료") 복기 화면이 열린다. 같은 시나리오를 다시 돌리면 확인이 이어진 구간부터 안내가 짧아진다.

| 시나리오 | 보여주는 것 |
|---|---|
| ▶ 1_first_practice | 첫 연습. 회전 300m 전 "거울 확인 → 왼쪽 레버 위/아래 → 감속·차로 이동" 안내. 우회전 확인, 좌회전 미확인 → "확인 1 / 평가 2" |
| ▶ 2_second_practice | 확인 2회 기록이 있는 구간은 짧은 안내("290m 앞 우회전이에요. 거울 보시고, 레버는 위로 올려 주세요.")만 |
| ▶ 3_reroute_and_gap | 재탐색·신호 오류 구간은 감점 없이 제외 |
| ▶ 4_park_then_review | 도착해도 기어 P 전에는 복기를 열지 않음 |
| ▶ 5_live_signal | 가상 TBT + 실제 방향지시등 신호로 확인 |

모든 이벤트는 `files/probe.log`에 남는다: `adb shell run-as dev.seobew.yeopjari --user 10 cat files/probe.log` (에뮬레이터 유저는 10).

## 구조

- `domain/` 순수 Kotlin. `CoachEvent`(입력) → `CoachEngine`(평가 구간·관측 충분 조건·안내 단계) → `CoachOutput`. 단위 테스트 `app/src/test` (`./gradlew testDebugUnitTest`).
- `coach/PracticeSession` 화면 상태 기계(선택 → 주행 중 → 복기). `CoachActivity`가 그린다.
- `probe/` Pleos SDK 어댑터(Vehicle, NaviHelper) → `CoachEvent`.
- `scenario/` `assets/scenarios/*.json` 재생(가상 TBT 입력 모드).
- `store/` 구간별 안내 단계를 `files/practice.json`에 저장.
- `docs/` P0·P1 실측 로그, 시연 영상 계획.

## 빌드

JDK 17, AGP 8.8.2. `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.

## SDK

- `ai.pleos.playground:Vehicle:2.3.2` — TurnSignal(CAR_LIGHTS), Gear(CAR_DRIVE), Seat(CAR_SEATS). 조회 권한만 쓴다.
- `ai.pleos.playground:NaviHelper:3.0.5` — NAVI_ROUTE, NAVI_ROUTE_SEARCH
- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 불필요)
- TTS SDK는 Playground에서 "준비 중"이라 발화는 화면 텍스트 + 플랫폼 TTS(있을 때만).
