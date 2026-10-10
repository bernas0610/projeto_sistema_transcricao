# 04 — Backlog

[← SLA e reengajamento](03-SLA%20e%20reengajamento.md) · [README](../README.md)

## Como ler

Este backlog consolida entregas e próximos trabalhos. **Entregue** descreve código
existente; **validar** indica verificações pendentes; **proposto** não implica
implementação ou compromisso de prazo. Prioridades refletem a sequência sugerida
para o projeto, sem datas de entrega.

## Entregas atuais

- [x] Estrutura Spring Boot, entidades e migrations Flyway.
- [x] Upload, validação inicial e consulta do status.
- [x] Divisão de áudio com FFmpeg em partes de 15 minutos.
- [x] Provedor Gemini, prompt de transcrição e retries.
- [x] Processamento assíncrono e recuperação de jobs após reinício.
- [x] P02: checkpoints por parte no banco, duração de segmento preservada e
  progresso no histórico/detalhe; retomada reutiliza trechos confirmados.
- [x] P03: reprocessamento autenticado pelo dono, com original disponível,
  checkpoints preservados e bloqueio de pedidos concorrentes, sem novo upload.
- [x] Tratamento específico de cota diária explícita do Gemini.
- [x] Autenticação por sessão, BCrypt e CSRF.
- [x] Limite diário de cinco arquivos por usuário, com reserva transacional.
- [x] Perfis `ADMIN` e `USER`; criação de usuários restrita ao admin.
- [x] Bootstrap do primeiro administrador pelo ambiente.
- [x] Isolamento de transcrições por dono e histórico paginado.
- [x] Repositório organizado em `backend/` e `frontend/`.
- [x] Interface Transcreve: login, upload, histórico, gravação, cópia/exportação e administração.
- [x] Testes do backend e do proxy/validações do frontend.
- [x] README e documentos de arquitetura, dados, API e operação.
- [x] Identidade Transcreve: nome, símbolo de áudio, paleta azul/grafite/cinza,
  textos objetivos e banner do README atualizados em 10/10/2026.

## Validação do frontend integrado

O login, logout, navegação, formulário do admin e layout em desktop/celular foram
conferidos no navegador. O upload foi validado ponta a ponta com áudio sintético
em 10/10/2026. O usuário também confirmou gravação, reprodução da prévia, envio
e recebimento da transcrição com voz natural no navegador externo nessa data.

| ID | Trabalho | Critério de conclusão |
| --- | --- | --- |
| V01 | Upload real pelo navegador | Áudio curto chega ao backend, progresso de envio funciona, status conclui e texto abre. |
| V02 | Captura pelo microfone | Permissão, gravação, parada, reprodução, envio e liberação do microfone funcionam. |
| V03 | Cópia e exportação | Texto é copiado e `.txt` contém acentuação e conteúdo esperado. |
| V04 | Conta comum | Cadastro pelo admin funciona; conta comum não vê administração nem jobs alheios. |
| V05 | Falhas e cotas na interface | Sessão expirada, backend indisponível, arquivo inválido e sexto envio têm resposta clara. |

Usar conta e áudios de teste; chamadas reais ao Gemini consomem a cota do provedor.

### Validação de 10/10/2026

- **V01 validado com áudio sintético:** seleção de WAV pelo navegador, envio com
  progresso até 100%, processamento com FFmpeg e Gemini, atualização do histórico
  e abertura do texto concluído. A cota passou de cinco para quatro arquivos.
  Duas tentativas de transcrição falharam antes da conclusão automática.
- **V03 validado:** exportação pela home gerou `teste-interface.txt` com conteúdo
  completo e acentuação correta. O usuário confirmou manualmente a cópia e a
  colagem do texto “Testando áudio em Flow 1 2 3.” no navegador externo, incluindo
  o acento de “áudio”. A divergência anterior na colagem automatizada não se
  reproduziu nesse teste manual.
- **V02 validado pelo usuário no navegador externo:** gravação com microfone,
  parada, reprodução da prévia, envio e recebimento da transcrição funcionaram.
  A primeira gravação silenciosa ocorreu com o microfone mutado, conforme
  identificado pelo usuário. Ele também confirmou que o indicador de uso do
  microfone desliga tanto ao parar quanto ao sair da tela durante a gravação.
- **Fidelidade observada:** na gravação com voz natural, o usuário relatou que
  o provedor transcreveu “flow” em vez de “Flor”, no nome anterior do produto.
  Registrar como exemplo para
  P01; esse teste isolado não mede a qualidade geral da transcrição.
- **V05 validado nos cenários previstos:** arquivos `.txt` e WAV vazio foram
  rejeitados na interface, sem consumo de cota. Em uma instância temporária da
  interface real com respostas controladas, HTTP 401 após autenticação retornou
  ao login com “Sua sessão expirou. Entre novamente.”; proxy sem backend exibiu
  mensagem de conexão indisponível; cota zerada desabilitou o envio mesmo com
  áudio selecionado; HTTP 429 no upload exibiu a mensagem de limite diário e
  liberou o formulário após a falha. Os testes de integração com H2 confirmam
  rejeição do sexto arquivo no backend real. As simulações não interromperam o
  backend de uso nem consumiram cota do Gemini.
- **V04 validado:** administrador criou uma conta comum pela interface e o login
  funcionou. A conta não mostrou o menu de administração, recebeu cota própria
  de cinco arquivos e exibiu histórico vazio, sem os jobs do administrador.
  A abertura direta de um job do administrador retornou HTTP 404, sem conteúdo.
  Os 22 testes de `AuthIntegrationTest` foram reexecutados e passaram, incluindo
  bloqueio de cadastro por usuário comum com HTTP 403 e isolamento por dono.
  A conta de QA foi mantida no banco local, sem uploads; o navegador do aplicativo
  ficou autenticado nela ao fim da validação.
- **Verificações automatizadas:** 47 testes isolados do backend e sete testes do
  frontend passaram; build do frontend concluído.
- **V01–V05 concluídos no escopo descrito acima.** O áudio sintético valida o
  fluxo; não mede fidelidade de fala natural. As confirmações manuais se referem
  ao navegador externo do usuário; ampliar compatibilidade faz parte de P07.

## Prioridade 1 — confiabilidade e qualidade

P02 foi implementado em 10/10/2026. A migration V4 foi aplicada no PostgreSQL
local e o backend iniciou validando o schema. A suíte isolada tem 51 testes
aprovados (47 existentes e quatro casos de checkpoint com H2), além dos sete
testes do frontend e build aprovado. Histórico e detalhe foram conferidos com
dados sintéticos de progresso, sem chamadas reais ao Gemini nesta etapa.

P03 foi implementado em 10/10/2026. Os quatro novos testes de autenticação cobrem
proprietário, CSRF, conta alheia, original ausente, estados inválidos, cota cheia
e pedidos concorrentes. A suíte passou com 55 testes do backend e sete do frontend,
além do build. O botão e a transição de erro para fila foram conferidos no
navegador com respostas simuladas; nenhuma chamada real ao Gemini foi feita.

| ID | Proposta | Critério de aceitação |
| --- | --- | --- |
| P01 | Medir fidelidade da transcrição | Comparar áudio/texto de referência, registrar omissões e escolher ajustes com evidência. |
| P02 — entregue | Progresso e checkpoint por parte | Partes confirmadas são reutilizadas; testes com H2 cobrem interrupção, retomada, falha e divisão incompatível. |
| P03 — entregue | Reprocessamento de jobs com erro | Dono reenfileira o mesmo job com original disponível; bloqueio transacional impede pedidos simultâneos e não reserva nova cota de upload. |
| P04 | Contrato de erros uniforme | Respostas previsíveis para validação, segurança e falhas; frontend trata cada causa. |
| P05 | Observabilidade do processamento | Registrar início, fim, fila, tentativas e causa de erro sem expor credenciais ou áudio. |
| P06 | Backups e retenção | Definir retenção de originais com erro e procedimento de backup/restauração de banco e arquivos. |

## Prioridade 2 — experiência e manutenção

| ID | Proposta | Critério de aceitação |
| --- | --- | --- |
| P07 | Acessibilidade e compatibilidade | Conferir teclado, foco, leitor de tela, contraste e gravação nos navegadores escolhidos. |
| P08 | Histórico mais leve | Listagem sem textos completos; consulta de detalhe fornece texto, mantendo recursos da home. |
| P09 | Gestão de contas | Definir troca/recuperação de senha e bloqueio de acesso antes de criar novos endpoints. |
| P10 | Pesquisa e organização | Definir busca, renomeação ou filtros conforme necessidade real dos usuários. |
| P11 | Integração contínua | Executar suíte isolada e build do frontend no CI, sem banco real ou chave Gemini. |

## Prioridade 3 — implantação e expansão

| ID | Proposta | Critério de aceitação |
| --- | --- | --- |
| P12 | Implantação pública | Ambiente definido, HTTPS, cookie seguro, proxy, secrets e dados persistentes. |
| P13 | Medir objetivos de serviço | Coletar indicadores antes de definir SLO/SLA e estimativas de processamento. |
| P14 | Avisos de conclusão | Definir primeiro aviso na interface; canais externos dependem de consentimento e escopo. |
| P15 | Várias instâncias | Coordenação de jobs e armazenamento compartilhado, sem processamento duplicado. |
| P16 | Provedor alternativo | Implementar outro `TranscriptionProvider` e comparar custo, privacidade e fidelidade. |

## Decisões em aberto

- Hospedagem e custos de operação.
- Reprocessamento definido: mesmo job e dono, original disponível, sem nova cota de upload; chamadas restantes continuam sujeitas à cota do provedor.
- Retenção e exclusão de dados.
- Necessidade de timestamps e identificação de falantes.
- Necessidade de notificações externas e canais permitidos.

Atualizar este documento ao concluir trabalhos e revisar os demais contratos quando
uma mudança alterar comportamento, schema ou requisitos de execução.
