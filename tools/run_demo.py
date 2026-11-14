#!/usr/bin/env python3
"""Build, start the existing Pleos AVD, install, and launch with Studio closed."""
from pathlib import Path
import datetime
import os
import signal
import subprocess
import sys
import time
import urllib.request
import json
import zipfile

OUTPUTS = Path(__file__).resolve().parents[1] / 'build/demo-logs'
from demo_environment import PROJECT, sdk_directory
SDK = sdk_directory()
ADB = str(SDK / 'platform-tools/adb')
SERIAL = 'emulator-5554'


def adb(*args, timeout=12):
    result = subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr).strip())
    return result.stdout


def ensure_news_service():
    def healthy():
        try:
            with urllib.request.urlopen('http://127.0.0.1:8092/health', timeout=1) as response:
                health = json.load(response)
                if health.get('service') != 'commutemate-live-news':
                    raise RuntimeError('8092 포트를 다른 서비스가 사용하고 있습니다.')
                return health.get('version', 1) >= 2
        except RuntimeError:
            raise
        except Exception:
            return False
    if not healthy():
        # Replace only this user's recognized legacy news server; preserve all other apps.
        try:
            with urllib.request.urlopen('http://127.0.0.1:8092/health', timeout=2) as response:
                old = json.load(response)
        except Exception:
            old = None
        if old and old.get('service') == 'commutemate-live-news' and old.get('version', 1) < 2:
            result = subprocess.run(['/usr/sbin/lsof','-t','-a','-iTCP:8092','-sTCP:LISTEN','-u',str(os.getuid())],
                capture_output=True,text=True,check=False)
            pids = {int(value) for value in result.stdout.split() if value.isdigit()}
            if len(pids) != 1: raise RuntimeError('이전 뉴스 서버를 하나로 확인하지 못했습니다.')
            os.kill(next(iter(pids)), signal.SIGTERM)
            for _ in range(30):
                try:
                    with urllib.request.urlopen('http://127.0.0.1:8092/health',timeout=.3): pass
                except Exception: break
                time.sleep(.1)
            else: raise RuntimeError('이전 뉴스 서버가 아직 종료되지 않았습니다.')
        log = OUTPUTS / ('News_서비스_' + datetime.datetime.now().strftime('%Y%m%d_%H%M%S') + '.txt')
        with log.open('w') as stream:
            process = subprocess.Popen([sys.executable, str(PROJECT / 'tools/news_service.py')],
                stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        for _ in range(30):
            if healthy(): break
            if process.poll() is not None: raise RuntimeError('뉴스 서버 시작 실패. 로그: ' + str(log))
            time.sleep(.2)
        else: raise RuntimeError('뉴스 서버 응답 확인 실패. 로그: ' + str(log))
    adb('-s', SERIAL, 'reverse', 'tcp:8092', 'tcp:8092')
    print('최신 경제 뉴스 연결을 준비했습니다. 앱에서 뉴스 코스를 고르면 자료를 가져옵니다.', flush=True)


def main():
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    if sys.argv[1:] not in ([], ['--run-only']):
        raise RuntimeError('지원하는 옵션: --run-only')
    run_only = sys.argv[1:] == ['--run-only']
    if any(line.startswith('emulator-') for line in adb('devices').splitlines()):
        raise RuntimeError('Pleos 창을 먼저 닫은 뒤 실행해주세요. Android Studio도 닫아두세요.')
    if run_only:
        apk = PROJECT / 'app/build/outputs/apk/debug/app-debug.apk'
        if not apk.is_file() or apk.stat().st_size == 0:
            raise RuntimeError('기존 APK가 없습니다. 먼저 빌드가 필요합니다.')
        print('[1/4] 이미 빌드된 APK를 사용합니다.', flush=True)
    else:
        print('[1/4] 메모리 사용량을 제한해 APK를 빌드합니다.', flush=True)
        result = subprocess.run([sys.executable, str(PROJECT / 'tools/build_demo.py')])
        if result.returncode:
            return result.returncode
    # Never install a new safety build without its two real warning recordings.
    apk = PROJECT / 'app/build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(apk) as package:
        for name in ('assets/safety/lead_departure.wav', 'assets/safety/attention.wav'):
            if name not in package.namelist() or package.getinfo(name).file_size < 20000:
                raise RuntimeError('안전 안내 음성이 APK에 없습니다. python3 tools/generate_safety_audio.py 실행 후 새로 빌드해주세요.')
    print('[2/4] Pleos를 실행합니다. Android Studio는 필요하지 않습니다.', flush=True)
    log = OUTPUTS / ('Pleos_통합실행_' + datetime.datetime.now().strftime('%Y%m%d_%H%M%S') + '.txt')
    env = dict(os.environ)
    env.pop('QEMU_COREAUDIO_BUFFER_SIZE', None)
    with log.open('w') as stream:
        stream.write('Default emulator audio; experimental QEMU_COREAUDIO_BUFFER_SIZE override removed.\n')
        stream.flush()
        emulator = subprocess.Popen([
            str(SDK / 'emulator/emulator'), '-avd', os.environ.get('COMMUTEMATE_AVD', 'Pleos_Connect_v2.1.0'),
            '-no-snapshot-load', '-verbose'
        ], env=env, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
    deadline = time.monotonic() + 120
    next_notice = time.monotonic() + 10
    booted = False
    while time.monotonic() < deadline:
        if emulator.poll() is not None:
            raise RuntimeError('Pleos가 종료됐습니다. 실행 로그: ' + str(log))
        try:
            booted = adb('-s', SERIAL, 'shell', 'getprop', 'sys.boot_completed', timeout=5).strip() == '1'
        except (RuntimeError, subprocess.TimeoutExpired):
            booted = False
        if booted:
            break
        if time.monotonic() >= next_notice:
            print('차량 시스템 부팅을 기다리고 있습니다…', flush=True)
            next_notice = time.monotonic() + 10
        time.sleep(2)
    if not booted:
        raise RuntimeError('부팅 확인 시간이 지났습니다. 창과 실행 로그를 확인해주세요: ' + str(log))
    adb('-s', SERIAL, 'forward', 'tcp:8088', 'tcp:8088')
    adb('-s', SERIAL, 'reverse', 'tcp:8091', 'tcp:8091')
    ensure_news_service()
    print('[3/4] APK를 설치합니다.', flush=True)
    apk = PROJECT / 'app/build/outputs/apk/debug/app-debug.apk'
    result = adb('-s', SERIAL, 'install', '-r', str(apk), timeout=90)
    if 'Success' not in result:
        raise RuntimeError('설치 성공을 확인하지 못했습니다: ' + result)
    print('[4/4] CommuteMate를 실행합니다.', flush=True)
    print(adb('-s', SERIAL, 'shell', 'am', 'start', '-W', '-n', 'com.example.commutemate/.MainActivity', timeout=30).strip())
    print('\n완료: 빌드·설치·앱 실행 명령을 마쳤습니다.', flush=True)
    print('차량 화면만 보이면 README의 앱 화면 열기 항목을 확인해주세요.', flush=True)
    print('이 터미널 창은 명령 입력 상태로 돌아가며, Pleos는 계속 실행됩니다.', flush=True)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print('\n대기를 중단했습니다. Pleos가 켜져 있다면 창을 닫아 종료할 수 있습니다.')
        sys.exit(130)
    except Exception as error:
        print('진행 중단: ' + str(error), file=sys.stderr)
        sys.exit(1)
