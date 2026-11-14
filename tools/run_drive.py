#!/usr/bin/env python3
"""Open the linked drive scene; replace only a recognized older local bridge."""
from pathlib import Path
import json,os,signal,subprocess,sys,time,urllib.request,urllib.error,webbrowser,http.client
from demo_environment import PROJECT, sdk_directory
ADB=str(sdk_directory()/'platform-tools/adb')
SERIAL='emulator-5554'
URL='http://127.0.0.1:8091/'

def read(path):
    try:
        with urllib.request.urlopen(URL+path,timeout=4) as response:return json.load(response)
    except (ConnectionResetError,ConnectionAbortedError,http.client.RemoteDisconnected,TimeoutError) as error:
        raise RuntimeError('3D 서버가 연결을 정리하고 있습니다.') from error

def bridge_status():
    try:
        health=read('api/health')
        if health.get('service')!='commutemate-drive':raise RuntimeError('8091 포트를 다른 프로그램이 사용하고 있습니다.')
        return 'current' if health.get('bridgeVersion',0)>=3 else 'old'
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
        state=read('api/state')
        if state.get('emulatorOnly') is not True or 'revision' not in state:
            raise RuntimeError('기존 서버가 CommuteMate인지 확인할 수 없습니다. 자동 종료하지 않습니다.')
        return 'old'
    except urllib.error.URLError as error:
        if isinstance(error.reason,ConnectionRefusedError):return 'none'
        raise RuntimeError('3D 서버가 연결을 정리하고 있습니다.') from error
    except (ConnectionResetError,ConnectionAbortedError,http.client.RemoteDisconnected,TimeoutError) as error:
        raise RuntimeError('3D 서버가 연결을 정리하고 있습니다.') from error

def stop_old_bridge():
    # Only this user's unique listener, after recognizing the legacy bridge response.
    result=subprocess.run(['/usr/sbin/lsof','-t','-a','-iTCP:8091','-sTCP:LISTEN','-u',str(os.getuid())],capture_output=True,text=True)
    pids={int(value) for value in result.stdout.split() if value.isdigit()}
    if len(pids)!=1:raise RuntimeError('기존 3D 서버를 하나로 확인하지 못해 종료하지 않았습니다.')
    os.kill(next(iter(pids)),signal.SIGTERM)
    for _ in range(30):
        try:
            if bridge_status()=='none':return
        except RuntimeError:
            pass
        time.sleep(.1)
    raise RuntimeError('기존 3D 서버 종료를 확인하지 못했습니다. 명령을 반복하지 말고 알려주세요.')

def main():
    result=subprocess.run([ADB,'-s',SERIAL,'forward','tcp:8088','tcp:8088'],timeout=15)
    if result.returncode==0:
        result=subprocess.run([ADB,'-s',SERIAL,'reverse','tcp:8091','tcp:8091'],timeout=15)
    if result.returncode:raise RuntimeError('에뮬레이터를 먼저 켜주세요.')
    for attempt in range(20):
        try:
            state=bridge_status();break
        except RuntimeError as error:
            if '연결을 정리' not in str(error) or attempt==19:raise
            time.sleep(.2)
    if state=='old':
        print('수정 전 3D 서버를 확인했습니다. 이 서버만 종료하고 새 버전으로 바꿉니다.',flush=True)
        stop_old_bridge();state='none'
    if state=='current':
        print('연결 유지 기능이 적용된 3D 서버를 사용합니다.',flush=True)
        webbrowser.open(URL);return 0
    process=subprocess.Popen([sys.executable,str(PROJECT/'tools/drive_bridge.py')])
    for _ in range(30):
        try:
            if bridge_status()=='current':break
        except RuntimeError:pass
        if process.poll() is not None:raise RuntimeError('연동 서버 시작 실패. 위 오류를 알려주세요.')
        time.sleep(.2)
    else:raise RuntimeError('연동 서버 응답 확인 실패')
    webbrowser.open(URL)
    print('수정된 연동 3D 창을 열었습니다. 이 터미널을 유지하세요. 종료: Control+C',flush=True)
    try:process.wait()
    except KeyboardInterrupt:
        if process.poll() is None:process.terminate()
        process.wait()
    return 0
if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:
        print('진행 중단:',str(error),file=sys.stderr)
        sys.exit(1)
