import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createFrontendServer } from '../server.mjs';
const start = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(`http://127.0.0.1:${server.address().port}`)));
const stop = server => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); });

test('proxy preserva sessão, CSRF, formulário de login e cookie de resposta', async () => {
  let captured;
  const upstream = http.createServer(async (req, res) => {
    let body = ''; for await (const chunk of req) body += chunk;
    captured = { url: req.url, method: req.method, cookie: req.headers.cookie, csrf: req.headers['x-csrf-token'], body };
    res.writeHead(204, { 'set-cookie': 'JSESSIONID=sessao-nova; HttpOnly; Path=/' }); res.end();
  });
  const backend = await start(upstream), frontend = createFrontendServer({ apiTarget: backend });
  const base = await start(frontend);
  try {
    const result = await fetch(`${base}/auth/login`, { method: 'POST', headers: { Cookie: 'JSESSIONID=anterior', 'X-CSRF-TOKEN': 'csrf-valido' }, body: new URLSearchParams({ email: 'teste@example.com', password: 'senha-ficticia' }) });
    assert.equal(result.status, 204);
    assert.match(result.headers.get('set-cookie'), /sessao-nova/);
    assert.deepEqual(captured, { url: '/auth/login', method: 'POST', cookie: 'JSESSIONID=anterior', csrf: 'csrf-valido', body: 'email=teste%40example.com&password=senha-ficticia' });
  } finally { await stop(frontend); await stop(upstream); }
});
test('proxy encaminha upload multipart sem transformar os bytes', async () => {
  let captured;
  const upstream = http.createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    captured = { body: Buffer.concat(chunks), contentType: req.headers['content-type'] };
    res.writeHead(202, { 'content-type': 'application/json' }); res.end('{"id":"teste"}');
  });
  const frontend = createFrontendServer({ apiTarget: await start(upstream) }), base = await start(frontend);
  try {
    const body = Buffer.from('--boundary\r\nContent-Disposition: form-data; name="arquivo"; filename="aula.mp3"\r\n\r\naudio\r\n--boundary--\r\n');
    const result = await fetch(`${base}/transcricoes`, { method: 'POST', headers: { 'content-type': 'multipart/form-data; boundary=boundary' }, body });
    assert.equal(result.status, 202); assert.deepEqual(captured.body, body); assert.match(captured.contentType, /boundary=boundary/);
  } finally { await stop(frontend); await stop(upstream); }
});
test('servidor entrega a interface e não expõe código do servidor', async () => {
  const server = createFrontendServer(), base = await start(server);
  try {
    const index = await fetch(base); assert.equal(index.status, 200); assert.match(await index.text(), /lang="pt-BR"/);
    assert.equal((await fetch(`${base}/server.mjs`)).status, 404);
    assert.equal((await fetch(`${base}/package.json`)).status, 404);
    assert.equal((await fetch(`${base}/app.js`)).status, 200);
    assert.equal((await fetch(`${base}/theme.css`)).status, 200);
  } finally { await stop(server); }
});
