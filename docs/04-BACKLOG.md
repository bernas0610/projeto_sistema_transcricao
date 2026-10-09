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
- [x] Tratamento específico de cota diária explícita do Gemini.
- [x] Autenticação por sessão, BCrypt e CSRF.
- [x] Limite diário de cinco arquivos por usuário, com reserva transacional.
- [x] Perfis `ADMIN` e `USER`; criação de usuários restrita ao admin.
- [x] Bootstrap do primeiro administrador pelo ambiente.
- [x] Isolamento de transcrições por dono e histórico paginado.
- [x] Repositório organizado em `backend/` e `frontend/`.
- [x] Interface Voz em Flor: login, upload, histórico, gravação, cópia/exportação e administração.
- [x] Testes do backend e do proxy/validações do frontend.
- [x] README e documentos de arquitetura, dados, API e operação.

## Próxima etapa — validar o frontend integrado

O login, logout, navegação, formulário do admin e layout em desktop/celular foram
conferidos no navegador. Upload e gravação existem no código, mas sua validação
real ponta a ponta pelo frontend ainda está pendente.

| ID | Trabalho | Critério de conclusão |
| --- | --- | --- |
| V01 | Upload real pelo navegador | Áudio curto chega ao backend, progresso de envio funciona, status conclui e texto abre. |
| V02 | Captura pelo microfone | Permissão, gravação, parada, reprodução, envio e liberação do microfone funcionam. |
| V03 | Cópia e exportação | Texto é copiado e `.txt` contém acentuação e conteúdo esperado. |
| V04 | Conta comum | Cadastro pelo admin funciona; conta comum não vê administração nem jobs alheios. |
| V05 | Falhas e cotas na interface | Sessão expirada, backend indisponível, arquivo inválido e sexto envio têm resposta clara. |

Usar conta e áudios de teste; chamadas reais ao Gemini consomem a cota do provedor.

## Prioridade 1 — confiabilidade e qualidade

| ID | Proposta | Critério de aceitação |
| --- | --- | --- |
| P01 | Medir fidelidade da transcrição | Comparar áudio/texto de referência, registrar omissões e escolher ajustes com evidência. |
| P02 | Progresso e checkpoint por parte | Persistir partes concluídas e retomar sem repetir partes já confirmadas. |
| P03 | Reprocessamento de jobs com erro | Ação autenticada, sem execução duplicada; regra de cota definida e testada. |
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
- Política para reprocessamento e cota em jobs com erro.
- Retenção e exclusão de dados.
- Necessidade de timestamps e identificação de falantes.
- Necessidade de notificações externas e canais permitidos.

Atualizar este documento ao concluir trabalhos e revisar os demais contratos quando
uma mudança alterar comportamento, schema ou requisitos de execução.
