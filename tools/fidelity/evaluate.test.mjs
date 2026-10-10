import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, copyFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('./evaluate.mjs', import.meta.url));
const corpusDir = fileURLToPath(new URL('../../docs/fidelidade/', import.meta.url));
test('CLI não inventa resultado quando faltam transcrições', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'fidelity-'));
  try {
    const output = join(dir, 'report.json');
    const result = spawnSync(process.execPath, [script, '--transcripts', dir, '--output', output, '--model', 'simulacao'], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    const report = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(report.complete, false); assert.equal(report.aggregate.wer, null); assert.equal(report.evaluated, 0);
  } finally { await rm(dir, { recursive: true, force: true }); }
});
test('CLI agrega todos os casos e registra modelo informado com entradas sintéticas', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'fidelity-'));
  try {
    const corpus = JSON.parse(await readFile(join(corpusDir, 'corpus.json'), 'utf8'));
    await mkdir(join(dir, 'transcripts'));
    for (const sample of corpus.samples) await copyFile(join(corpusDir, 'referencias', sample.file), join(dir, 'transcripts', sample.file));
    const output = join(dir, 'report.json');
    const result = spawnSync(process.execPath, [script, '--transcripts', join(dir, 'transcripts'), '--output', output, '--model', 'simulacao'], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    const report = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(report.complete, true); assert.equal(report.evaluated, 5); assert.equal(report.aggregate.wer, 0);
    assert.equal(report.model, 'simulacao'); assert.equal(report.corpusVersion, '1');
  } finally { await rm(dir, { recursive: true, force: true }); }
});
