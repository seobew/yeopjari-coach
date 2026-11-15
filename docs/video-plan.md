# 시연 영상 촬영 계획 (2분)

목표: "신호를 넣으면 반응한다"가 아니라 **"반복 연습에서 코치 안내가 줄고, 불확실하면 평가를 멈춘다"**를 보여준다.
화면 왼쪽 위에 구현 구분 라벨을 자막으로 넣는다: `실제 SDK` / `가상 TBT 입력` / `목업`.

## 준비

```bash
# 에뮬레이터 + Virtual Console
# 직전 크래시 뒤엔 크래시 리포트 전송 대화상자가 부팅을 막는다 → DB 삭제 + -no-metrics
rm -f /tmp/android-$USER/emu-crash-*.db
~/Library/Android/sdk/emulator/emulator -avd pleos_connect_2_1_0 -no-snapshot -no-boot-anim -no-metrics &
adb install -r apk/스튜디오빛_옆자리코치_release.apk
adb shell pm clear --user 10 dev.seobew.yeopjari   # 기록 초기화 (release는 run-as 불가)
# 화면 녹화 (최대 3분, 장면마다 따로)
adb shell screenrecord --time-limit 60 /sdcard/scene1.mp4 &
```

Pleos TTS SDK는 준비 중이고 에뮬레이터에 플랫폼 TTS 엔진이 없다. 발화는 화면 텍스트로 찍고, **편집 때 녹음 음성을 입힌다**. 영상에 "음성은 녹음본" 자막.

## 장면 (총 2분)

| # | 시간 | 조작 | 보여줄 것 | 라벨 |
|---|---|---|---|---|
| 0 | 0:00–0:10 | 선택 화면. 개발자 화면 "경로 요청" → 돌아오면 "내비 목적지: 42dot · 기어 P · 벨트 …" | 정차·벨트 확인 뒤에만 시작 | 실제 SDK |
| 1 | 0:10–0:45 | 개발자 화면 ▶ 1_first_practice → 주행 중 화면 | "다음 우회전, 깜빡이 준비예요." → 확인 / 좌회전은 미확인 → 기어 P → "확인 1 / 평가 2 (제외 0) · 다음 연습: 2번째 좌회전" | 가상 TBT 입력 + 실제 기어 |
| 2 | 0:45–1:15 | ▶ 2_second_practice (사용 확인 2회 기록 주입) | 1번 구간은 짧은 예고 "우회전"만, 2번은 전체 예고. 둘 다 확인 → "확인 2 / 평가 2" | 가상 TBT 입력 (기록 주입 자막) |
| 3 | 1:15–1:40 | ▶ 3_reroute_and_gap | 재탐색 → 진행 중 평가 취소, 신호 오류 → 제외. "확인 0 / 평가 0 (제외 2)" 감점 없음 | 가상 TBT 입력 |
| 4 | 1:40–2:00 | ▶ 5_live_signal + `tools/vconsole.sh turn BlinkRight` + `gear Drive` → `gear Park` | 실제 방향지시등 신호로 "확인", 도착해도 기어 P 전에는 복기가 안 열림 | 실제 SDK (방향지시등·기어) + 가상 TBT |

장면 4에서 깜빡이를 켜면 차량의 사각지대 카메라(EVS) 미리보기가 앱 위를 덮는다. 자르지 말고 그대로 두고 자막: "회전 중엔 화면이 가려질 수 있어 안내는 음성이 기본".

## 나레이션 (녹음용, 한 문장씩)

1. 옆자리 코치는 연수를 마친 초보 운전자가 자주 다니는 길을 혼자 반복 연습하도록 돕습니다.
2. 정차 상태에서 안전벨트를 매면 내비 경로로 연습을 시작합니다.
3. 회전 구간에 가까워지면 깜빡이 준비를 예고만 합니다. 차선이나 주변 차량은 말하지 않습니다.
4. 방향지시등 신호를 관측하면 확인, 데이터는 충분한데 신호가 없으면 미확인으로만 기록합니다.
5. 같은 경로를 다시 달리면, 확인이 이어진 구간부터 예고가 짧아집니다.
6. 재탐색이나 신호 오류가 나면 그 구간은 감점 없이 평가에서 뺍니다.
7. 주차가 확인된 뒤에만 복기를 엽니다. 관측 사실 하나와 다음 연습 하나만 말합니다.

## 녹화 결과 (2026-11-03)

`docs/video/scene0~4.mp4`(git 제외, 로컬), 스틸 `docs/video/stills/`. 장면표의 결과 문구가 로그와 모두 일치했다 (장면 1 "확인 1 / 평가 2", 장면 2 "확인 2 / 평가 2", 장면 3 "제외 2", 장면 4 실제 깜빡이로 확인 → 단계 0).

녹화하며 알게 된 것:

- **주행 제한.** 기어 D에서 플랫폼이 앱 위에 "주행 중에는 사용할 수 없습니다"를 띄운다. Activity에 `distractionOptimized=true` meta-data(Android Automotive 표준, Pleos 문서의 DO 선언)를 넣으면 풀린다. Pleos 가이드는 DO 앱이 `CarUxRestrictions`를 직접 확인하라고 하므로 실차 출시 전 대응 필요.
- **release 빌드는 run-as 불가.** 로그는 `adb logcat -s YeopjariProbe`, 기록 초기화는 `adb shell pm clear --user 10 dev.seobew.yeopjari`.
- 경로 요청을 누르면 내비 앱이 전면으로 올라온다. `am start -n dev.seobew.yeopjari/.CoachActivity` 뒤 BACK으로 복귀.
- 선택 화면의 "개발자 화면" 버튼 위치는 연습 기록 줄 수에 따라 내려간다 (uiautomator dump로 좌표 확인).
- 시나리오 ▶4·▶5 버튼은 가로 스크롤 뒤에 있다.

## 녹화 후

```bash
adb pull /sdcard/scene1.mp4 docs/video/   # 장면별
adb shell "run-as dev.seobew.yeopjari --user 10 cat files/probe.log" > docs/video/probe-$(date +%F).log
```
