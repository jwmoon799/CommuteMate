#!/usr/bin/env python3
"""Create a private release key, then build/align/sign/verify without saving passwords."""
import argparse,datetime,getpass,hashlib,json,os,re,shutil,subprocess,sys,tempfile,zipfile
from pathlib import Path
from demo_environment import PROJECT,sdk_directory,java_directory
ALIAS='commutemate'
DEFAULT_KEY=PROJECT.parent/(PROJECT.name+'-signing')/'commutemate-release.keystore'
PASSWORD_ENV='COMMUTEMATE_RELEASE_SIGNING_PASSWORD'

def command_env(password=None):
    env=dict(os.environ);java=java_directory()
    env['JAVA_HOME']=str(java);env['PATH']=str(java/'bin')+os.pathsep+env.get('PATH','')
    if password is not None:env[PASSWORD_ENV]=password
    return env

def run(args,env=None,capture=False):
    r=subprocess.run([str(x) for x in args],env=env,stdout=subprocess.PIPE if capture else None,
                     stderr=subprocess.PIPE if capture else None)
    if r.returncode:
        # No command expansion or environment values are logged.
        detail=r.stderr.decode(errors='replace').strip() if capture else '위의 도구 오류를 확인해주세요.'
        raise RuntimeError(detail or '서명 도구 실행 실패')
    return r.stdout if capture else b''

def public_certificate(key,password):
    data=run([java_directory()/'bin/keytool','-exportcert','-keystore',key,'-alias',ALIAS,
              '-storepass:env',PASSWORD_ENV],command_env(password),capture=True)
    if not data or data[0]!=0x30:raise RuntimeError('공개 인증서를 읽지 못했습니다.')
    return data,hashlib.sha256(data).hexdigest()

def generate_key(key,password):
    if key.exists():raise RuntimeError('이미 있는 서명키를 덮어쓰지 않습니다: '+str(key))
    key.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    with tempfile.TemporaryDirectory(prefix='.key-',dir=key.parent) as tmp:
        staged=Path(tmp)/'release.keystore'
        run([java_directory()/'bin/keytool','-genkeypair','-keystore',staged,'-storetype','PKCS12',
             '-alias',ALIAS,'-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA',
             '-validity','10000','-dname','CN=CommuteMate','-storepass:env',PASSWORD_ENV,
             '-keypass:env',PASSWORD_ENV,'-noprompt'],command_env(password),capture=True)
        certificate,digest=public_certificate(staged,password)
        staged.chmod(0o600)
        # Link fails if a key appeared since the initial check: never replace a private key.
        os.link(staged,key)
    (key.parent/'commutemate-release-public.cer').write_bytes(certificate)
    (key.parent/'public-key-info.json').write_text(json.dumps({
        'alias':ALIAS,'format':'PKCS12','subject':'CN=CommuteMate','algorithm':'RSA 3072',
        'certificate_sha256':digest,'created':datetime.datetime.now().isoformat(timespec='seconds')
    },ensure_ascii=False,indent=2))
    return digest

def build_tools():
    directory=sdk_directory()/'build-tools'
    candidates=[p for p in directory.iterdir() if p.is_dir() and re.fullmatch(r'\d+(\.\d+)+',p.name)]
    for p in sorted(candidates,key=lambda x:tuple(map(int,x.name.split('.'))),reverse=True):
        if all((p/name).is_file() for name in ('apksigner','zipalign','aapt')):return p
    raise RuntimeError('Android SDK Build Tools의 apksigner/zipalign/aapt가 필요합니다.')

def sign_and_verify(unsigned,signed,key,password):
    tools=build_tools();env=command_env(password)
    with tempfile.TemporaryDirectory(prefix='align-',dir=signed.parent) as tmp:
        aligned=Path(tmp)/'aligned.apk'
        run([tools/'zipalign','-P','16','-f','4',unsigned,aligned],env)
        run([tools/'apksigner','sign','--ks',key,'--ks-key-alias',ALIAS,
             '--ks-pass','env:'+PASSWORD_ENV,'--key-pass','env:'+PASSWORD_ENV,
             '--v4-signing-enabled','false','--out',signed,aligned],env)
    report=run([tools/'apksigner','verify','--verbose','--print-certs',signed],env,capture=True).decode()
    run([tools/'zipalign','-c','-P','16','4',signed],env)
    _,expected=public_certificate(key,password)
    actual=re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})',report)
    if not actual or actual.group(1).lower()!=expected:raise RuntimeError('APK 인증서가 선택한 서명키와 일치하지 않습니다.')
    return report,expected

def validate_release(apk):
    badging=run([build_tools()/'aapt','dump','badging',apk],command_env(),capture=True).decode()
    if 'application-debuggable' in badging:raise RuntimeError('debuggable APK는 제출용 결과로 내보내지 않습니다.')
    if "package: name='com.example.commutemate'" not in badging:raise RuntimeError('다른 앱의 APK입니다.')
    with zipfile.ZipFile(apk) as z:
        for name in ('lead_departure','attention'):
            source=PROJECT/'app/src/main/assets/safety'/(name+'.wav')
            if hashlib.sha256(z.read('assets/safety/'+name+'.wav')).digest()!=hashlib.sha256(source.read_bytes()).digest():
                raise RuntimeError('안전 안내 음성 불일치: '+name)
    return next(line for line in badging.splitlines() if line.startswith('package:'))

def build_release():
    adb=sdk_directory()/'platform-tools/adb'
    devices=run([adb,'devices'],command_env(),capture=True).decode()
    if any(line.startswith('emulator-') for line in devices.splitlines()):
        raise RuntimeError('빌드 전에 차량 에뮬레이터와 Android Studio를 닫아주세요. 3D 실행 터미널도 Control+C로 종료해주세요.')
    run([sys.executable,PROJECT/'tools/generate_safety_audio.py','--verify-only'])
    print('release 빌드 시작: 메모리1GB/작업1개. 앱을 실행하거나 기존 앱을 삭제하지 않습니다.',flush=True)
    # No password enters the Gradle process or configuration cache.
    env=command_env();env.pop(PASSWORD_ENV,None)
    run([PROJECT/'gradlew','-p',PROJECT,':app:assembleRelease','--no-daemon','--no-parallel',
         '--no-configuration-cache','--max-workers=1','--console=plain',
         '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8',
         '-Pkotlin.compiler.execution.strategy=in-process'],env)
    apk=PROJECT/'app/build/outputs/apk/release/app-release-unsigned.apk'
    if not apk.is_file():raise RuntimeError('예상한 unsigned release APK가 없습니다.')
    return apk

def ask_password(confirm=False):
    if not sys.stdin.isatty():raise RuntimeError('비밀번호는 macOS 터미널에서 직접 입력해주세요. 파일/파이프로 입력받지 않습니다.')
    password=getpass.getpass('서명키 비밀번호 입력 (화면에 표시되지 않습니다): ')
    if len(password)<8:raise RuntimeError('8자 이상의 비밀번호를 사용해주세요.')
    if confirm and password!=getpass.getpass('같은 비밀번호를 다시 입력: '):raise RuntimeError('비밀번호가 일치하지 않습니다. 키를 생성하지 않았습니다.')
    return password

def main():
    parser=argparse.ArgumentParser(description='CommuteMate release 서명. 비밀번호는 터미널에서만 입력하며 저장하지 않습니다.')
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--create-key',action='store_true')
    group.add_argument('--build-and-sign',action='store_true')
    parser.add_argument('--keystore',type=Path,default=DEFAULT_KEY)
    args=parser.parse_args();key=args.keystore.expanduser().resolve()
    if args.create_key:
        if key.exists():raise RuntimeError('서명키가 이미 있습니다. 새로 만들거나 덮어쓰지 않습니다: '+str(key))
        print('새 서명키를 만듭니다. 비밀번호는 기록하지 않으므로 따로 보관해주세요.',flush=True)
        digest=generate_key(key,ask_password(confirm=True))
        print('서명키 생성 완료\n키 위치: '+str(key)+'\n별칭: '+ALIAS+'\n공개 인증서 SHA256: '+digest)
        print('키 파일과 비밀번호는 향후 같은 앱을 업데이트할 때 필요합니다. 제출 ZIP이나 Git에 넣지 마세요.')
        return
    if not key.is_file():raise RuntimeError('먼저 --create-key로 서명키를 만들어주세요.')
    unsigned=build_release();package=validate_release(unsigned)
    password=ask_password()
    # Verify the password before creating a submission directory.
    public_certificate(key,password)
    output=PROJECT/'build/submission'/datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
    output.mkdir(parents=True,exist_ok=False)
    signed=output/'CommuteMate-release.apk'
    try:
        report,digest=sign_and_verify(unsigned,signed,key,password)
        validate_release(signed)
    except Exception:
        if signed.exists():signed.unlink()
        raise
    sha=hashlib.sha256(signed.read_bytes()).hexdigest()
    (output/'signature-verification.txt').write_text(package+'\n'+report+'\nAPK SHA256: '+sha+'\n')
    (output/'checksums.sha256').write_text(sha+'  '+signed.name+'\n')
    print('제출용 APK 서명·검증 완료\n'+str(signed)+'\nAPK SHA256: '+sha)
    print('앱 설치·실행 검증은 다음 단계입니다. 기존 debug 앱은 자동 삭제하지 않았습니다.')

if __name__=='__main__':
    try:main()
    except (KeyboardInterrupt,EOFError):print('\n서명 작업을 중단했습니다.',file=sys.stderr);sys.exit(130)
    except Exception as e:print('서명 작업 중단: '+str(e),file=sys.stderr);sys.exit(1)
