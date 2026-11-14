#!/usr/bin/env python3
"""Install the verified release APK; remove a conflicting debug app only on explicit flag."""
import argparse,datetime,hashlib,json,os,re,subprocess,sys,tempfile,time
from pathlib import Path
from demo_environment import PROJECT
from run_demo import SDK,ADB,SERIAL,OUTPUTS,adb,ensure_news_service
from sign_release import build_tools,command_env,run,validate_release
APP='com.example.commutemate'

def latest_apk():
    root=PROJECT/'build/submission'
    candidates=sorted(p for p in root.glob('*/CommuteMate-release.apk') if p.is_file() and not (p.parent/'DO_NOT_SUBMIT.txt').exists())
    if not candidates:raise RuntimeError('서명된 제출용 APK가 없습니다.')
    return candidates[-1]

def verify_apk(apk):
    apk=apk.resolve()
    if (apk.parent/'DO_NOT_SUBMIT.txt').exists():raise RuntimeError('실행 오류가 확인된 APK입니다. 수정 버전을 먼저 빌드·서명해주세요.')
    package=validate_release(apk)
    checksum=apk.parent/'checksums.sha256'
    expected=checksum.read_text().split()[0]
    if hashlib.sha256(apk.read_bytes()).hexdigest()!=expected:raise RuntimeError('제출용 APK 체크섬 불일치')
    report=run([build_tools()/'apksigner','verify','--print-certs',apk],command_env(),capture=True).decode()
    match=re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})',report)
    info=json.loads((PROJECT.parent/(PROJECT.name+'-signing')/'public-key-info.json').read_text())
    if not match or match.group(1).lower()!=info['certificate_sha256']:raise RuntimeError('제출 서명 인증서 불일치')
    return package,match.group(1).lower()

def ensure_emulator():
    devices=adb('devices')
    lines=[line.split() for line in devices.splitlines() if line.startswith('emulator-')]
    if any(row[0]!=SERIAL for row in lines):raise RuntimeError('다른 에뮬레이터가 실행 중입니다. 닫고 다시 실행해주세요.')
    process=None
    if not lines:
        log=OUTPUTS/('Release_에뮬레이터_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S')+'.txt')
        env=dict(os.environ);env.pop('QEMU_COREAUDIO_BUFFER_SIZE',None)
        with log.open('w') as stream:
            process=subprocess.Popen([str(SDK/'emulator/emulator'),'-avd',os.environ.get('COMMUTEMATE_AVD','Pleos_Connect_v2.1.0'),
                                      '-no-snapshot-load','-verbose'],env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
        print('차량 시스템을 시작합니다. 로그: '+str(log),flush=True)
    deadline=time.monotonic()+120;next_notice=time.monotonic()+10
    while time.monotonic()<deadline:
        if process and process.poll() is not None:raise RuntimeError('에뮬레이터가 종료됐습니다. 로그를 확인해주세요: '+str(log))
        try:
            if adb('-s',SERIAL,'shell','getprop','sys.boot_completed',timeout=5).strip()=='1':return
        except (RuntimeError,subprocess.TimeoutExpired):pass
        if time.monotonic()>=next_notice:print('차량 시스템 부팅 대기 중…',flush=True);next_notice=time.monotonic()+10
        time.sleep(2)
    raise RuntimeError('에뮬레이터 부팅 시간이 초과됐습니다. 아직 앱은 삭제하지 않았습니다.')

def install(apk,replace_debug):
    result=subprocess.run([ADB,'-s',SERIAL,'install','-r',str(apk)],capture_output=True,text=True,timeout=120)
    response=result.stdout+result.stderr
    if result.returncode==0 and 'Success' in response:return '기존 데이터 유지 설치 또는 신규 설치'
    if 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' not in response:raise RuntimeError(response.strip())
    if not replace_debug:raise RuntimeError('기존 앱과 서명이 다릅니다. 개발용 앱 데이터 초기화를 승인했다면 --replace-debug 옵션으로 실행해주세요.')
    dump=adb('-s',SERIAL,'shell','dumpsys','package',APP)
    if not re.search(r'(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b',dump):
        raise RuntimeError('기존 앱이 개발용(debuggable)임을 확인하지 못해 삭제하지 않았습니다.')
    print('승인된 개발용 앱 교체: 에뮬레이터의 앱 내부 데이터만 초기화합니다.',flush=True)
    if 'Success' not in adb('-s',SERIAL,'uninstall',APP,timeout=30):raise RuntimeError('기존 개발용 앱 삭제 실패')
    if 'Success' not in adb('-s',SERIAL,'install',str(apk),timeout=120):raise RuntimeError('제출용 APK 설치 실패. APK 파일은 보존되어 있습니다.')
    return '서명이 충돌한 개발용 앱 삭제 후 제출용 앱 설치'

def verify_running(timeout=20):
    deadline=time.monotonic()+timeout;last_pid="";stable=0
    while time.monotonic()<deadline:
        # pidof exits with status1 when the app has already crashed.
        result=subprocess.run([ADB,'-s',SERIAL,'shell','pidof',APP],capture_output=True,text=True,timeout=5)
        pid=result.stdout.strip()
        dump=adb('-s',SERIAL,'shell','dumpsys','activity','activities',timeout=5)
        marker='packageName='+APP+' processName='+APP
        block=dump.split(marker,1)[-1].split('\n  * Task',1)[0] if marker in dump else ''
        drawn='reportedDrawn=true' in block and 'reportedVisible=true' in block
        if pid and pid==last_pid and drawn:stable+=1
        else:stable=0
        if stable>=3:return pid
        last_pid=pid;time.sleep(.6)
    crash=adb('-s',SERIAL,'logcat','-b','crash','-d','-v','threadtime',timeout=10)
    path=OUTPUTS/('Release_실행실패_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S')+'.txt')
    path.write_text(crash)
    raise RuntimeError('앱 프로세스와 실제 화면 표시를 확인하지 못했습니다. 실행 완료로 처리하지 않습니다. 오류 로그: '+str(path))

def main():
    parser=argparse.ArgumentParser(description='제출용 APK 검증 및 설치. --replace-debug일 때만 서명 충돌 개발용 앱 삭제.')
    parser.add_argument('--apk',type=Path)
    parser.add_argument('--replace-debug',action='store_true')
    parser.add_argument('--check-apk',action='store_true',help='파일만 확인하고 에뮬레이터·앱은 변경하지 않음')
    args=parser.parse_args();apk=args.apk or latest_apk()
    package,expected=verify_apk(apk)
    print('제출 APK 파일·서명·경고 음성 검증 완료\n'+str(apk),flush=True)
    if args.check_apk:return
    OUTPUTS.mkdir(parents=True,exist_ok=True)
    ensure_emulator()
    adb('-s',SERIAL,'forward','tcp:8088','tcp:8088')
    adb('-s',SERIAL,'reverse','tcp:8091','tcp:8091')
    ensure_news_service()
    outcome=install(apk,args.replace_debug)
    paths=adb('-s',SERIAL,'shell','pm','path',APP).splitlines()
    base=next((line.removeprefix('package:') for line in paths if line.endswith('/base.apk')),None)
    if not base:raise RuntimeError('설치된 APK 경로를 확인하지 못했습니다.')
    with tempfile.TemporaryDirectory(prefix='release-installed-',dir=OUTPUTS) as tmp:
        installed=Path(tmp)/'installed.apk'
        adb('-s',SERIAL,'pull',base,str(installed),timeout=60)
        cert=run([build_tools()/'apksigner','verify','--print-certs',installed],command_env(),capture=True).decode()
        if expected not in cert:raise RuntimeError('설치된 앱의 서명 인증서가 제출용과 다릅니다.')
        validate_release(installed)
    adb('-s',SERIAL,'shell','am','force-stop',APP)
    try:
        launch=adb('-s',SERIAL,'shell','am','start','-W','-n',APP+'/.MainActivity',timeout=25)
    except subprocess.TimeoutExpired:
        launch='앱 시작 요청 응답 시간 초과. 실제 프로세스·화면 상태로 추가 확인합니다.'
    pid=verify_running()
    note=OUTPUTS/('Release_설치검증_'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S')+'.txt')
    note.write_text('APK: '+str(apk)+'\n'+package+'\n'+outcome+'\n설치된 APK의 서명/비debuggable/경고 자산 일치 확인.\n'+launch+
                    '\nPID '+pid+' 유지 및 실제 앱 화면 reportedDrawn/reportedVisible 확인. 음질·코스 진행·차량 SDK 권한은 실제 사용자 확인 필요.\n')
    print(launch.strip(),flush=True)
    print('제출용 APK 설치·서명·앱 화면 표시 확인 완료\n로그: '+str(note),flush=True)
    print('환영 화면이 나오는지 확인해주세요. 앱 실제 기능은 다음으로 점검합니다.',flush=True)
if __name__=='__main__':
    try:main()
    except KeyboardInterrupt:print('\n설치 작업을 중단했습니다.',file=sys.stderr);sys.exit(130)
    except Exception as e:print('설치 작업 중단: '+str(e),file=sys.stderr);sys.exit(1)
