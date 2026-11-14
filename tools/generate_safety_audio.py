#!/usr/bin/env python3
"""Stage both Korean warning clips, verify PCM and publish only a complete set."""
from pathlib import Path
import subprocess,tempfile,wave,array,sys,json,hashlib,shutil
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/safety'
CLIPS={'lead_departure':'앞차가 출발했습니다. 전방을 확인하세요.','attention':'전방을 주시하세요.'}
def verify(folder):
    data=json.loads((folder/'manifest.json').read_text())
    for key,text in CLIPS.items():
        p=folder/(key+'.wav')
        with wave.open(str(p),'rb') as f:
            if (f.getframerate(),f.getnchannels(),f.getsampwidth())!=(48000,1,2):raise RuntimeError('음성 형식 오류: '+key)
            frames=f.getnframes();pcm=array.array('h',f.readframes(frames))
        if sys.byteorder!='little':pcm.byteswap()
        if not 1 <= frames/48000 <= 12 or max(map(abs,pcm),default=0)<500:raise RuntimeError('비어 있는 안내 음성: '+key)
        if data[key]['text']!=text or data[key]['sha256']!=hashlib.sha256(p.read_bytes()).hexdigest():raise RuntimeError('음성 검증 불일치: '+key)
    return data

def main():
    if sys.argv[1:] not in ([],['--verify-only']):raise RuntimeError('지원 옵션: --verify-only')
    if sys.argv[1:]:print(json.dumps(verify(OUT),ensure_ascii=False));return
    try:
        existing=verify(OUT);print('검증된 안전 안내 음성 2개를 그대로 사용합니다.');return
    except (OSError,KeyError,ValueError,RuntimeError,EOFError,wave.Error):pass
    with tempfile.TemporaryDirectory(prefix='commutemate_safety_') as tmp:
        stage=Path(tmp);manifest={}
        for key,text in CLIPS.items():
            aiff=stage/(key+'.aiff');wav=stage/(key+'.wav')
            subprocess.run(['/usr/bin/say','-v','Yuna','-r','155','-o',str(aiff),text],check=True,timeout=30)
            subprocess.run(['/usr/bin/afconvert','-f','WAVE','-d','LEI16@48000','-c','1',str(aiff),str(wav)],check=True,timeout=15)
            with wave.open(str(wav),'rb') as f:
                if (f.getframerate(),f.getnchannels(),f.getsampwidth())!=(48000,1,2):raise RuntimeError('음성 형식 오류')
                pcm=array.array('h',f.readframes(f.getnframes()))
            if sys.byteorder!='little':pcm.byteswap()
            if len(pcm)<10000 or max(map(abs,pcm),default=0)<500:raise RuntimeError('Mac 음성 합성이 빈 파일을 만들었습니다. macOS 터미널에서 이 실행기를 직접 실행해주세요.')
            gain=min(1,26000/max(map(abs,pcm)))
            for i in range(len(pcm)):pcm[i]=int(pcm[i]*gain)
            for i in range(240):
                pcm[i]=int(pcm[i]*i/240);pcm[-i-1]=int(pcm[-i-1]*i/240)
            if sys.byteorder!='little':pcm.byteswap()
            with wave.open(str(wav),'wb') as f:
                f.setparams((1,2,48000,0,'NONE','not compressed'))
                f.writeframes(bytes(48000)+pcm.tobytes()+bytes(9600))
            manifest[key]={'text':text,'seconds':round(.6+len(pcm)/48000,3),'sha256':hashlib.sha256(wav.read_bytes()).hexdigest()}
        (stage/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
        verify(stage)
        OUT.mkdir(exist_ok=True)
        for name in ['lead_departure.wav','attention.wav','manifest.json']:shutil.copy2(stage/name,OUT/name)
        verify(OUT)
    print('완료: 앞차 출발·전방 주시 안내 음성 2개 생성 및 검증 성공',flush=True)
if __name__=='__main__':
    try:main()
    except Exception as e:print('음성 준비 중단: '+str(e),file=sys.stderr);sys.exit(1)
