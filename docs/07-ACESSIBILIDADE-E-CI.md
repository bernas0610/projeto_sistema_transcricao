# 07 — Acessibilidade, compatibilidade e integração contínua

## Interface

- Atalho “Ir para o conteúdo” como primeiro controle de teclado, com destino
  correto no formulário de login ou no conteúdo principal.
- Navegação identificada, página atual com `aria-current` e foco no conteúdo ao
  trocar de tela. Alternância enviar/gravar usa `aria-pressed`.
- Restauração do foco dos controles recriados pela atualização de histórico,
  recentes, resultado e diálogo. A atualização de status não deve devolver foco
  ao início da página ou fechar o diálogo.
- Campos associados às mensagens de erro; instrução da senha associada ao campo.
- Prévia de áudio identificada; texto da transcrição navegável e rolável pelo teclado.
- Texto pequeno do histórico com contraste maior, foco visível sobre a sidebar
  escura e controles móveis com altura mínima de 44 px.
- Diálogos nativos mantêm Escape e contenção de foco. Animações continuam respeitando
  `prefers-reduced-motion`.

A validação de teclado/árvore de acessibilidade e layout usa Chromium do navegador
do aplicativo. Gravação, prévia, envio e liberação do microfone no navegador externo
foram confirmados pelo usuário na validação anterior. Isso não certifica leitores
de tela nem todos os navegadores: NVDA/VoiceOver, Firefox, Safari e dispositivos
físicos continuam exigindo conferência específica antes de prometer suporte.
HTTPS ou localhost são necessários para captura de microfone; há fallback para
upload quando `getUserMedia`/`MediaRecorder` não estão disponíveis.

Em 10/10/2026 foram conferidos Tab/Enter no login e no histórico, foco preservado
durante transição simulada de processamento para conclusão, rolagem do texto por
PageDown, ciclo de Tab dentro do diálogo e Escape retornando ao item atualizado
do histórico. Viewports de 390×844 e 1440×900 não tiveram rolagem horizontal;
os controles principais móveis mediram 44 px de altura. Contrastes medidos nas
cores renderizadas: texto do histórico 4,76:1, cabeçalho 4,55:1, botão primário
5,17:1 e status concluído 6,77:1. Esses valores cobrem os elementos conferidos,
não constituem auditoria completa de conformidade.

## GitHub Actions

O workflow `.github/workflows/ci.yml` roda em push na `main`, pull requests e
disparo manual. Três jobs separados:

1. Java 25, Maven Wrapper e FFmpeg: suíte com H2/substitutos, excluindo o teste
   real do Gemini e o contexto que depende do PostgreSQL de uso.
2. Node 22: testes do frontend/proxy e build.
3. Python: integridade e proteções do backup, sem banco de uso. O round trip
   PostgreSQL é validado localmente e fica fora desse job padrão.

Não depende de chave Gemini, senha do banco ou secrets de produção. Tokens têm
somente leitura de conteúdo; checkout não persiste credenciais. O workflow tem
timeouts e cancela uma execução anterior da mesma referência. Não publica site,
não faz deploy e não cria backups de dados pessoais no GitHub.

O comparador de fidelidade está preparado, mas a avaliação com gravações reais
foi adiada pelo usuário. Nenhuma gravação de aula deve ser adicionada ao repositório
ou a artefatos de CI.
