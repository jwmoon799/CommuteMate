#!/usr/bin/env python3
"""Read-only preflight: no emulator launch, server startup or permission changes."""
import json,hashlib,wave,zipfile,sys
from demo_environment import PROJECT,sdk_directory,java_directory

def main():
    failed=[]
    def check(label,action):
        try:detail=action();print('OK  '+label+': '+str(detail))
        except Exception as e:failed.append(label);print('확인 필요  '+label+': '+str(e))
    check('프로젝트',lambda:PROJECT if (PROJECT/'gradlew').is_file() else (_ for _ in ()).throw(RuntimeError('gradlew 없음')))
    check('Android SDK',sdk_directory)
    check('빌드 Java',java_directory)
    def audio():
        folder=PROJECT/'app/src/main/assets/safety'
        manifest=json.loads((folder/'manifest.json').read_text())
        for key in ('lead_departure','attention'):
            p=folder/(key+'.wav')
            if hashlib.sha256(p.read_bytes()).hexdigest()!=manifest[key]['sha256']:raise RuntimeError('파일 해시 불일치: '+key)
            with wave.open(str(p),'rb') as a:
                if (a.getframerate(),a.getnchannels(),a.getsampwidth())!=(48000,1,2) or a.getnframes()<48000:raise RuntimeError('음성 형식 오류: '+key)
        return '안내 음성 2개 해시와 PCM 정상'
    check('안전 안내 음성',audio)
    apk=PROJECT/'app/build/outputs/apk/debug/app-debug.apk'
    if apk.exists():
        def packaged():
            with zipfile.ZipFile(apk) as z:
                for key in ('lead_departure','attention'):
                    if z.getinfo('assets/safety/'+key+'.wav').file_size<20000:raise RuntimeError('APK 안내 음성 누락')
            return 'debug APK의 안전 음성 포함 확인 (서명·실행 검증은 별도)'
        check('기존 APK',packaged)
    else:print('안내  기존 APK 없음: 첫 실행 전 빌드 필요')
    print('이 점검은 에뮬레이터 연결, 실제 차량 지원, 화면·음질을 검증하지 않습니다.')
    return 1 if failed else 0
if __name__=='__main__':sys.exit(main())
