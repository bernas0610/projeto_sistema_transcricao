import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, validarArquivo, mensagemErro, emAndamento } from '../public/core.js';

test('nomes e texto de transcrição não inserem HTML executável', () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
  assert.equal(escapeHtml('a & b'), 'a &amp; b');
});
test('validação do áudio aceita MPEG e extensões em maiúsculas', () => {
  for (const name of ['aula.MP3', 'aula.mpeg', 'aula.m4a']) assert.equal(validarArquivo({ name, size: 10 }), null);
  assert.ok(validarArquivo({ name: 'arquivo.txt', size: 10 }));
  assert.ok(validarArquivo({ name: 'aula.mp3', size: 0 }));
  assert.ok(validarArquivo({ name: 'aula.mp3', size: 300 * 1024 * 1024 + 1 }));
});
test('falhas de autenticação, cota e conflito têm mensagens próprias', () => {
  assert.match(mensagemErro(401, null, 'login'), /senha incorretos/);
  assert.match(mensagemErro(401, null), /sessão expirou/);
  assert.match(mensagemErro(429, null), /limite/);
  assert.match(mensagemErro(409, null), /cadastrado/);
  assert.match(mensagemErro(503, { message: 'Backend indisponível' }), /Backend indisponível/);
});
test('somente jobs pendentes e processando são acompanhados', () => {
  assert.ok(emAndamento({ status: 'PENDENTE' }));
  assert.ok(emAndamento({ status: 'PROCESSANDO' }));
  assert.equal(emAndamento({ status: 'CONCLUIDA' }), false);
  assert.equal(emAndamento({ status: 'ERRO' }), false);
});
