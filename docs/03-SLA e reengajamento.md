# 03 — SLA e reengajamento

[← Contrato da API](02-CONTRATO%20DA%20API.md) · [Backlog →](04-BACKLOG.md)

## Situação atual

O projeto é local e está em desenvolvimento. **Não há SLA contratado, garantia de
disponibilidade ou prazo máximo de transcrição.** Este documento registra os
limites implementados e as propostas para medir qualidade e orientar o retorno
do usuário ao fluxo. As propostas não representam funcionalidades entregues.

## Comportamento implementado

| Aspecto | Comportamento atual |
| --- | --- |
| Aceitação | Upload registrado retorna `202`; conclusão ocorre em segundo plano. |
| Paralelismo | Uma thread de processamento por padrão. |
| Atualização da interface | Consulta a cada 5 segundos com jobs em andamento na página atual; nos demais casos, a cada 60 segundos. |
| Cota do usuário | Cinco arquivos/dia; renovação lógica à meia-noite em `America/Sao_Paulo`. |
| Cota do provedor | Compartilhada, dependente de conta e modelo; independente da cota por arquivo. |
| FFmpeg | Timeout de 30 minutos por execução, configurado em `app.ffmpeg.timeout-minutos`. |
| Cliente Gemini | Timeout de leitura de 20 minutos e conexão de 30 segundos na configuração atual. |
| Retry | Até cinco tentativas para falhas elegíveis; cota diária explícita encerra o job. |
| Reinício | Recupera `PENDENTE` e `PROCESSANDO`, reutilizando partes confirmadas no banco; `ERRO` pode ser reenfileirado pelo dono, com o original disponível. |
| Sessão | Reiniciar o backend exige novo login. |

Timeouts por operação não formam um limite total do job. O tempo final depende
de fila, duração do áudio, quantidade de partes, retries e resposta do provedor.
O percentual de upload não é percentual de transcrição.

## Reengajamento no fluxo atual

Aqui, reengajamento significa ajudar a pessoa a voltar ao trabalho iniciado ou
entender por que ele parou; não significa enviar campanhas ou mensagens externas.

| Situação | O que a aplicação faz hoje | Ação disponível ao usuário |
| --- | --- | --- |
| Página fechada durante o processamento | Backend continua enquanto a aplicação estiver ativa. | Reabrir o site e consultar o histórico. |
| Backend reiniciado | Reenfileira jobs recuperáveis e perde as sessões locais. | Entrar novamente e acompanhar o mesmo job. |
| Job concluído | Mostra resultado no histórico e, quando presente entre os recentes, na tela inicial. | Ler, copiar e exportar. |
| Cota de arquivos esgotada | Bloqueia novos envios; frontend informa o uso diário. | Voltar após a renovação do dia. |
| Cota diária do Gemini esgotada | Encerra o job em `ERRO`, preservando o original. | Consultar a mensagem; não há retomada automática no dia seguinte. |
| Outras falhas | Mostra o status e o motivo disponível. | Corrigir a causa e enviar novamente; um novo upload aceito conta na cota. |

Não existem e-mail, push, lembretes, tarefas de contato ou acompanhamento da
inatividade. A aplicação não envia notificações fora da sessão aberta.

## Indicadores propostos — ainda não implementados

| Indicador | Definição proposta | O que falta |
| --- | --- | --- |
| Tempo em fila | Intervalo entre aceitação e início efetivo. | Registrar `iniciado_em`. |
| Tempo de processamento | Intervalo entre início e término do job. | Registrar término e duração do áudio. |
| Taxa de conclusão | Jobs concluídos sobre jobs finalizados em uma janela definida. | Métricas e classificação das causas de falha. |
| Tempo de recuperação | Intervalo entre reinício e retomada dos jobs recuperáveis. | Métricas específicas de recuperação. |
| Latência da API | Percentis de resposta separados por endpoint, fora do envio dos bytes do áudio. | Instrumentação e amostra representativa. |

`criado_em` e `atualizado_em` sozinhos não distinguem fila, processamento e retries.
Antes de fixar um objetivo de serviço, medir cargas reais de 30 segundos, 15,
60 e 90 minutos e definir ambiente, concorrência e janela de observação.
Ainda não foi definido um percentual de uptime nem um prazo prometido.

## Evolução proposta de reengajamento

1. Dar orientações específicas para cota, conexão, arquivo inválido e falha do provedor.
2. Permitir reprocessar um job com segurança, definindo o impacto na cota e evitando duplicidade.
3. O progresso por parte já é exibido; acrescentar estimativa somente quando houver medição confiável.
4. Avaliar aviso de conclusão dentro da interface; notificações externas dependem
   de escopo, consentimento, canal e política de dados definidos.

Essas evoluções estão no [backlog](04-BACKLOG.md). O documento deve ser atualizado
quando houver métricas, implantação pública ou funcionalidades de notificação.
