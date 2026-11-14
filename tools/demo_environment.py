"""Resolve this macOS project's tools without any developer-specific home path."""
import os,shutil
from pathlib import Path
PROJECT=Path(__file__).resolve().parents[1]

def sdk_directory(project=PROJECT, environ=None):
    env=os.environ if environ is None else environ
    for key in ('ANDROID_HOME','ANDROID_SDK_ROOT'):
        if env.get(key):
            path=Path(env[key]).expanduser()
            if (path/'platform-tools/adb').is_file():return path
    props=project/'local.properties'
    if props.is_file():
        for line in props.read_text().splitlines():
            if line.strip().startswith('sdk.dir='):
                value=line.strip().split('=',1)[1].replace('\\:',':').replace('\\ ',' ').replace('\\\\','\\')
                path=Path(value).expanduser()
                if (path/'platform-tools/adb').is_file():return path
    path=Path.home()/'Library/Android/sdk'
    if (path/'platform-tools/adb').is_file():return path
    raise RuntimeError('Android SDK를 찾지 못했습니다. Android Studio에서 SDK를 설치하고 ANDROID_HOME 또는 local.properties의 sdk.dir를 설정해주세요.')

def java_directory(environ=None):
    env=os.environ if environ is None else environ
    if env.get('JAVA_HOME'):
        path=Path(env['JAVA_HOME']).expanduser()
        if (path/'bin/java').is_file():return path
    path=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
    if (path/'bin/java').is_file():return path
    binary=shutil.which('java')
    if binary and binary!='/usr/bin/java':return Path(binary).resolve().parents[1]
    raise RuntimeError('빌드용 Java를 찾지 못했습니다. Android Studio를 설치하거나 JAVA_HOME을 설정해주세요.')
