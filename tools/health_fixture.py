"""Local-only HTTP health fixture. This is not the team's backend."""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if not self.path.endswith('/actuator/health'):
            self.send_error(404)
            return
        mode = self.path.split('/')[1]
        if mode == 'timeout':
            time.sleep(8)
        status = 503 if mode == 'down' else 200
        body = b'not-json' if mode == 'invalid' else json.dumps({'status': 'DOWN' if mode == 'down' else 'UP'}).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass

if __name__ == '__main__':
    server = ThreadingHTTPServer(('127.0.0.1', 18885), Handler)
    print('TEST FIXTURE ONLY: http://127.0.0.1:18885/{up,down,invalid,timeout}/actuator/health', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
