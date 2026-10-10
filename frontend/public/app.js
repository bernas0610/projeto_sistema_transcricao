import { escapeHtml as h, STATUS, emAndamento, validarArquivo, mensagemErro, orientacaoErro } from './core.js';
const app = document.querySelector('#app');
const detail = document.querySelector('#detail-dialog');
const userDialog = document.querySelector('#user-dialog');
const state = { user: null, csrf: null, page: 0, jobs: [], pages: 0, total: 0, file: null, busy: false, epoch: 0, selected: null, tab: 'home', mode: 'upload' };
let recorder, recordingStream, recordingTimer, recordingStarted, recordedUrl;
let poll, refreshBusy = false, toastTimer;
let detailOpenerSelector;
function focusSelector(element) {
  return element.id ? '#' + CSS.escape(element.id)
    : element.dataset.job ? '[data-job="' + CSS.escape(element.dataset.job) + '"]'
    : element.dataset.recent ? '[data-recent="' + CSS.escape(element.dataset.recent) + '"]'
    : element.classList.contains('close-dialog') ? '.close-dialog' : null;
}
detail.addEventListener('close', () => {
  if (!state.user) return;
  const opener = detailOpenerSelector && document.querySelector(detailOpenerSelector);
  (opener?.getClientRects().length ? opener : document.querySelector('#main-content'))?.focus({ preventScroll: true });
});
const icon = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${({ upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 15v5h16v-5"/>', file: '<path d="M14 3H6v18h12V7l-4-4Z"/><path d="M14 3v5h4M9 12h6m-6 4h6"/>', arrow: '<path d="m9 5 7 7-7 7"/>', close: '<path d="m6 6 12 12M6 18 18 6"/>', users: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M17 5a3 3 0 0 1 0 6m1 4a5 5 0 0 1 3 5"/>', logout: '<path d="M9 4H4v16h5m5-12 4 4-4 4m-6-4h10"/>', eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>', copy: '<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M16 8V3H3v13h5"/>', download: '<path d="M12 3v13m-5-5 5 5 5-5M4 20h16"/>', refresh: '<path d="M20 8a8 8 0 1 0 0 8M20 3v5h-5"/>' })[name]}</svg>`;
const brand = '<div class="brand"><span class="signal-mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 10v4m4-7v10m4-13v16m4-13v10m4-7v4"/></svg></span>Transcreve</div>';
const wave = (count = 27) => Array.from({ length: count }, (_, i) => `<i style="height:${12 + Math.abs(Math.sin(i * 1.7)) * (i < count / 2 ? i + 2 : count - i + 1) * 6}px"></i>`).join('');
const date = value => value ? new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : 'Agora';
const progresso = job => job.totalPartes > 0 ? `${job.partesConcluidas || 0} de ${job.totalPartes} partes concluídas` : '';
const remaining = () => Math.max(0, state.user.limiteArquivosDiario - state.user.arquivosEnviadosHoje);
function toast(message, error = false) { const el = document.querySelector('#toast'); el.textContent = message; el.classList.toggle('error-toast', error); el.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => el.hidden = true, 6500); }
async function request(url, { method = 'GET', body, context = '' } = {}) {
  try {
    const headers = {};
    if (method !== 'GET') {
      if (!state.csrf) await csrf();
      headers[state.csrf.headerName] = state.csrf.token;
      if (body && !(body instanceof URLSearchParams)) headers['Content-Type'] = 'application/json';
    }
    const res = await fetch(url, { method, credentials: 'same-origin', headers, body: body && !(body instanceof URLSearchParams) ? JSON.stringify(body) : body });
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) {
      if (res.status === 401 && state.user) showLogin();
      throw new Error(mensagemErro(res.status, data, context));
    }
    return data;
  } catch (error) { if (error instanceof TypeError) throw new Error('Não foi possível conectar. Verifique sua conexão e se o backend está rodando.'); throw error; }
}
async function csrf() { state.csrf = await request('/auth/csrf'); }
function preserveFocus(root) {
  const previous = document.activeElement;
  if (!root?.contains(previous)) return () => {};
  const selector = focusSelector(previous);
  return () => {
    if (previous.isConnected) return;
    const target = selector && root.querySelector(selector) || root.querySelector('button') || root;
    if (target === root) root.tabIndex = -1;
    target.focus({ preventScroll: true });
  };
}
function navigate(view) {
  stopRecording(); state.tab = view; renderContent(); document.querySelector('#main-content')?.focus();
}
function showLogin() {
  stopRecording(); if (recordedUrl) URL.revokeObjectURL(recordedUrl);
  document.querySelector('#skip-content').href = '#login-form';
  state.epoch++; state.user = null; state.csrf = null; state.jobs = []; state.recentJobs = []; state.selected = null; state.file = null; state.busy = false; clearInterval(poll); detail.close(); userDialog.close();
  app.innerHTML = `<div class="login"><section class="login-story">${brand}<div><h1>Do áudio<br>ao <em>texto.</em></h1><p class="muted">Transforme aulas, conversas e reuniões em texto. Mais tempo para ouvir. Menos tempo para anotar.</p><div class="wave-art" aria-hidden="true">${wave(40)}</div></div><footer>GRAVE. TRANSCREVA. EXPORTE.</footer></section><section class="login-form-wrap"><form class="login-form" id="login-form" tabindex="-1" aria-label="Acesse sua conta"><p class="eyebrow">TRANSCRIÇÃO DE ÁUDIO</p><h2>Acesse sua conta.</h2><p class="muted">Entre na sua conta para continuar.</p><div class="field"><label for="email">E-mail</label><input id="email" aria-describedby="login-error" type="email" autocomplete="username" placeholder="voce@exemplo.com" required></div><div class="field"><label for="password">Senha</label><div class="password-field"><input id="password" aria-describedby="login-error" type="password" autocomplete="current-password" placeholder="Sua senha" required><button type="button" class="password-toggle" id="toggle-password" aria-label="Mostrar senha">${icon('eye')}</button></div></div><button class="primary" id="login-button">Entrar ${icon('arrow')}</button><p id="login-error" class="error" role="alert"></p><p class="restricted-note">Ainda não tem acesso?<br>Peça ao administrador para criar sua conta.</p></form></section></div>`;
  document.querySelector('#toggle-password').onclick = event => { const field = document.querySelector('#password'); field.type = field.type === 'password' ? 'text' : 'password'; event.currentTarget.setAttribute('aria-label', field.type === 'password' ? 'Mostrar senha' : 'Ocultar senha'); };
  document.querySelector('#login-form').onsubmit = async event => {
    event.preventDefault(); const button = document.querySelector('#login-button'), error = document.querySelector('#login-error'); error.textContent = ''; button.disabled = true; button.textContent = 'Entrando…';
    try { await csrf(); await request('/auth/login', { method: 'POST', body: new URLSearchParams({ email: document.querySelector('#email').value, password: document.querySelector('#password').value }), context: 'login' }); state.csrf = null; await csrf(); state.user = await request('/auth/me'); state.epoch++; state.page = 0; state.tab = 'home'; renderShell(); await refresh(); }
    catch (e) { error.textContent = e.message; button.disabled = false; button.innerHTML = `Entrar ${icon('arrow')}`; }
  };
}
function renderShell() {
  document.querySelector('#skip-content').href = '#main-content';
  app.innerHTML = `<div class="shell"><aside class="sidebar">${brand}<nav aria-label="Navegação principal"><button class="nav-button active" id="nav-home">⌂ <span>Início</span></button><button class="nav-button" id="nav-jobs">${icon('file')} Minhas transcrições</button>${state.user.role === 'ADMIN' ? `<button class="nav-button" id="nav-users">${icon('users')} Usuários</button>` : ''}</nav><div class="recent-heading">RECENTES</div><div id="recents"></div><div class="sidebar-bottom"><div class="quota-side" id="quota-side"></div><div class="sidebar-account"><div class="account-avatar">${h(state.user.email.slice(0, 1).toUpperCase())}</div><div class="account-copy"><div class="account-email">${h(state.user.email)}</div><div class="account-role">${state.user.role === 'ADMIN' ? 'Administrador' : 'Conta pessoal'}</div></div><button class="logout" id="logout" aria-label="Sair da conta">${icon('logout')}</button></div></div></aside><div class="workspace"><header class="topbar"><p class="breadcrumb" id="breadcrumb-page">Início</p><div class="topbar-actions"><span class="privacy-note"><i></i> Suas transcrições ficam só com você</span><button class="primary" id="new-transcription"><span aria-hidden="true">＋</span> Nova transcrição</button><button class="logout mobile-logout" id="mobile-logout" aria-label="Sair da conta">${icon('logout')}</button></div></header><main class="main" id="main-content" tabindex="-1" aria-label="Conteúdo principal"></main></div></div>`;
  document.querySelector('#nav-home').onclick = () => navigate('home');
  document.querySelector('#new-transcription').onclick = () => { stopRecording(); state.tab = 'home'; state.mode = 'upload'; renderContent(); document.querySelector('#choose-file').click(); };
  document.querySelector('#nav-jobs').onclick = () => navigate('jobs');
  document.querySelector('#nav-users')?.addEventListener('click', () => navigate('users'));
  document.querySelector('#logout').onclick = async () => { try { await request('/auth/logout', { method: 'POST' }); showLogin(); } catch (e) { toast(e.message, true); } };
  document.querySelector('#mobile-logout').onclick = document.querySelector('#logout').onclick;
  renderContent();
}
function renderContent() {
  if (!state.user) return;
  document.querySelector('#quota-side').innerHTML = `<span class="small">SEU USO HOJE</span><strong>${remaining()} de ${state.user.limiteArquivosDiario} arquivos disponíveis</strong><div class="quota-track"><span style="width:${Math.min(100, state.user.arquivosEnviadosHoje / state.user.limiteArquivosDiario * 100)}%"></span></div><p class="small" style="margin-top:11px">Renova à meia-noite.</p>`;
  document.querySelector('#breadcrumb-page').textContent = ({ home: 'Início', jobs: 'Minhas transcrições', users: 'Usuários' })[state.tab];
  document.querySelector('#nav-home').classList.toggle('active', state.tab === 'home');
  document.querySelector('#nav-jobs').classList.toggle('active', state.tab === 'jobs'); document.querySelector('#nav-users')?.classList.toggle('active', state.tab === 'users');
  for (const view of ['home', 'jobs', 'users']) {
    const button = document.querySelector('#nav-' + view);
    if (button) { if (state.tab === view) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current'); }
  }
  const main = document.querySelector('#main-content');
  main.classList.toggle('home-content', state.tab === 'home');
  main.classList.toggle('history-content', state.tab === 'jobs');
  if (state.tab === 'users') {
    main.innerHTML = `<div class="hero"><div><p class="eyebrow">Administração</p><h1>Gerencie os <em>acessos.</em></h1><p class="muted">Crie contas individuais para quem vai usar o Transcreve.</p></div></div><section class="card admin-panel"><h2>Crie um novo acesso</h2><p class="muted">Cada pessoa terá suas próprias transcrições e poderá enviar até ${state.user.limiteArquivosDiario} arquivos por dia. As novas contas recebem o perfil de usuário comum.</p><button class="primary" id="new-user">${icon('users')} Criar usuário</button></section>`;
    document.querySelector('#new-user').onclick = openUserDialog;
    return;
  }
  main.innerHTML = `
    <section class="hero"><div><p class="welcome-pill">ÁUDIO EM TEXTO</p><h1>Transcreva.<br><em>Revise. Exporte.</em></h1><p class="muted">Envie um arquivo ou grave pelo microfone. Acompanhe o processamento e exporte o texto.</p></div></section>
    <section class="card upload-card">
      <div class="upload-tabs"><button id="mode-upload" class="upload-tab">${icon('upload')} Enviar áudio</button><button id="mode-record" class="upload-tab"><span aria-hidden="true">♩</span> Gravar agora</button></div>
      <div class="dropzone" id="dropzone"><div class="upload-icon">${icon('upload')}</div><button class="file-picker" id="choose-file" type="button">Escolha um áudio para transcrever</button><p class="muted">ou arraste e solte o arquivo aqui</p><p class="format-note">MP3, M4A, WAV e outros formatos · até 300 MB</p><input type="file" id="file-input" accept=".mp3,.wav,.m4a,.ogg,.flac,.aac,.webm,.opus,.mpeg" hidden></div>
      <div class="record-panel" id="record-panel" hidden><div class="upload-icon">♩</div><h3>Grave seu áudio.</h3><p class="muted" id="record-state">Grave pelo microfone, depois envie para transcrever.</p><button class="secondary" id="record-button">Iniciar gravação</button><audio id="record-preview" aria-label="Prévia da gravação" controls hidden></audio></div>
      <div id="selected-file"></div><div id="upload-progress" hidden class="progress" role="progressbar" aria-label="Envio do áudio" aria-valuemin="0" aria-valuemax="100"><span></span></div>
      <div class="upload-footer"><p class="muted small"><span id="remaining">${remaining()} arquivos disponíveis hoje</span><br>O processamento continua mesmo se você fechar a página.</p><button class="primary" id="upload-button">${icon('upload')} Transcrever áudio</button></div><p class="error" id="upload-error" role="alert"></p>
    </section>
    <section id="latest-section" class="latest-section"></section>
    <section class="history-section"><div class="section-heading"><h2>Minhas transcrições</h2><div class="history-tools"><span class="count-label" id="history-count"></span><button class="secondary" id="refresh-jobs" aria-label="Atualizar transcrições">${icon('refresh')}</button></div></div><div class="card history-card" id="history"></div><div class="pagination" id="pagination"></div></section><p class="footer-note">Transcreve · Áudio em texto</p>`;
  const input = document.querySelector('#file-input'), zone = document.querySelector('#dropzone');
  document.querySelector('#choose-file').onclick = () => { if (!state.busy) input.click(); };
  input.onchange = () => selectFile(input.files[0]);
  zone.ondragover = e => { e.preventDefault(); if (!state.busy) zone.classList.add('drag'); };
  zone.ondragleave = () => zone.classList.remove('drag');
  zone.ondrop = e => { e.preventDefault(); zone.classList.remove('drag'); if (!state.busy) selectFile(e.dataTransfer.files[0]); };
  document.querySelector('#upload-button').onclick = upload;
  document.querySelector('#refresh-jobs').onclick = () => refresh().catch(e => toast(e.message, true));
  document.querySelector('#mode-upload').onclick = () => { stopRecording(); state.mode = 'upload'; updateMode(); };
  document.querySelector('#mode-record').onclick = () => { state.mode = 'record'; updateMode(); };
  document.querySelector('#record-button').onclick = record;
  updateMode(); updateFile(); renderHistory(); renderRecents(); renderLatest();
}
function selectFile(file) { const error = validarArquivo(file); document.querySelector('#upload-error').textContent = error || ''; if (!error) { state.file = file; updateFile(); } }
function updateFile() {
  const el = document.querySelector('#selected-file'); if (!el) return;
  el.innerHTML = state.file ? `<div class="selected-file"><div class="file-symbol">${icon('file')}</div><div class="file-info"><strong>${h(state.file.name)}</strong><span>${(state.file.size / 1024 / 1024).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB · pronto para enviar</span></div><button id="remove-file" class="icon-button" aria-label="Remover arquivo" ${state.busy ? 'disabled' : ''}>${icon('close')}</button></div>` : '';
  document.querySelector('#remove-file')?.addEventListener('click', () => { state.file = null; document.querySelector('#file-input').value = ''; updateFile(); });
  const button = document.querySelector('#upload-button'); button.disabled = !state.file || state.busy || !remaining(); button.innerHTML = state.busy ? 'Enviando…' : `${icon('upload')} Transcrever áudio`;
  document.querySelector('#choose-file').disabled = state.busy;
  for (const id of ['mode-upload', 'mode-record', 'record-button']) document.getElementById(id).disabled = state.busy;
}
async function upload() {
  if (!state.file || state.busy || !remaining()) return;
  state.busy = true; updateFile(); const epoch = state.epoch; document.querySelector('#upload-error').textContent = '';
  try {
    if (!state.csrf) await csrf();
    const job = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest(), form = new FormData(); form.append('arquivo', state.file); xhr.open('POST', '/transcricoes'); xhr.setRequestHeader(state.csrf.headerName, state.csrf.token);
      xhr.upload.onprogress = e => { if (!e.lengthComputable || epoch !== state.epoch) return; const el = document.querySelector('#upload-progress'); if (!el) return; el.hidden = false; const percent = Math.round(e.loaded / e.total * 100); el.setAttribute('aria-valuenow', percent); el.querySelector('span').style.width = `${percent}%`; };
      xhr.onload = () => { let data; try { data = JSON.parse(xhr.responseText); } catch { data = null; } if (xhr.status === 202) resolve(data); else { if (xhr.status === 401) showLogin(); reject(new Error(mensagemErro(xhr.status, data))); } };
      xhr.onerror = () => reject(new Error('Falha de conexão durante o envio. Tente novamente.')); xhr.send(form);
    });
    if (epoch !== state.epoch) return; state.file = null; state.page = 0; state.tab = 'jobs'; renderContent(); toast('Áudio enviado! Acompanhe o status em Minhas transcrições.'); await refresh();
  } catch (e) { if (epoch === state.epoch) { const el = document.querySelector('#upload-error'); if (el) el.textContent = e.message; else toast(e.message, true); } }
  finally { if (epoch === state.epoch) { state.busy = false; document.querySelector('#upload-progress')?.setAttribute('hidden', ''); updateFile(); } }
}
async function refresh() {
  if (!state.user || refreshBusy) return;
  refreshBusy = true; const epoch = state.epoch;
  try {
    const [user, page, recentPage] = await Promise.all([request('/auth/me'), request(`/transcricoes?pagina=${state.page}`), state.page ? request('/transcricoes?pagina=0') : Promise.resolve(null)]);
    if (epoch !== state.epoch) return; state.user = user; state.jobs = page.itens; state.pages = page.totalPaginas; state.total = page.total;
    state.recentJobs = (recentPage || page).itens;
    if (state.tab !== 'users') { renderHistory(); renderLatest(); document.querySelector('#remaining').textContent = `${remaining()} arquivos disponíveis hoje`; updateFile(); }
    renderRecents();
    document.querySelector('#quota-side').innerHTML = `<span class="small">SEU USO HOJE</span><strong>${remaining()} de ${user.limiteArquivosDiario} arquivos disponíveis</strong><div class="quota-track"><span style="width:${Math.min(100, user.arquivosEnviadosHoje / user.limiteArquivosDiario * 100)}%"></span></div><p class="small" style="margin-top:11px">Renova à meia-noite.</p>`;
    if (state.selected && detail.open && emAndamento(state.selected)) { const job = await request(`/transcricoes/${encodeURIComponent(state.selected.id)}`); if (epoch === state.epoch) { state.selected = job; renderDetail(); } }
    clearInterval(poll); poll = setInterval(() => refresh().catch(e => toast(e.message, true)), state.jobs.some(emAndamento) ? 5000 : 60000);
  } finally { refreshBusy = false; }
}
function renderHistory() {
  const list = document.querySelector('#history'); if (!list) return;
  const restoreFocus = preserveFocus(list);
  document.querySelector('#history-count').textContent = `${state.total} ${state.total === 1 ? 'arquivo' : 'arquivos'}`;
  list.innerHTML = state.jobs.length ? `<div class="table-header"><span>Arquivo</span><span>Enviado em</span><span>Status</span><span></span></div>${state.jobs.map(job => `<button class="job-row" data-job="${h(job.id)}" aria-label="Abrir transcrição de ${h(job.nomeArquivoOriginal)}"><div class="file-cell"><div class="file-symbol">${icon('file')}</div><div style="min-width:0"><p class="file-title">${h(job.nomeArquivoOriginal)}</p><p class="file-id">${h(progresso(job) || 'Áudio em português')}</p></div></div><span class="job-date">${h(date(job.criadoEm))}</span><span class="status ${h(job.status)}">${h(STATUS[job.status] || job.status)}</span><span class="row-arrow">${icon('arrow')}</span></button>`).join('')}` : `<div class="empty"><div class="file-symbol">${icon('file')}</div><h3>Seu primeiro áudio começa aqui.</h3><p>Use Nova transcrição para enviar um áudio.<br>Suas transcrições aparecerão neste espaço.</p></div>`;
  list.querySelectorAll('[data-job]').forEach(el => el.onclick = async () => { try { state.selected = await request(`/transcricoes/${encodeURIComponent(el.dataset.job)}`); renderDetail(); detail.showModal(); } catch (e) { toast(e.message, true); } });
  document.querySelector('#pagination').innerHTML = state.pages > 1 ? `<span>Página ${state.page + 1} de ${state.pages}</span><div><button class="secondary" id="previous-page" ${!state.page ? 'disabled' : ''}>Anterior</button><button class="secondary" id="next-page" ${state.page + 1 >= state.pages ? 'disabled' : ''}>Próxima</button></div>` : '';
  document.querySelector('#previous-page')?.addEventListener('click', () => { state.page--; refresh().catch(e => toast(e.message, true)); }); document.querySelector('#next-page')?.addEventListener('click', () => { state.page++; refresh().catch(e => toast(e.message, true)); });
  restoreFocus();
}
function renderDetail() {
  if (!detail.open) detailOpenerSelector = focusSelector(document.activeElement);
  const restoreFocus = preserveFocus(detail);
  const job = state.selected;
  detail.innerHTML = `<div class="dialog-header"><div><h2 id="detail-title">${h(job.nomeArquivoOriginal)}</h2><p class="small muted">${h(date(job.criadoEm))} · <span class="status ${h(job.status)}">${h(STATUS[job.status])}</span></p>${progresso(job) ? `<p class="small muted">${h(progresso(job))}</p>` : ''}</div><button class="icon-button close-dialog" aria-label="Fechar transcrição">${icon('close')}</button></div>${job.status === 'CONCLUIDA' ? `<pre class="transcript" tabindex="0" aria-label="Texto da transcrição">${h(job.texto || '')}</pre><div class="dialog-actions"><button class="secondary" id="copy-text">${icon('copy')} Copiar texto</button><button class="primary" id="download-text">${icon('download')} Baixar .txt</button></div>` : `<div class="processing-info">${job.status === 'ERRO' ? `<strong>Não foi possível concluir.</strong><br>${h(job.mensagemErro || orientacaoErro(job))}` : `<strong>${job.status === 'PENDENTE' ? 'Seu áudio está na fila.' : 'Estamos transcrevendo seu áudio.'}</strong><br>Você pode fechar esta janela. O status será atualizado automaticamente.`}</div>${job.status === 'ERRO' ? `<div class="dialog-actions"><button class="primary" id="retry-transcription">${icon('refresh')} Reprocessar áudio</button></div><p class="small muted">As partes já salvas serão reutilizadas. Não consome outro arquivo da sua cota diária.</p>` : ''}`}`;
  detail.querySelector('.close-dialog').onclick = () => detail.close();
  document.querySelector('#retry-transcription')?.addEventListener('click', async event => {
    const button = event.currentTarget, epoch = state.epoch; button.disabled = true; button.textContent = 'Reenfileirando…';
    try {
      const updated = await request('/transcricoes/' + encodeURIComponent(job.id) + '/reprocessar', { method: 'POST', context: 'reprocessar' });
      if (epoch !== state.epoch) return;
      if (state.selected?.id === job.id) { state.selected = updated; renderDetail(); }
      toast('Áudio reenfileirado. As partes já salvas serão reutilizadas.');
      await refresh();
    } catch (error) { if (epoch === state.epoch) toast(error.message, true); }
    finally { if (button.isConnected) { button.disabled = false; button.innerHTML = icon('refresh') + ' Reprocessar áudio'; } }
  });
  document.querySelector('#copy-text')?.addEventListener('click', async () => { try { await navigator.clipboard.writeText(job.texto); toast('Texto copiado.'); } catch { toast('Não foi possível copiar. Você pode selecionar o texto acima.', true); } });
  document.querySelector('#download-text')?.addEventListener('click', () => { const url = URL.createObjectURL(new Blob([job.texto || ''], { type: 'text/plain;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = job.nomeArquivoOriginal.replace(/\.[^.]+$/, '') + '.txt'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); });
  restoreFocus();
}
function renderRecents() {
  const root = document.querySelector('#recents'); if (!root) return;
  const restoreFocus = preserveFocus(root);
  root.innerHTML = (state.recentJobs || []).slice(0, 4).map(job => `<button class="recent-job" data-recent="${h(job.id)}"><span class="recent-icon">▷</span><span class="recent-copy"><strong>${h(job.nomeArquivoOriginal)}</strong><small>${h(date(job.criadoEm))}</small></span><span class="recent-status">${h(STATUS[job.status])}</span></button>`).join('') || '<p class="recent-empty">Nenhuma transcrição recente.</p>';
  root.querySelectorAll('[data-recent]').forEach(el => el.onclick = async () => { try { state.selected = await request(`/transcricoes/${encodeURIComponent(el.dataset.recent)}`); renderDetail(); detail.showModal(); } catch (e) { toast(e.message, true); } });
  restoreFocus();
}
function renderLatest() {
  const root = document.querySelector('#latest-section'); if (!root) return;
  const restoreFocus = preserveFocus(root);
  const latest = (state.recentJobs || []).find(job => job.status === 'CONCLUIDA');
  if (!latest) { root.innerHTML = `<p class="latest-eyebrow">RESULTADO DA TRANSCRIÇÃO</p><h2>Sua transcrição aparece aqui.</h2><p class="muted">Quando sua primeira transcrição ficar pronta, ela aparecerá aqui.</p>`; restoreFocus(); return; }
  root.innerHTML = `<p class="latest-eyebrow">SUA ÚLTIMA TRANSCRIÇÃO</p><div class="section-heading"><h2>${h(latest.nomeArquivoOriginal)}</h2><div class="latest-actions"><button class="secondary" id="copy-latest">${icon('copy')} Copiar</button><button class="secondary" id="download-latest">${icon('download')} Exportar</button></div></div><div class="latest-text">${h((latest.texto || '').slice(0, 800))}${latest.texto?.length > 800 ? '…' : ''}</div><button class="text-link" id="open-latest">Ler transcrição completa ${icon('arrow')}</button>`;
  document.querySelector('#copy-latest').onclick = () => copyText(latest.texto);
  document.querySelector('#download-latest').onclick = () => downloadText(latest);
  document.querySelector('#open-latest').onclick = () => { state.selected = latest; renderDetail(); detail.showModal(); };
  restoreFocus();
}
async function copyText(text) { try { await navigator.clipboard.writeText(text || ''); toast('Texto copiado.'); } catch { toast('Não foi possível copiar. Você pode selecionar o texto da transcrição.', true); } }
function downloadText(job) { const url = URL.createObjectURL(new Blob([job.texto || ''], { type: 'text/plain;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = job.nomeArquivoOriginal.replace(/\.[^.]+$/, '') + '.txt'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
function updateMode() {
  document.querySelector('#mode-upload').setAttribute('aria-pressed', String(state.mode === 'upload'));
  document.querySelector('#mode-record').setAttribute('aria-pressed', String(state.mode === 'record'));
  document.querySelector('#dropzone').hidden = state.mode !== 'upload'; document.querySelector('#record-panel').hidden = state.mode !== 'record';
  document.querySelector('#mode-upload').classList.toggle('active', state.mode === 'upload'); document.querySelector('#mode-record').classList.toggle('active', state.mode === 'record');
}
function stopRecording() { if (recorder?.state === 'recording') recorder.stop(); recordingStream?.getTracks().forEach(track => track.stop()); clearInterval(recordingTimer); }
async function record() {
  if (recorder?.state === 'recording') { stopRecording(); return; }
  if (state.busy) return;
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) { toast('Este navegador não permite gravação. Você pode enviar um arquivo de áudio.', true); return; }
  const button = document.querySelector('#record-button'), epoch = state.epoch; button.disabled = true;
  try {
    recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    if (epoch !== state.epoch || state.mode !== 'record' || state.tab !== 'home') { stopRecording(); return; }
    const mimeType = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4'].find(type => MediaRecorder.isTypeSupported(type));
    recorder = new MediaRecorder(recordingStream, mimeType ? { mimeType } : undefined);
    const chunks = []; let size = 0; const actualType = recorder.mimeType, stream = recordingStream;
    state.file = null; updateFile();
    recorder.ondataavailable = event => { if (event.data.size) { chunks.push(event.data); size += event.data.size; if (size >= 300 * 1024 * 1024) stopRecording(); } };
    recorder.onstop = () => {
      clearInterval(recordingTimer); stream.getTracks().forEach(track => track.stop());
      if (epoch !== state.epoch) return;
      const blob = new Blob(chunks, { type: actualType }), extension = actualType.includes('mp4') ? 'm4a' : actualType.includes('ogg') ? 'ogg' : 'webm';
      const file = new File([blob], `gravacao-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.${extension}`, { type: actualType });
      const error = validarArquivo(file); if (error) { toast(error, true); return; }
      state.file = file; updateFile(); if (recordedUrl) URL.revokeObjectURL(recordedUrl); recordedUrl = URL.createObjectURL(blob);
      const preview = document.querySelector('#record-preview'); if (preview) { preview.src = recordedUrl; preview.hidden = false; }
      if (document.querySelector('#record-state')) document.querySelector('#record-state').textContent = 'Gravação pronta. Ouça e envie quando quiser.';
      if (document.querySelector('#record-button')) document.querySelector('#record-button').textContent = 'Gravar novamente';
    };
    recorder.start(1000); recordingStarted = Date.now(); button.textContent = 'Parar gravação';
    document.querySelector('#record-preview').hidden = true;
    const updateTime = () => { const seconds = Math.floor((Date.now() - recordingStarted) / 1000); const label = document.querySelector('#record-state'); if (label) label.textContent = `Gravando · ${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`; };
    updateTime(); recordingTimer = setInterval(updateTime, 1000);
  } catch { stopRecording(); toast('Não foi possível acessar o microfone. Verifique a permissão do navegador.', true); }
  finally { button.disabled = false; }
}
window.addEventListener('pagehide', stopRecording);
function openUserDialog() {
  userDialog.className = 'user-dialog'; userDialog.innerHTML = `<div class="dialog-header"><h2 id="user-title">Novo usuário</h2><button class="icon-button close-dialog" aria-label="Fechar cadastro">${icon('close')}</button></div><p class="muted">Defina as credenciais e compartilhe-as diretamente com a pessoa.</p><form id="create-user-form"><div class="field"><label for="new-email">E-mail</label><input aria-describedby="create-user-error" id="new-email" type="email" autocomplete="off" maxlength="255" required></div><div class="field"><label for="new-password">Senha inicial</label><input aria-describedby="password-help create-user-error" id="new-password" type="password" autocomplete="new-password" minlength="8" maxlength="72" required><p id="password-help" class="small muted" style="margin-top:8px">Pelo menos 8 caracteres.</p></div><p class="error" id="create-user-error" role="alert"></p><div class="dialog-actions"><button class="primary" id="create-user-button">Criar acesso</button></div></form>`;
  userDialog.querySelector('.close-dialog').onclick = () => userDialog.close();
  document.querySelector('#create-user-form').onsubmit = async e => { e.preventDefault(); const button = document.querySelector('#create-user-button'); button.disabled = true; try { await request('/auth/cadastro', { method: 'POST', body: { email: document.querySelector('#new-email').value, senha: document.querySelector('#new-password').value } }); document.querySelector('#new-password').value = ''; userDialog.close(); toast('Usuário criado. Ele já pode entrar com as credenciais definidas.'); } catch (error) { const field = document.querySelector('#create-user-error'); if (field) field.textContent = error.message; } finally { button.disabled = false; } };
  userDialog.showModal();
}
document.addEventListener('visibilitychange', () => { if (!document.hidden && state.user) refresh().catch(e => toast(e.message, true)); });
try { state.user = await request('/auth/me'); await csrf(); renderShell(); await refresh(); }
catch (e) { showLogin(); if (!e.message.includes('sessão expirou')) document.querySelector('#login-error').textContent = e.message; }




