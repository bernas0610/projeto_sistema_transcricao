import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compare } from './metrics.mjs';

async function main() {
  const args = process.argv.slice(2);
  if (args.length % 2 || args.some((arg, i) => i % 2 === 0 && !['--transcripts', '--output', '--model'].includes(arg))) {
    throw new Error('Uso: node tools/fidelity/evaluate.mjs --transcripts PASTA --output RELATORIO.json --model MODELO');
  }
  const options = Object.fromEntries(Array.from({ length: args.length / 2 }, (_, i) => [args[i * 2], args[i * 2 + 1]]));
  if (!options['--transcripts'] || !options['--output'] || !options['--model']) throw new Error('Informe --transcripts, --output e --model.');
  const corpusDir = fileURLToPath(new URL('../../docs/fidelidade/', import.meta.url));
  const corpus = JSON.parse(await readFile(resolve(corpusDir, 'corpus.json'), 'utf8'));
  const samples = [];
  for (const sample of corpus.samples) {
    const reference = await readFile(resolve(corpusDir, 'referencias', sample.file), 'utf8');
    try {
      const hypothesis = await readFile(resolve(options['--transcripts'], sample.file), 'utf8');
      samples.push({ id: sample.id, category: sample.category, status: 'avaliada', ...compare(reference, hypothesis) });
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      samples.push({ id: sample.id, category: sample.category, status: 'transcricao_ausente' });
    }
  }
  const evaluated = samples.filter(sample => sample.status === 'avaliada');
  const sum = field => evaluated.reduce((total, sample) => total + sample[field], 0);
  const words = sum('referenceWords');
  const report = { corpusVersion: corpus.version, generatedAt: new Date().toISOString(), model: options['--model'],
    complete: evaluated.length === samples.length, evaluated: evaluated.length, expected: samples.length,
    normalization: 'NFC, minúsculas, pontuação ignorada; acentos e representação de números preservados',
    aggregate: { referenceWords: words, substitutions: sum('substitutions'), deletions: sum('deletions'), insertions: sum('insertions'),
      wer: words ? (sum('substitutions') + sum('deletions') + sum('insertions')) / words : null }, samples };
  await mkdir(dirname(resolve(options['--output'])), { recursive: true });
  await writeFile(options['--output'], JSON.stringify(report, null, 2) + '\n');
  console.log(`Amostras avaliadas: ${evaluated.length}/${samples.length}. WER: ${report.aggregate.wer === null ? 'não medido' : (report.aggregate.wer * 100).toFixed(2) + '%'}.`);
  if (!report.complete) { console.error('Relatório incompleto: faltam transcrições. Não representa a fidelidade do sistema.'); process.exitCode = 1; }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
