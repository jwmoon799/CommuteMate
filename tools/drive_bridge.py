#!/usr/bin/env python3
"""Local emulator-only turn bridge. Never controls gear, speed or a real vehicle."""
import argparse,json,secrets,threading,time,urllib.request,urllib.error
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

class TurnBridge:
    def __init__(self, transport=None):
        self.transport=transport or self.http
        self.browser_client='commutemate-'+secrets.token_hex(12)
        self.last_heartbeat=0.0
        self.safety_cursor='';self.safety_event=None;self.safety_created=0.0
        self.lock=threading.RLock();self.mode=None;self.turn_id=None;self.created=0.;self.online=False;self.error='연결 대기';self.completed=None;self.revision=0
    def http(self, method, path):
        request=urllib.request.Request('http://127.0.0.1:8088'+path,method=method)
        with urllib.request.urlopen(request,timeout=1.5) as r:
            data=r.read()
            return json.loads(data) if data else {}
    def refresh(self):
        with self.lock:
            try:
                # The official console activates DDS only while a browser session is alive.
                # Reuse that published session handshake while this 3D page is polling.
                if time.monotonic()-self.last_heartbeat >= 0.8:
                    self.transport('POST','/browser/session?client='+self.browser_client)
                    self.last_heartbeat=time.monotonic()
                raw=self.transport('GET','/vehicle/state')
                if raw.get('controls',{}).get('emulator_active') is not True:raise RuntimeError('에뮬레이터 비활성')
                mode=raw['lights']['turn_signal_mode']
                if mode not in ('Off','BlinkLeft','BlinkRight','FlashLeft','FlashRight'):raise RuntimeError('지원하지 않는 신호')
                # Reconnection invalidates old completion requests even if the direction is unchanged.
                if mode!=self.mode or not self.online:
                    self.turn_id=secrets.token_urlsafe(18) if mode!='Off' else None
                    self.created=time.monotonic();self.completed=None;self.revision+=1
                self.mode=mode;self.online=True;self.error=''
            except Exception as e:self.online=False;self.error=str(e)
            return self.snapshot()
    def snapshot(self):
        return {'online':self.online,'mode':self.mode,'turnId':self.turn_id,'error':self.error,'emulatorOnly':True,'bridgeVersion':3,'revision':self.revision,'serverId':self.browser_client}
    def safety_snapshot(self):
        with self.lock:
            fresh = self.safety_event if time.monotonic()-self.safety_created < 15 else None
            return {'source':'demo','cursor':self.safety_cursor,'event':fresh,'emulatorOnly':True}
    def warn(self, kind):
        if kind not in ('lead_departure','attention'):raise ValueError('invalid warning kind')
        with self.lock:
            self.safety_cursor=secrets.token_urlsafe(18)
            self.safety_event={'kind':kind,'source':'demo'}
            self.safety_created=time.monotonic()
            return self.safety_snapshot()
    def signal(self, mode):
        if mode not in ('Off','BlinkLeft','BlinkRight'):raise ValueError('invalid mode')
        with self.lock:
            if not self.refresh()['online']:raise RuntimeError(self.error)
            self.transport('PUT','/vehicle/turn-signal?mode='+mode)
            return self.refresh()
    def complete(self, turn_id):
        with self.lock:
            self.refresh()
            if not self.online:raise RuntimeError('연결 끊김: 신호를 변경하지 않습니다')
            if not turn_id or turn_id!=self.turn_id:raise ValueError('만료된 회전 요청')
            if time.monotonic()-self.created<7:raise ValueError('회전 완료 시점 이전')
            # Re-read actual emulator state, not a local UI switch. New direction wins.
            if self.mode not in ('BlinkLeft','BlinkRight','FlashLeft','FlashRight'):raise ValueError('이미 해제된 신호')
            self.transport('PUT','/vehicle/turn-signal?mode=Off')
            state=self.refresh()
            if state['mode']!='Off':raise RuntimeError('OFF 확인 대기: 학습 재개는 실제 SDK 수신 기준')
            return state

class Server(ThreadingHTTPServer):
    daemon_threads=True

def make_handler(bridge,folder,port):
    origin='http://127.0.0.1:'+str(port)
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def result(self,status,data,kind='application/json; charset=utf-8'):
            payload=data if isinstance(data,bytes) else json.dumps(data,ensure_ascii=False).encode()
            self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(payload)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(payload)
        def valid_host(self):return self.headers.get('Host')=='127.0.0.1:'+str(port)
        def do_GET(self):
            if not self.valid_host():return self.result(403,{'error':'localhost only'})
            path=urlparse(self.path).path
            if path=='/api/health':return self.result(200,{'service':'commutemate-drive','bridgeVersion':3})
            if path=='/api/state':return self.result(200,bridge.refresh())
            if path=='/api/safety-state':return self.result(200,bridge.safety_snapshot())
            assets={'/':'index.html','/index.html':'index.html','/turn-route.js':'turn-route.js'}
            if path not in assets:return self.result(404,{'error':'not found'})
            p=folder/assets[path]
            return self.result(200,p.read_bytes(),'text/html; charset=utf-8' if p.suffix=='.html' else 'text/javascript; charset=utf-8')
        def do_POST(self):
            if not self.valid_host() or self.headers.get('Origin')!=origin or self.headers.get('X-Commute-Drive')!='1':return self.result(403,{'error':'same-origin control required'})
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=1024:raise ValueError('invalid body')
                body=json.loads(self.rfile.read(size));path=urlparse(self.path).path
                if path=='/api/safety-demo':data=bridge.warn(body.get('kind'))
                elif path=='/api/signal':data=bridge.signal(body.get('mode'))
                elif path=='/api/turn-complete':data=bridge.complete(body.get('turnId'))
                else:return self.result(404,{'error':'not found'})
                self.result(200,data)
            except ValueError as e:self.result(409,{'error':str(e)})
            except Exception as e:self.result(503,{'error':str(e)})
    return Handler

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8091);args=parser.parse_args()
    folder=Path(__file__).resolve().parent/'driving_demo'
    server=Server(('127.0.0.1',args.port),make_handler(TurnBridge(),folder,args.port))
    print('CommuteMate 연동 3D: http://127.0.0.1:'+str(args.port)+'/',flush=True)
    print('이 터미널을 열어 두세요. 종료: Control+C. Virtual Console은 8088 포워딩이 필요합니다.',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()
if __name__=='__main__':main()
