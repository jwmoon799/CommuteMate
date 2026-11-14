# CommuteMate · 커뮤트 메이트

**팀명: 119기 · 앱 이름: CommuteMate**

Pleos Connect에서 업무 브리핑, 경제 뉴스, 영어 듣기·따라 말하기를 제공하는 출근 도우미 프로토타입입니다.

## 포함된 구성

| 구성 | 코드 | 역할 |
| --- | --- | --- |
| Android 앱 | `app/` | 코스 선택, 오디오 재생, 마이크 음파, 복습·도착 요약, Vehicle SDK 연동 |
| 실시간 뉴스 서버 | `tools/news_service.py` | 최신 경제 RSS 조회, 공개 기사 본문 추출, macOS 음성 생성 |
| 3D 주행 화면 | `tools/driving_demo/` | 직진·차선 이동·좌우 회전과 안전 경고 시연 |
| 차량 연결 브리지 | `tools/drive_bridge.py` | Pleos 콘솔 신호와 3D 연결, 회전 후 에뮬레이터 깜빡이 OFF |
| 실행·서명 도구 | `tools/` | 환경 점검, 저메모리 빌드, 실행, APK 서명·검증 |
| 팀 제작 자료 | `demo/` | 음성 원본, 편집 프로젝트, 영상·자막 |

## 실행 환경

- macOS, Python 3, Android Studio 내장 Java 및 Android SDK.
- SDK compile/target 37, min 28. Gradle Wrapper를 사용합니다.
- 검증한 에뮬레이터: **Pleos Connect v2.1.0**, Android 34 Pleos Car Emulator, ARM64 (`arm64-v8a`).
- 에뮬레이터 전체 화면 규격: **2560 × 1440 px**, **160 dpi**, 가로 화면. 차량 기본 UI와 앱이 함께 표시되므로 앱 영역은 전체 화면보다 작습니다.
- Pleos Connect Emulator와 Vehicle SDK 저장소 접근이 필요합니다. 기본 AVD 이름은 `Pleos_Connect_v2.1.0`입니다.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` 또는 자신의 `local.properties`로 SDK 위치를 지정합니다. AVD 이름 변경은 `COMMUTEMATE_AVD`로 지정합니다.
- 뉴스 음성 생성에는 macOS `say`의 Yuna 음성과 `afconvert`를 사용합니다. Windows/Linux는 검증하지 않았습니다.
- 8GB 메모리에서는 빌드할 때 Android Studio·에뮬레이터·3D 창을 닫아 주세요.

## 처음 실행

이 저장소 폴더에서:

```bash
python3 tools/check_demo.py
python3 tools/run_demo.py
```

첫 빌드는 의존성을 다운로드합니다. 실행기가 빌드, 에뮬레이터 부팅, 뉴스 서버, APK 설치와 앱 실행을 진행합니다. 실행 완료 후 차량 홈만 보이면:

```bash
"${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" -s emulator-5554 shell am start -W -n com.example.commutemate/.MainActivity
```

새 터미널에서 같은 저장소 폴더로 이동해 3D를 실행합니다.

```bash
python3 tools/run_drive.py
```

- 3D: http://127.0.0.1:8091/
- 차량 조작 콘솔: http://127.0.0.1:8088/
- 뉴스 서버: http://127.0.0.1:8092/health

3D 터미널을 유지하고 ‘차량 신호 연결됨’을 확인합니다. 두 창은 별도 화면입니다. APK만 설치하면 Mac 보조 서버와 3D까지 자동 배포되는 구조는 아닙니다.

## 동작과 검증 범위

- 영어 중심 코스: 브리핑 → 8문항 듣기·두 번 말하기 → 두 표현 복습.
- 뉴스 포함 코스: 브리핑 → 최신 뉴스 2편 전체 본문 → 영어 4문항 → 두 표현 복습.
- 복습 후 `시연: 도착·주차`로 도착 요약에 이동합니다. 복습 완료가 실제 차량 도착을 의미하지 않습니다.
- 뉴스는 최신순이며 인기 랭킹이 아닙니다. 본문 확보 실패 시 짧은 요약을 전체 기사로 대체하지 않습니다. 외부 제공 페이지 구조·연결 상태에 영향을 받습니다.
- 긴 뉴스 제목은 줄바꿈하고 본문은 스크롤됩니다. 뉴스 길이에 따라 표시된 계획 시간을 넘을 수 있습니다.
- LEFT/RIGHT에서 재생 정지, OFF에서 정지 지점부터 재개: 실제 **에뮬레이터 Vehicle SDK 신호** 연동입니다.
- 3D 좌우 회전·차선 이동·회전 후 깜빡이 자동 OFF: **가상 시연**이며 실제 차량을 제어하지 않습니다.
- 앞차 출발/전방 주시 경고: **모의 이벤트**입니다. 실제 HUD·앞차 센서·동공 추적과 연결되지 않았습니다.
- 경고 시 학습·마이크를 정지하며 확인 전에는 재개하지 않습니다. 깜빡이 OFF가 안전 경고를 해제하지 않습니다.
- 일정·출근 경로는 샘플입니다. 마이크 음파는 발화 반응이며 발음 정확도 채점이 아닙니다.
- 실제 주행 중 화면 사용 제한을 우회하지 않습니다. 시연은 에뮬레이터 기어 P 상태에서 진행합니다.

2026-11-14, 버전 **1.0.2 (3)**: 사용자 환경에서 영어 코스, 뉴스 두 편의 마지막 문장까지 재생, 영어 1/4 자동 전환, 복습·도착 요약, 깜빡이 연동, 두 안전 경고를 확인했습니다.

## 검사

```bash
python3 tools/test_news_fulltext.py
python3 tools/generate_safety_audio.py --verify-only
```

전체 실행·문제 해결·서명 안내는 [README.txt](README.txt)를 확인하세요.

## 제출 APK와 서명

제출 파일: [`apk/119기_CommuteMate_release.apk`](apk/119기_CommuteMate_release.apk)

- [APK 직접 다운로드](https://github.com/jwmoon799/CommuteMate/raw/refs/heads/main/apk/119%EA%B8%B0_CommuteMate_release.apk)
- [SHA-256 체크섬](apk/checksums.sha256) / [서명 검증 결과](apk/signature-verification.txt)
- [기존 v1.0.2 GitHub Release](https://github.com/jwmoon799/CommuteMate/releases/tag/v1.0.2): 동일한 APK가 기존 파일명으로 보관되어 있습니다.
- 버전: **1.0.2**, versionCode **3**. 실행 가능한 release APK이며 기존 팀 키로 서명했습니다.
- 패키지: `com.example.commutemate`. Pleos 호환 환경에서 사용합니다.
- SHA-256: `0ed77e6d61c593c17706183a4256bb797781fee306e2f669202d945e83e7a718`.

APK 다운로드 후 설치만으로 업무·영어의 포함된 음성은 사용할 수 있습니다. **실시간 뉴스와 3D 연동은 위 실행 절차로 Mac 보조 서버도 실행해야 합니다.** 앱 서명키와 비밀번호, 캐시·빌드 출력·기기 내부 자료는 저장소에 포함하지 않습니다.
팀에서 기존 키를 보유한 경우 같은 키로 업데이트해야 합니다. 공유받은 팀원은 개발용 빌드로 실행할 수 있습니다.

```bash
python3 tools/rebuild_release_demo.py
```

위 명령은 키를 이미 생성한 원래 개발 환경에서 사용합니다. 비밀번호는 터미널에서만 입력하며 GitHub에 올리지 않습니다.

## 콘텐츠 출처

뉴스는 뉴시스 경제 RSS 및 공개 기사 페이지에서 실행 시 조회합니다. 기사 본문·생성한 최신 뉴스 캐시는 이 저장소에 포함하지 않습니다.
음성·영상은 팀 제작 자료이며, 외부 SDK와 의존성은 각 제공자의 조건을 따릅니다. 이 저장소의 공개 라이선스는 별도로 지정하지 않았습니다.
