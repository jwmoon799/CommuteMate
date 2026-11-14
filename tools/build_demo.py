#!/usr/bin/env python3
"""Build CommuteMate without Android Studio or an emulator running."""
from pathlib import Path
import datetime
import os
import subprocess
import sys

from demo_environment import PROJECT, java_directory
JBR = java_directory()
OUTPUTS = PROJECT / 'build/demo-logs'


def main():
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    if not (PROJECT / 'gradlew').is_file() or not (JBR / 'bin/java').is_file():
        raise RuntimeError('CommuteMate 또는 Android Studio의 Java 경로를 찾지 못했습니다.')
    env = dict(os.environ)
    env['JAVA_HOME'] = str(JBR)
    env['PATH'] = str(JBR / 'bin') + os.pathsep + env.get('PATH', '')
    base = [str(PROJECT / 'gradlew'), '-p', str(PROJECT)]
    print('기존 Gradle 대기 프로세스를 종료합니다.', flush=True)
    stop = subprocess.run(base + ['--stop'], env=env, timeout=60)
    if stop.returncode:
        raise RuntimeError('Gradle 종료 명령이 실패했습니다. 위 오류를 알려주세요.')
    log = OUTPUTS / ('CommuteMate_빌드_' + datetime.datetime.now().strftime('%Y%m%d_%H%M%S') + '.txt')
    command = base + [
        ':app:assembleDebug', '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain',
        '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8',
        '-Pkotlin.compiler.execution.strategy=in-process',
    ]
    print('빌드를 시작합니다. 빌드 JVM 힙 최대 1GB, 동시 작업 1개로 제한합니다.', flush=True)
    print('Pleos는 아직 실행하지 마세요. 로그: ' + str(log), flush=True)
    with log.open('w') as stream:
        process = subprocess.Popen(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        try:
            for line in process.stdout:
                print(line, end='', flush=True)
                stream.write(line)
                stream.flush()
            result = process.wait()
        except KeyboardInterrupt:
            process.terminate()
            process.wait()
            print('\n빌드를 중단했습니다. 로그는 보존됩니다.')
            return 130
    if result:
        print('\n빌드 실패. 저장된 로그로 원인을 확인하겠습니다.')
        print(log)
        return result
    apk = PROJECT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.is_file() or apk.stat().st_size == 0:
        raise RuntimeError('빌드 명령은 종료됐지만 예상 APK를 찾지 못했습니다. 로그를 확인해야 합니다.')
    print('\n완료: APK 빌드 성공')
    print(apk)
    print('Pleos 실행 전 채팅에 완료를 알려주세요.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print('빌드 중단: ' + str(error), file=sys.stderr)
        sys.exit(1)
