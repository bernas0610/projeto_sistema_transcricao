import http from 'node:http';
import https from 'node:https';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

export function createFrontendServer({ apiTarget = 'http://localhost:8080', publicDir = new URL('./public/', import.meta.url) } = {}) {
  const target = new URL(apiTarget);
  if (!['http:', 'https:'].includes(target.protocol)) throw new Error('API_TARGET deve ser HTTP ou HTTPS');
  const assets = { '/': ['index.html', 'text/html'], '/index.html': ['index.html', 'text/html'], '/styles.css': ['styles.css', 'text/css'], '/theme.css': ['theme.css', 'text/css'], '/app.js': ['app.js', 'text/javascript'], '/core.js': ['core.js', 'text/javascript'], '/latest.js': ['latest.js', 'text/javascript'] };
  return http.createServer(async (req, res) => {
    const incoming = new URL(req.url, 'http://localhost');
    const path = incoming.pathname;
    if (path === '/auth' || path.startsWith('/auth/') || path === '/transcricoes' || path.startsWith('/transcricoes/')) {
      const headers = { ...req.headers, host: target.host };
      delete headers.connection;
      const upstream = (target.protocol === 'https:' ? https : http).request(new URL(incoming.pathname + incoming.search, target), { method: req.method, headers }, response => {
        res.writeHead(response.statusCode, response.headers);
        response.pipe(res);
        response.on('error', () => res.destroy());
      });
      upstream.on('error', () => {
        if (!res.headersSent) { res.writeHead(503, { 'content-type': 'application/json' }); res.end(JSON.stringify({ status: 503, code: 'SERVICO_INDISPONIVEL', retryable: true, message: 'Não foi possível conectar ao backend. Verifique se a aplicação está rodando.' })); }
        else res.destroy();
      });
      req.on('aborted', () => upstream.destroy());
      req.pipe(upstream);
      return;
    }
    if (!assets[path] || !['GET', 'HEAD'].includes(req.method)) { res.writeHead(404); res.end(); return; }
    try {
      const [file, type] = assets[path];
      const content = await readFile(new URL(file, publicDir));
      res.writeHead(200, { 'content-type': `${type}; charset=utf-8`, 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
      res.end(req.method === 'HEAD' ? undefined : content);
    } catch { res.writeHead(404); res.end(); }
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const port = Number(process.env.PORT || 5173);
  const publicDir = process.env.NODE_ENV === 'production' ? new URL('./dist/', import.meta.url) : undefined;
  createFrontendServer({ apiTarget: process.env.API_TARGET, publicDir }).listen(port, process.env.HOST || '127.0.0.1', () => console.log(`Frontend: http://localhost:${port}`));
}
