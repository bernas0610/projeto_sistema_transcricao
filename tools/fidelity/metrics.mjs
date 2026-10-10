// WER = (substituições + omissões + inserções) / palavras de referência.
// Não remove acentos nem normaliza números: essas escolhas ficam visíveis no relatório.
export function tokens(text) {
  return text.normalize('NFC').toLocaleLowerCase('pt-BR').match(/[\p{L}\p{N}]+/gu) || [];
}

export function compare(reference, hypothesis) {
  const ref = tokens(reference), hyp = tokens(hypothesis);
  if (!ref.length) throw new Error('A referência deve conter palavras.');
  if (ref.length > 3000 || hyp.length > 3000) throw new Error('Use amostras de até 3000 palavras.');
  const width = hyp.length + 1;
  const directions = new Uint8Array((ref.length + 1) * width);
  let previous = Array.from({ length: width }, (_, j) => j);
  for (let j = 1; j < width; j++) directions[j] = 3;
  for (let i = 1; i <= ref.length; i++) {
    const current = new Array(width); current[0] = i; directions[i * width] = 2;
    for (let j = 1; j < width; j++) {
      const substitution = previous[j - 1] + (ref[i - 1] === hyp[j - 1] ? 0 : 1);
      const deletion = previous[j] + 1, insertion = current[j - 1] + 1;
      current[j] = Math.min(substitution, deletion, insertion);
      directions[i * width + j] = current[j] === substitution ? 1 : current[j] === deletion ? 2 : 3;
    }
    previous = current;
  }
  let i = ref.length, j = hyp.length, substitutions = 0, deletions = 0, insertions = 0;
  const differences = [];
  while (i || j) {
    const direction = directions[i * width + j];
    if (direction === 1) {
      if (ref[i - 1] !== hyp[j - 1]) {
        substitutions++; differences.push({ type: 'substituicao', reference: ref[i - 1], hypothesis: hyp[j - 1], position: i });
      }
      i--; j--;
    } else if (direction === 2) {
      deletions++; differences.push({ type: 'omissao', reference: ref[i - 1], position: i }); i--;
    } else {
      insertions++; differences.push({ type: 'insercao', hypothesis: hyp[j - 1], position: i }); j--;
    }
  }
  return { referenceWords: ref.length, hypothesisWords: hyp.length,
    substitutions, deletions, insertions,
    wer: (substitutions + deletions + insertions) / ref.length, differences: differences.reverse() };
}
