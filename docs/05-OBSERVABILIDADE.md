# 05 — Observabilidade do processamento

O backend emite eventos de aplicação com campos `chave=valor`. A configuração
do console acrescenta `job` e `parte` a partir do MDC, correlacionando chamadas
do provedor à transcrição sem incluir nomes de arquivos, textos ou credenciais.
São logs consultáveis; ainda não existe dashboard, exportador de métricas ou alerta.

| Evento | Campos principais |
| --- | --- |
| `job_enfileirado` | `job_id`, `origem` (upload/reprocessamento), partes salvas no reprocessamento. |
| `job_inicio` | `job_id`, `fila_ms`, `recuperacao`. |
| `job_partes` | `job_id`, `total`, `salvas`. |
| `parte_inicio`, `parte_fim` | `job_id`, `parte`, `total` no início, `reutilizada` e `duracao_ms` ao terminar. |
| `provedor_tentativa`, `provedor_sucesso` | `operacao`, `tentativa`, duração da chamada bem-sucedida. |
| `provedor_retry` | `operacao`, `tentativa`, `max_tentativas`, `espera_s`. |
| `provedor_falha` | `operacao`, `tentativa`, código e status HTTP quando disponível. |
| `job_fim` | `job_id`, `status`, `duracao_ms`; código e tipo da exceção em falhas. |
| `job_interrompido`, `limpeza_falha` | ID do job e informação operacional sem caminho local. |
| `api_falha` | Status e tipo de falha interna sem mensagem bruta. |

`fila_ms` mede o intervalo desde a última atualização persistida do job até o
início desta execução. Em novos uploads e reprocessamentos, a atualização marca
o enfileiramento. Em recuperação de `PROCESSANDO`, inclui a interrupção e **não
é uma medição pura de fila**: filtre `recuperacao=false` para essa análise.
`duracao_ms` do job usa relógio monotônico, inclui divisão, checkpoints e retries
da execução atual; não inclui a espera anterior em fila nem execuções passadas.
Os logs de fim bem-sucedido são emitidos após persistir o texto e tentar remover
o original. Durações de partes reutilizadas incluem leitura/consolidação, sem API.

Para consultar um arquivo de logs que você capturou localmente:

```powershell
rg 'evento=job_fim|evento=provedor_falha|evento=provedor_retry' caminho/do/backend.log
rg 'UUID-DA-TRANSCRICAO' caminho/do/backend.log
```

Conte conclusões/falhas apenas em `job_fim`; execuções interrompidas têm evento
separado. Uma transcrição pode ter várias execuções e retries, portanto contagem
de eventos não equivale a arquivos únicos. Agrupe por ID conforme a pergunta.
Os IDs também devem ser tratados como dados internos; restrinja acesso e defina
retenção antes de hospedar. O aplicativo não grava arquivo de log automaticamente.
Não ativar logs de payload/headers HTTP para diagnóstico: podem expor chave e áudio.

Validação automatizada verifica eventos de início/parte/falha, duração, correlação
MDC, limpeza do contexto e ausência de caminhos, chave e stack trace. Testes de
retry cobrem limite temporário, cota diária, rede e HTTP do provedor sem API real.
Backups, retenção, métricas persistentes e objetivos de serviço continuam no backlog.
