#!/usr/bin/env python3
"""Rebuild, ask for the existing key password, sign, and verify actual launch."""
from pathlib import Path
import subprocess,sys
TOOLS=Path(__file__).resolve().parent

def main():
    print('수정 release APK를 기존 서명키로 빌드·서명한 뒤 설치합니다. 새 서명키를 만들지 않습니다.',flush=True)
    result=subprocess.run([sys.executable,str(TOOLS/'sign_release.py'),'--build-and-sign'])
    if result.returncode:return result.returncode
    # Same certificate as the currently installed release; never delete it on failure.
    result=subprocess.run([sys.executable,str(TOOLS/'install_release_demo.py')])
    if result.returncode:return result.returncode
    import install_release_demo
    latest=install_release_demo.latest_apk()
    (latest.parent/'RUNTIME_CHECKED.txt').write_text('서명 APK 설치 후 프로세스 유지 및 실제 화면 reportedDrawn/reportedVisible 확인.\n음질·학습·마이크·차량 연동은 사용자 별도 확인 필요.\n')
    print('수정 제출용 앱의 서명·설치·화면 표시 확인 완료. 환영 화면을 확인해주세요.',flush=True)
    return 0
if __name__=='__main__':
    try:sys.exit(main())
    except KeyboardInterrupt:print('\n작업을 중단했습니다. 기존 서명키는 그대로입니다.',file=sys.stderr);sys.exit(130)
    except Exception as e:print('수정 앱 준비 중단: '+str(e),file=sys.stderr);sys.exit(1)
