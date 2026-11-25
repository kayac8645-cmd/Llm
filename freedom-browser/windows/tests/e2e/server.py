import http.server, socketserver, sys
PAGES = {
 '/adtest.html': ('text/html', b'<!doctype html><title>Ad test</title><h1>Ad test page</h1><p>Two tracker requests below should be blocked.</p>'
   b'<img src="https://ad.doubleclick.net/pixel.gif" width=1 height=1><script src="https://www.google-analytics.com/analytics.js"></script>'
   b'<p><a href="/popup.html">popup test</a></p>'),
 '/private.html': ('text/html', b'<!doctype html><title>Private page</title><h1>Private page</h1>'),
 '/popup.html': ('text/html', b'<!doctype html><title>Popup test</title><h1>Popup test</h1><script>window.open("/popup-target.html","x","width=300,height=300")</script>'),
 '/popup-target.html': ('text/html', b'<!doctype html><title>Popup target</title><h1>Popup opened</h1>'),
 '/report.txt': ('text/plain', b'hello from the test server\n' * 1000),
 '/setup.exe': ('application/octet-stream', b'MZ' + b'\0' * 4096),
}
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        p = self.path.split('?')[0]
        if p not in PAGES: self.send_error(404); return
        ctype, body = PAGES[p]
        self.send_response(200)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        if p in ('/report.txt', '/setup.exe'): self.send_header('Content-Disposition', 'attachment; filename="%s"' % p[1:])
        self.end_headers(); self.wfile.write(body)
    def log_message(self, *a): sys.stderr.write('REQ %s\n' % (self.path,))
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(('127.0.0.1', 8765), H).serve_forever()
