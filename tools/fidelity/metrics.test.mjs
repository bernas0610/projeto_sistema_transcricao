import test from 'node:test';
import assert from 'node:assert/strict';
import { compare } from './metrics.mjs';

test('pontuação, caixa e Unicode equivalente não contam como erro', () => {
  assert.equal(compare('Olá, ÁUDIO!', 'ola\u0301 a\u0301udio').wer, 0);
});
test('conta separadamente trocas, omissões e palavras extras', () => {
  const result = compare('bom dia este áudio ficou claro', 'bom dia esse ficou claro hoje');
  assert.equal(result.substitutions, 1); assert.equal(result.deletions, 1); assert.equal(result.insertions, 1);
  assert.equal(result.wer, 0.5);
});
test('silêncio omite toda a referência e não produz falso sucesso', () => {
  assert.equal(compare('teste de áudio', '').deletions, 3);
  assert.equal(compare('teste de áudio', '').wer, 1);
  assert.throws(() => compare('', 'teste'), /referência/);
});
test('palavras extras podem produzir WER acima de cem por cento', () => {
  assert.equal(compare('flor', 'flow com palavras extras').wer, 4);
});
test('acentos e representação de números exigem revisão explícita', () => {
  assert.equal(compare('avó vinte', 'avo 20').substitutions, 2);
});
