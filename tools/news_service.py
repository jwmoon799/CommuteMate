#!/usr/bin/env python3
"""Latest-first personal RSS reader + local speech audio for the emulator."""
from pathlib import Path
from html.parser import HTMLParser
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlparse,parse_qs
import array,datetime,email.utils,hashlib,html,json,re,secrets,subprocess,tempfile,threading,time,wave,xml.etree.ElementTree as ET
FEED='https://www.newsis.com/RSS/economy.xml'
CACHE=Path(tempfile.gettempdir())/'commutemate-live-news';CACHE.mkdir(exist_ok=True)
KST=datetime.timezone(datetime.timedelta(hours=9))

def clean(value):return re.sub(r'\s+',' ',html.unescape(re.sub(r'<[^>]+>',' ',value or ''))).strip()
def parse_feed(data,now=None):
    now=now or datetime.datetime.now(datetime.timezone.utc)
    if len(data)>3_000_000 or b'<!DOCTYPE' in data.upper():raise ValueError('invalid feed')
    items=[];seen=set()
    for item in ET.fromstring(data).findall('./channel/item'):
        title=clean(item.findtext('title'));url=item.findtext('link') or ''
        if not title or title.startswith(('[인사]','[부고]')) or not url.startswith('https://www.newsis.com/'):continue
        if not re.search('경제|금리|물가|환율|증시|코스피|코스닥|금융|수출|투자|증권|기업|주식|무역|반도체|산업|생산|부가가치|성장|시장|매출|영업|수입|고용',title):continue
        try:date=email.utils.parsedate_to_datetime(item.findtext('pubDate') or '')
        except (ValueError,TypeError):continue
        if date.tzinfo is None or not -300 <= (now-date).total_seconds() <= 7*86400:continue
        if url in seen:continue
        seen.add(url)
        published=date.astimezone(KST).strftime('%Y-%m-%d %H:%M')
        items.append({'category':'경제 · 최신순','title':title,'source':'뉴시스 경제','publishedAt':published,'url':url,'_date':date})
    items.sort(key=lambda x:x['_date'],reverse=True)
    for item in items:item.pop('_date')
    if len(items)<2:raise RuntimeError('최근 경제 뉴스 2편을 확보하지 못했습니다.')
    return items

class ArticleParser(HTMLParser):
    """Read the publisher's public article element, excluding photos and page UI."""
    VOID = {'br','img','input','meta','link','hr','source','wbr','area','embed','param','track','col','base'}
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.active=False; self.found=False; self.stack=[]; self.parts=[]
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if tag=='article' and not self.found:
            self.active=True; self.found=True; return
        if not self.active:return
        classes=set(attrs.get('class','').split())
        skip=(tag in {'script','style','aside','iframe','button'} or
              bool(classes & {'summury','thumCont','article_photo','photojournal','copyright'}) or
              attrs.get('id')=='view_ad' or 'ad' in classes)
        inherited=self.stack[-1][1] if self.stack else False
        if tag not in self.VOID:self.stack.append((tag,skip or inherited))
        if tag in {'br','p','div','h2','h3'} and not (skip or inherited):self.parts.append('\n')
    def handle_endtag(self,tag):
        if not self.active:return
        if tag=='article':self.active=False;return
        for index in range(len(self.stack)-1,-1,-1):
            if self.stack[index][0]==tag:
                self.stack=self.stack[:index];break
        if tag in {'p','div','h2','h3'}:self.parts.append('\n')
    def handle_data(self,data):
        if self.active and not (self.stack and self.stack[-1][1]):self.parts.append(data)
    def body(self):
        lines=[clean(line) for line in ''.join(self.parts).splitlines()]
        text='\n\n'.join(line for line in lines if line)
        # Reporter contact and copyright follow the end of the editorial body.
        text=re.split(r'\s*◎공감언론|\s*Copyright\s*©',text, maxsplit=1)[0].strip()
        if not self.found or len(text)<100:raise RuntimeError('공개 기사 본문을 확인하지 못했습니다.')
        return text

def fetch_article(url):
    if not re.fullmatch(r'https://www\.newsis\.com/view/[A-Za-z0-9_]+',url):
        raise ValueError('지원하지 않는 기사 주소')
    data=subprocess.run(['/usr/bin/curl','--fail','--silent','--show-error','--max-time','20',
        '--proto','=https',url],check=True,capture_output=True,timeout=25).stdout
    if len(data)>5_000_000:raise RuntimeError('기사 응답 크기 초과')
    return data.decode('utf-8')

def complete_story(story,loader=fetch_article):
    parser=ArticleParser();parser.feed(loader(story['url']))
    body=parser.body()
    story=dict(story)
    story['text']=f"뉴시스 경제 뉴스입니다. {story['title']}.\n\n{body}"
    story['id']=hashlib.sha256(('full-v2'+story['url']+story['text']).encode()).hexdigest()[:24]
    story['contentMode']='full';story['bodyCharacters']=len(body)
    return story

def validate(path):
    with wave.open(str(path),'rb') as audio:
        if (audio.getnchannels(),audio.getsampwidth(),audio.getframerate())!=(1,2,48000):raise RuntimeError('음성 형식 오류')
        samples=array.array('h',audio.readframes(audio.getnframes()))
    if len(samples)<48000*2 or sum(abs(x)>250 for x in samples)<4800:raise RuntimeError('음성 생성 실패: 실제 소리 없음')

def speech(story):
    target=CACHE/(story['id']+'.wav')
    if target.exists():validate(target);return target
    with tempfile.TemporaryDirectory(prefix='commutemate_news_') as tmp:
        tmp=Path(tmp);aiff=tmp/'speech.aiff';wav=tmp/'speech.wav'
        source=tmp/'article.txt';source.write_text(story['text'])
        subprocess.run(['/usr/bin/say','-v','Yuna','-r','145','-o',str(aiff),'-f',str(source)],check=True,timeout=240,capture_output=True)
        subprocess.run(['/usr/bin/afconvert','-f','WAVE','-d','LEI16@48000','-c','1',str(aiff),str(wav)],check=True,timeout=15,capture_output=True)
        validate(wav)
        with wave.open(str(wav),'rb') as audio:params=audio.getparams();data=audio.readframes(audio.getnframes())
        with wave.open(str(target.with_suffix('.tmp')),'wb') as audio:
            audio.setparams(params);audio.writeframes(bytes(48000)+data+bytes(144000))
        target.with_suffix('.tmp').replace(target);validate(target)
    return target

class NewsService:
    def __init__(self,fetcher=None,speaker=None):
        self.lock=threading.Lock();self.data=None;self.updated=0
        self.fetcher=fetcher or self.fetch;self.speaker=speaker or speech
    def fetch(self):
        return subprocess.run(['/usr/bin/curl','--fail','--silent','--show-error','--max-time','15','--proto','=https',FEED],check=True,capture_output=True,timeout=20).stdout
    def latest(self,force=False):
        with self.lock:
            if not force and self.data and time.monotonic()-self.updated<300:return self.data
            stories=[]
            for candidate in parse_feed(self.fetcher())[:8]:
                try:stories.append(complete_story(candidate))
                except (RuntimeError,ValueError,subprocess.SubprocessError):continue
                if len(stories)==2:break
            if len(stories)!=2:raise RuntimeError('전체 본문을 읽을 수 있는 뉴스 2편을 확보하지 못했습니다.')
            for item in stories:
                path=self.speaker(item)
                item['audioPath']='/audio/'+path.name
                item['audioSha256']=hashlib.sha256(path.read_bytes()).hexdigest()
            self.data={'stories':stories,'fetchedAt':datetime.datetime.now(KST).strftime('%Y-%m-%d %H:%M:%S'),'sort':'latest','provider':'뉴시스 경제 RSS'}
            self.updated=time.monotonic();return self.data

class Handler(BaseHTTPRequestHandler):
    service=NewsService()
    def log_message(self,*args):pass
    def reply(self,status,data,kind='application/json; charset=utf-8'):
        blob=data if isinstance(data,bytes) else json.dumps(data,ensure_ascii=False).encode()
        self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(blob)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(blob)
    def do_GET(self):
        if self.headers.get('Host')!='127.0.0.1:8092' or self.headers.get('Sec-Fetch-Site')=='cross-site':return self.reply(403,{'error':'local app only'})
        parsed=urlparse(self.path)
        if parsed.path=='/health':return self.reply(200,{'service':'commutemate-live-news','version':2})
        try:
            if parsed.path=='/news/latest':return self.reply(200,self.service.latest(parse_qs(parsed.query).get('force')==['1']))
            if re.fullmatch(r'/audio/[a-f0-9]{24}\.wav',parsed.path):
                path=CACHE/parsed.path.split('/')[-1]
                if path.exists():return self.reply(200,path.read_bytes(),'audio/wav')
            self.reply(404,{'error':'not found'})
        except Exception as e:
            print('뉴스 갱신 실패:',type(e).__name__,str(e)[:160],flush=True)
            self.reply(503,{'error':'최신 뉴스나 음성을 준비하지 못했습니다. Mac 뉴스 서버의 로그를 확인해주세요.'})

if __name__=='__main__':
    print('최신 뉴스 서비스: 127.0.0.1:8092 (종료 Control+C)',flush=True)
    server=ThreadingHTTPServer(('127.0.0.1',8092),Handler)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()
