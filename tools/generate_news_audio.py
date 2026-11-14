#!/usr/bin/env python3
"""Generate two validated, dated news summaries using the local Mac voice."""
from pathlib import Path
import array,hashlib,json,shutil,subprocess,tempfile,wave
PROJECT=Path(__file__).resolve().parent.parent
ASSETS=PROJECT/'app/src/main/assets/news'
def validate(path):
    with wave.open(str(path),'rb') as w:
        if (w.getnchannels(),w.getsampwidth(),w.getframerate())!=(1,2,48000):raise RuntimeError('뉴스 음성 형식 오류')
        samples=array.array('h',w.readframes(w.getnframes()));duration=len(samples)/48000
    if not 3<duration<180 or sum(abs(x)>250 for x in samples)<4800:raise RuntimeError('뉴스 음성이 비어 있거나 정상 생성되지 않았습니다: '+str(path))
    return duration

def main():
    stories=json.loads((ASSETS/'stories.json').read_text())
    with tempfile.TemporaryDirectory(prefix='commutemate_news_') as tmp:
        tmp=Path(tmp);staged=[];manifest={}
        for index,story in enumerate(stories,1):
            ident=story['id'];aiff=tmp/(ident+'.aiff');wav=tmp/(ident+'.wav')
            subprocess.run(['/usr/bin/say','-v','Yuna','-r','145','-o',str(aiff),story['text']],check=True)
            subprocess.run(['/usr/bin/afconvert','-f','WAVE','-d','LEI16@48000','-c','1',str(aiff),str(wav)],check=True)
            validate(wav)
            with wave.open(str(wav),'rb') as audio:params=audio.getparams();data=audio.readframes(audio.getnframes())
            samples=array.array('h',data);fade=min(240,len(samples)//2)
            for i in range(fade):samples[i]=int(samples[i]*i/fade);samples[-i-1]=int(samples[-i-1]*i/fade)
            with wave.open(str(wav),'wb') as audio:
                audio.setparams(params);audio.writeframes(bytes(48000)+samples.tobytes()+bytes(144000))
            duration=validate(wav);staged.append(wav)
            manifest[ident]={'textSha256':hashlib.sha256(story['text'].encode()).hexdigest(),'durationSeconds':round(duration,2),'source':story['url'],'publishedAt':story['publishedAt']}
            print(f'[{index}/{len(stories)}] {story["title"]} · {duration:.1f}초 확인',flush=True)
        for wav in staged:shutil.copy2(wav,ASSETS/wav.name)
        (ASSETS/'audio_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    print('완료: 뉴스 음성 2개 준비. 이제 앱을 빌드합니다.',flush=True)
if __name__=='__main__':main()
