import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLatestLoader } from '../public/latest.js';

test('compartilha pedidos e mantém apenas o resultado mais recente', async () => {
  const calls = [];
  const loader = createLatestLoader(async id => { calls.push(id); return { id, texto: 'Aula completa' }; });
  const a = { id: 'a', atualizadoEm: '1' };
  const [first, second] = await Promise.all([loader.load(a), loader.load(a)]);
  assert.equal(first.texto, 'Aula completa'); assert.equal(first, second);
  await loader.load(a); assert.deepEqual(calls, ['a']);
  await loader.load({ ...a, atualizadoEm: '2' });
  await loader.load({ id: 'b', atualizadoEm: '1' });
  await loader.load(a); assert.deepEqual(calls, ['a', 'a', 'b', 'a']);
});

test('logout invalida resposta pendente e falha permite tentar novamente', async () => {
  let resolve;
  const loader = createLatestLoader(() => new Promise(r => { resolve = r; }));
  const pending = loader.load({ id: 'a' });
  await Promise.resolve(); loader.clear(); resolve({ texto: 'Privado' });
  assert.equal(await pending, null);
  let attempts = 0;
  const retry = createLatestLoader(async () => { if (!attempts++) throw Error('offline'); return { texto: 'OK' }; });
  await assert.rejects(retry.load({ id: 'a' }));
  assert.equal((await retry.load({ id: 'a' })).texto, 'OK');
});
