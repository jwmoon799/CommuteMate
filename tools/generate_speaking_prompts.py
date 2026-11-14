#!/usr/bin/env python3
"""Generate and verify local Korean speaking cues, then install them in CommuteMate."""
from pathlib import Path
import array
import hashlib
import shutil
import subprocess
import sys
import tempfile
import wave

CLIPS = {
    'intro': '다음 문장을 두 번 반복해서 말하세요.',
    'praise': '잘했어요.',
    'retry': '한 번 더 따라 말해주세요.',
    'four': '사', 'three': '삼', 'two': '이', 'one': '일',
}
FALLBACK_PROJECT = Path(__file__).resolve().parents[1]


def validate(path):
    with wave.open(str(path), 'rb') as audio:
        rate = audio.getframerate()
        channels = audio.getnchannels()
        width = audio.getsampwidth()
        frames = audio.getnframes()
        samples = array.array('h', audio.readframes(frames))
    if sys.byteorder != 'little':
        samples.byteswap()
    duration = frames / rate
    if (channels, width, rate) != (1, 2, 48000):
        raise RuntimeError(f'음성 형식 오류: {path.name}')
    # Check both signal strength and a nontrivial amount of non-silent audio.
    audible = sum(abs(sample) >= 250 for sample in samples)
    if not 0.15 <= duration <= 15 or audible < rate * 0.03:
        raise RuntimeError(f'비어 있거나 소리가 없는 파일: {path.name}')
    return duration


def make_countdown(directory):
    """One four-second stream avoids restarting the audio output per number."""
    rate = 48000
    data = bytearray()
    for name in ('four', 'three', 'two', 'one'):
        with wave.open(str(directory / f'{name}.wav'), 'rb') as audio:
            samples = array.array('h', audio.readframes(audio.getnframes()))
        if sys.byteorder != 'little':
            samples.byteswap()
        # A short fade prevents discontinuities at clip boundaries.
        fade = min(240, len(samples) // 2)
        for index in range(fade):
            samples[index] = int(samples[index] * index / fade)
            samples[-index - 1] = int(samples[-index - 1] * index / fade)
        if sys.byteorder != 'little':
            samples.byteswap()
        clip = samples.tobytes()
        data.extend(clip)
        data.extend(bytes(max(0, rate * 2 - len(clip))))
    output = directory / 'countdown.wav'
    with wave.open(str(output), 'wb') as audio:
        audio.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
        audio.writeframes(data)
    validate(output)
    return output


def add_output_warmup(path):
    """Start output with 500ms of silence; preserve every original speech sample."""
    with wave.open(str(path), 'rb') as audio:
        params = audio.getparams()
        data = audio.readframes(audio.getnframes())
    prefix = bytes(params.framerate // 2 * params.nchannels * params.sampwidth)
    temp = path.with_suffix('.warmup.wav')
    with wave.open(str(temp), 'wb') as audio:
        audio.setparams(params)
        audio.writeframes(prefix + data)
    temp.replace(path)
    validate(path)


def main():
    if sys.argv[1:] not in ([], ['--intro-only']):
        raise RuntimeError('지원하는 옵션: --intro-only')
    intro_only = sys.argv[1:] == ['--intro-only']
    selected = {'intro': CLIPS['intro']} if intro_only else CLIPS
    candidate = Path(__file__).resolve().parent.parent
    project = candidate if (candidate / 'app/src/main').is_dir() else FALLBACK_PROJECT
    if not (project / 'app/src/main/AndroidManifest.xml').is_file():
        raise RuntimeError('CommuteMate 프로젝트 경로를 찾지 못했습니다.')
    destination = project / 'app/src/main/assets/speaking'
    with tempfile.TemporaryDirectory(prefix='commutemate_speaking_') as scratch:
        scratch = Path(scratch)
        staged = []
        for index, (name, text) in enumerate(selected.items(), 1):
            aiff, wav = scratch / f'{name}.aiff', scratch / f'{name}.wav'
            subprocess.run(['/usr/bin/say', '-v', 'Yuna', '-r', '145', '-o', str(aiff), text], check=True)
            subprocess.run(['/usr/bin/afconvert', '-f', 'WAVE', '-d', 'LEI16@48000', '-c', '1', str(aiff), str(wav)], check=True)
            duration = validate(wav)
            staged.append(wav)
            print(f'[{index}/{len(selected)}] {text} · {duration:.2f}초 확인', flush=True)
        if not intro_only:
            staged.append(make_countdown(scratch))
        for wav in staged:
            if wav.stem in ('intro', 'praise', 'retry', 'countdown'):
                add_output_warmup(wav)
        # Do not install any generated files until every prompt has valid audio.
        destination.mkdir(parents=True, exist_ok=True)
        for wav in staged:
            output = destination / wav.name
            shutil.copyfile(wav, output)
            validate(output)
            if hashlib.sha256(wav.read_bytes()).digest() != hashlib.sha256(output.read_bytes()).digest():
                raise RuntimeError(f'복사 검증 실패: {output.name}')
    print(f'완료: 안내 음성 {len(staged)}개를 준비했습니다.')
    print('빌드 후 실행하면 앱에 반영됩니다.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'음성 생성 중단: {error}', file=sys.stderr)
        print('화면의 오류 내용을 채팅으로 알려주세요.', file=sys.stderr)
        sys.exit(1)
