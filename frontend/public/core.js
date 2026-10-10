export const FORMATOS = ['mp3', 'wav', 'm4a', 'ogg', 'flac', 'aac', 'webm', 'opus', 'mpeg'];
export const STATUS = { PENDENTE: 'Na fila', PROCESSANDO: 'Transcrevendo', CONCLUIDA: 'Concluída', ERRO: 'Falhou' };
export const emAndamento = job => ['PENDENTE', 'PROCESSANDO'].includes(job.status);
export function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
export function validarArquivo(file) {
  if (!file || !file.size) return 'Escolha um arquivo de áudio que não esteja vazio.';
  if (file.size > 300 * 1024 * 1024) return 'O arquivo deve ter no máximo 300 MB.';
  if (!FORMATOS.includes(file.name.split('.').pop().toLowerCase())) return 'Formato não suportado. Use MP3, WAV, M4A, OGG, FLAC, AAC, WEBM, OPUS ou MPEG.';
  return null;
}
export function mensagemErro(status, body, context = '') {
  if (body?.code && ![401, 409].includes(status)) return body.message || 'Não foi possível concluir a operação.';
  if (status === 401) return context === 'login' ? 'E-mail ou senha incorretos.' : 'Sua sessão expirou. Entre novamente.';
  if (status === 403) return 'Acesso negado ou sessão de segurança expirada. Atualize a página e tente novamente.';
  if (status === 409) return context === 'reprocessar'
    ? 'Não é possível reprocessar agora. Atualize o status e confira se o áudio original ainda está disponível.'
    : 'Esse e-mail já está cadastrado.';
  if (status === 404 && context === 'reprocessar') return 'Transcrição não encontrada. Atualize seu histórico.';
  if (status === 429) return 'Você atingiu o limite de arquivos de hoje. Tente novamente amanhã.';
  if (status === 413) return 'O arquivo excede o limite de 300 MB.';
  if (status === 400) return 'Verifique os dados informados e tente novamente.';
  return body?.message || 'Não foi possível concluir a operação. Tente novamente.';
}

export function orientacaoErro(job) {
  const orientacoes = {
    COTA_PROVEDOR_DIARIA: 'Aguarde a renovação da cota do serviço antes de reprocessar.',
    LIMITE_PROVEDOR: 'Aguarde alguns minutos antes de reprocessar.',
    PROVEDOR_INDISPONIVEL: 'Tente reprocessar mais tarde.',
    AUDIO_INVALIDO: 'Confira o áudio e envie uma versão válida.',
    ORIGINAL_AUSENTE: 'Envie o áudio novamente.',
  };
  return orientacoes[job.codigoErro] || (job.erroRepetivel
    ? 'Tente reprocessar mais tarde.' : 'Avise o administrador antes de reprocessar.');
}
