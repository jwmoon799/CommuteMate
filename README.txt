커뮤트 메이트 개발 및 시연 실행 안내
기준 2026-11-14

커뮤트 메이트는 업무 브리핑·경제 뉴스·영어 학습을 음성으로 제공하는 Pleos Connect 프로토타입입니다. 에뮬레이터의 방향지시등 신호에 따라 재생을 멈추고 이어갑니다. 별도 가상3D와 안전 경고 시연 기능도 있습니다.
현재 실행 환경은 macOS입니다. 뉴스와 안내 음성 생성에 macOS의 say·afconvert를 사용하므로 Windows/Linux 실행을 확인한 프로젝트가 아닙니다.

1 준비 사항
- Android Studio에서 필요한 Android SDK와 Pleos Connect Emulator를 설치하고 Pleos AVD를 생성합니다.
- 현재 기본 AVD 이름은 Pleos_Connect_v2.1.0입니다. 다른 이름이면 COMMUTEMATE_AVD 환경변수를 설정합니다.
- Python 3, Android Studio의 빌드용 Java, SDK platform-tools/adb가 필요합니다.
- SDK는 ANDROID_HOME 또는 ANDROID_SDK_ROOT, local.properties의 sdk.dir, macOS 기본 ~/Library/Android/sdk 순서로 찾습니다.
- Java는 JAVA_HOME 또는 기본 위치의 Android Studio 내장 JBR을 사용합니다.
- SDK 패키지·에뮬레이터·기기는 자동 설치하지 않습니다. 첫 Gradle 빌드에는 의존성 다운로드가 필요하며 Pleos 저장소 접근이 가능해야 합니다.
- 마이크를 쓸 때는 macOS의 에뮬레이터 마이크 권한과 에뮬레이터의 마이크 입력 설정을 확인합니다.
- 메모리 8GB 환경에서는 빌드 중 Android Studio·에뮬레이터·3D를 닫아두는 방식을 사용합니다.

2 설치 환경만 점검
터미널에서 이 README가 있는 CommuteMate 폴더로 이동한 뒤 실행합니다.

python3 tools/check_demo.py

이 명령은 에뮬레이터를 켜거나 서버를 시작하지 않습니다. SDK/Java 위치, 안전 안내 WAV의 해시·형식, 기존 debug APK의 안내 음성 포함 여부를 확인합니다. SDK 권한·실제 차량 지원·앱 화면·음질의 검증은 별도입니다.

3 첫 빌드와 실행
안전 안내 음성은 app/src/main/assets/safety에 이미 들어 있습니다. 파일이 없거나 달라졌다면 아래 명령으로 생성합니다. 이 명령은 실제 macOS 터미널에서 실행합니다.

python3 tools/generate_safety_audio.py

차량 에뮬레이터와 Android Studio가 닫힌 상태에서:

python3 tools/run_demo.py

빌드→차량 에뮬레이터 부팅→뉴스 보조 서버→포트 연결→APK 설치→앱 실행 순서입니다. 빌드는 JVM 힙 최대1GB/동시작업1개 설정을 사용합니다. 완료 후 차량 에뮬레이터는 계속 실행됩니다.
앱을 빌드만 할 때:

python3 tools/build_demo.py

이미 최신 APK가 있으면 빌드 없이:

python3 tools/run_demo.py --run-only

run-only도 에뮬레이터가 닫힌 상태에서 시작하는 실행기입니다. 켜진 에뮬레이터를 강제로 종료하지 않습니다. 다른 맥에 APK를 복사하지 않았다면 최초 빌드가 필요합니다.

4 차량 화면만 나올 때
터미널의 설치·앱 실행 완료를 먼저 확인합니다. 앱 시작 요청이 끝난 뒤에도 차량 홈만 보이면 SDK platform-tools 폴더의 adb를 사용해 아래 명령을 한 번 실행합니다.

adb -s emulator-5554 shell am start -W -n com.example.commutemate/.MainActivity

adb가 PATH에 없으면 SDK/platform-tools/adb 전체 경로를 사용합니다. 명령을 반복해서 누르지 말고 오류와 build/demo-logs의 최신 로그를 확인합니다. 에뮬레이터 자체가 종료되면 같은 명령을 계속 반복하지 않습니다.

5 3D 연동
앱이 보이면 새 터미널에서 같은 CommuteMate 폴더로 이동하고 실행합니다.

python3 tools/run_drive.py

3D 주소 http://127.0.0.1:8091/
차량 조작 콘솔 http://127.0.0.1:8088/
뉴스 보조 서버 http://127.0.0.1:8092/

3D 실행 터미널을 유지합니다. ‘차량 신호 연결됨’을 확인한 뒤 좌우 회전이나 경고 시연을 실행합니다. 3D 소스 HTML을 파일로 직접 여는 방식은 앱 연동 실행을 대신하지 않습니다.
실행기는 8088 포워딩과 8091 역방향 연결을 설정합니다. 경고 시연은 해당 로컬 브리지로 전달됩니다. 다른 서비스가 이미 포트를 쓰면 임의로 종료하지 말고 원인을 확인합니다.
종료는 3D 실행 터미널에서 Control+C를 누른 후 차량 에뮬레이터 창을 닫습니다. 뉴스 보조 서버는 실행기에서 별도 프로세스로 유지하며 다음 실행 때 정상 서버를 재사용합니다.

6 기능과 시연 범위
- 준비3단계: 환영·코스선택→예상 출근시간→활동계획 확인.
- 영어 중심: 브리핑→8문항 듣기/두 번 말하기→두 표현 복습→도착 요약.
- 뉴스 포함: 브리핑→RSS 최신순 뉴스2편→영어4문항→복습→도착 요약.
- 23분 시간 배분은 계획이며 업무·영어 음성은 단축 시연용이며, 뉴스는 공개 기사 본문 전체를 재생하므로 실제 길이에 따라 계획 시간을 넘을 수 있습니다.
- 일정·경로는 샘플이며 실제 캘린더나 내비게이션 목적지/ETA 연동은 아닙니다.
- 음파는 마이크 발화 반응입니다. 발음 정확도 채점이나 자유 영어 대화가 아닙니다.
- 방향지시등 LEFT/RIGHT 정지, OFF 후 정지 지점 재개는 에뮬레이터 Vehicle SDK 신호 연동입니다.
- 3D 좌우 회전 후 에뮬레이터 방향지시등 자동 OFF는 가상 시연용이며 실제 차량을 제어하지 않습니다.
- 앞차 출발/전방 주시 안전 경고는 앱/3D 버튼에서 보내는 모의 이벤트입니다. 실제 앞차 센서·HUD 경고·동공 인식은 미연결입니다.
- 안전 경고는 학습 음성·마이크를 멈추고 안내합니다. 확인 전까지 정지하며 깜빡이 OFF가 안전 정지를 해제하지 않습니다. 기존 수동 일시정지도 유지합니다.
- 도착 요약에서는 새 안전 경고가 들어와도 경고 음성을 재생하지 않습니다.
- 앱은 에뮬레이터 기어P 상태로 시연합니다. 주행 중 화면 허용이나 실차 안전 검증을 완료한 앱이 아닙니다.
- 음성 메모/LLM 요약/스마트 노트는 보류 중이며 현재 구현 범위에 없습니다.

7 공유와 제출
- tools 실행기는 프로젝트 상대 위치와 각 맥의 SDK/Java 설정을 사용합니다. 기존 세션 outputs 폴더의 실행기도 그대로 보존되어 있습니다.
- local.properties, .gradle, .kotlin, build, 계정·환경변수 파일, 개인 서명키는 소스 공유 대상에서 제외합니다.
- APK 위치: app/build/outputs/apk/debug/app-debug.apk
- 현재 APK는 개발/시연용 debug APK입니다. 제출용 release 서명 APK를 만들었다고 표시하지 않습니다.
- release 서명키와 배포 설정, Git 저장소 연결·공개 여부는 별도 준비 항목입니다. 키와 비밀번호를 소스에 직접 넣지 않습니다.
- 인터넷이 없는 환경에서 뉴스와 첫 의존성 다운로드는 동작을 보장하지 않습니다. 영어 사전 녹음과 보유한 로컬 음성은 별도 사용합니다.

8 검증 상태
앱 코스 진행, 방향지시등 정지/재개, 복습/도착 요약, 두 안전 경고, 수동 정지 유지, 경고 중 자동OFF 유지, 말하기 중단·동일 문장 재개, 도착 후 경고 무시는 기존 환경에서 사용자 확인을 마쳤습니다.
프로젝트 내부 실행기는 경로 설정과 기존 실행 흐름을 정리한 새 진입점입니다. 파일 점검과 설정 탐색 검사를 수행했으며 다른 맥에서의 실제 설치/주행 시연은 아직 확인하지 않았습니다. 제출 전 깨끗한 환경의 빌드 및 앱/3D 실행 확인이 필요합니다.

9 제출용 release APK 서명
최초 한 번 새 서명키 생성:

python3 tools/sign_release.py --create-key

터미널에서 비밀번호를 두 번 직접 입력합니다. 입력 문자는 화면에 표시되지 않습니다. 비밀번호는 소스나 파일에 저장하지 않습니다. 키는 프로젝트 폴더 바깥의 같은 상위 폴더/CommuteMate-signing/commutemate-release.keystore에 저장합니다. 별칭은 commutemate, 형식 PKCS12, RSA3072, 유효기간10000일입니다. 이미 존재하는 키는 덮어쓰지 않습니다. 별도 위치에 기존 키를 두었다면 --keystore 경로 옵션을 사용합니다(현재 별칭 commutemate 기준).
키 파일과 비밀번호는 같은 앱의 후속 업데이트에 필요하므로 팀이 별도로 보관합니다. 프로젝트 공유 ZIP과 제출 자료에는 넣지 않습니다.

그다음 차량 에뮬레이터·Android Studio·3D 실행기를 종료하고:

python3 tools/sign_release.py --build-and-sign

release 빌드 후 비밀번호를 다시 입력하면 zipalign→apksigner 서명→서명/인증서일치/정렬/debuggable여부/안전안내 자산 검증을 수행합니다. 결과는 build/submission/생성시각/CommuteMate-release.apk와 검증 텍스트·체크섬입니다. 비밀번호는 keytool/apksigner의 환경변수 참조로만 전달하고 Gradle에는 전달하지 않습니다.
스크립트는 기존 앱을 지우거나 새 APK를 설치·외부 제출하지 않습니다. 기존 debug 앱과 release 서명이 다르므로 같은 package의 덮어쓰기 설치는 실패할 수 있습니다. debug 앱 삭제가 필요하면 먼저 기존 앱 데이터 보존 여부를 확인해야 합니다. 서명만 성공했다고 실차 지원·릴리스 실행 검증까지 마쳤다고 표시하지 않습니다.
공식 참고: https://developer.android.com/studio/publish/app-signing
https://developer.android.com/tools/apksigner
https://developer.android.com/tools/zipalign

10 release 1.0 시작 오류 수정
초기 제출 후보1.0은 부분 R8 최적화로 Kotlin IllegalAccessError가 발생해 제출 금지로 보관했습니다. 1.0.1에서는 해당 최적화를 해제했습니다. 설치 실행기는 실제 프로세스와 화면이 표시되는지도 확인합니다. 수정본을 기존 키로 재서명하고 설치하려면 python3 tools/rebuild_release_demo.py를 실행합니다. 비밀번호는 터미널에서 직접 입력하며 기존 서명키를 다시 만들지 않습니다. 1.0.2에서 기사 전체 본문 읽기와 긴 제목/본문 표시를 수정했습니다. 2026-11-14 사용자 검증: 영어 코스, 뉴스 2편 전체 재생 후 영어4문항/복습, 도착 요약, 깜빡이 정지/재개, 두 모의 안전 경고 확인 완료. 도착 요약 이동은 시연: 도착·주차 버튼으로 수행합니다.
