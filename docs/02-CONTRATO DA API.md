# 02 — Contrato da API

[← Modelo de dados](01-MODELO%20DE%20DADOS.md) · [SLA e reengajamento →](03-SLA%20e%20reengajamento.md)

## Convenções

Base local do backend: `http://localhost:8080`. Pelo frontend, os mesmos caminhos
são encaminhados em `http://localhost:5173`. Não há prefixo `/api` ou versionamento
na URL. A autenticação é por sessão, sem JWT.

JSON usa nomes camelCase. IDs são UUIDs; datas de transcrição são timestamps
ISO 8601. Os exemplos abaixo são fictícios e não representam contas reais.

## Endpoints

| Método | Caminho | Acesso | Sucesso |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | Público | `200` |
| POST | `/auth/login` | Público, com CSRF | `204` |
| GET | `/auth/me` | Autenticado | `200` |
| POST | `/auth/cadastro` | Admin, com CSRF | `201` |
| POST | `/auth/logout` | Com sessão e CSRF | `204` |
| POST | `/transcricoes` | Autenticado, com CSRF | `202` |
| GET | `/transcricoes?pagina=0` | Autenticado | `200` |
| GET | `/transcricoes/{id}` | Dono | `200` |

## Autenticação e CSRF

1. Faça `GET /auth/csrf`, preservando cookies. A resposta contém `token`,
   `headerName` e `parameterName`; use os valores retornados, sem fixar o token.
2. Envie o token no header indicado em todos os POSTs, inclusive login e logout.
3. Faça login com `application/x-www-form-urlencoded` e os campos `email` e `password`.
4. Após login bem-sucedido, obtenha um novo token CSRF: o anterior é renovado.
5. Preserve `JSESSIONID` nas requisições seguintes.
6. Após logout, obtenha novo token antes de outro login.

Exemplo de corpo do login:

```text
email=pessoa%40example.com&password=senha_de_exemplo
```

Login bem-sucedido e logout retornam `204` sem corpo. Login inválido retorna `401`.
O cookie é `HttpOnly` e `SameSite=Lax`; reiniciar o backend exige novo login.

## Conta atual e cadastro

`GET /auth/me` e `POST /auth/cadastro` retornam o mesmo formato de conta:

```json
{
  "id": "2b3a3cfe-7d26-4a9f-99c6-06a9f3b62a20",
  "email": "pessoa@example.com",
  "role": "USER",
  "limiteArquivosDiario": 5,
  "arquivosEnviadosHoje": 0
}
```

Para cadastrar, um admin envia `Content-Type: application/json`:

```json
{
  "email": "pessoa@example.com",
  "senha": "senha_inicial_de_exemplo"
}
```

O e-mail é obrigatório, válido e tem até 255 caracteres. A senha exige 8 a 72
caracteres e no máximo 72 bytes UTF-8. E-mail duplicado retorna `409`.
O endpoint sempre cria `USER`; enviar `role` não concede outro perfil.
Cadastrar não autentica automaticamente a nova conta.

Não há cadastro público, alteração de senha, promoção ou exclusão de conta.
O primeiro admin é criado por configuração de ambiente, conforme o README.

## Criar transcrição

`POST /transcricoes` recebe `multipart/form-data` com o campo `arquivo`.
Ao montar multipart, deixe o cliente HTTP definir o boundary. Inclua sessão e CSRF.

Extensões: MP3, WAV, M4A, OGG, FLAC, AAC, WEBM, OPUS e MPEG. Arquivo vazio ou
extensão inválida retorna `400`; conteúdo sem áudio pode ser aceito no upload e
falhar posteriormente no processamento. O limite de arquivo e de requisição é
300 MB, incluindo o envelope da requisição multipart.

Resposta `202 Accepted`, com header `Location: /transcricoes/{id}`:

```json
{
  "id": "7c350689-d41a-4cc1-b4a8-3d3f0e915d5a",
  "nomeArquivoOriginal": "aula.mp3",
  "status": "PENDENTE",
  "texto": null,
  "mensagemErro": null,
  "codigoErro": null,
  "erroRepetivel": false,
  "totalPartes": 0,
  "partesConcluidas": 0,
  "criadoEm": "2026-10-09T16:00:00Z",
  "atualizadoEm": "2026-10-09T16:00:00Z"
}
```

`202` confirma a aceitação, não a conclusão. O sexto upload aceito do dia retorna
`429`. A API não recebe ID de dono do cliente: usa a conta autenticada.

## Consultar e listar

`GET /transcricoes/{id}` retorna o mesmo formato do upload. Status possíveis:
`PENDENTE`, `PROCESSANDO`, `CONCLUIDA` e `ERRO`. A transcrição concluída inclui `texto`;
uma falha inclui `mensagemErro`. O caminho do arquivo no servidor não é retornado.

`totalPartes` começa em zero e é definido após a divisão. `partesConcluidas`
conta trechos confirmados no banco; pode continuar positivo em um job com erro.
Em retomadas após reinício, esses trechos são reutilizados sem nova chamada ao
provedor. O texto parcial de cada parte não é retornado pela API.

ID inexistente ou pertencente a outra conta retorna `404`, inclusive para admins.
Não existe endpoint de download do áudio original. Copiar e
exportar `.txt` são operações do frontend sobre o texto recebido.

`GET /transcricoes?pagina=0` retorna:

```json
{
  "itens": [],
  "pagina": 0,
  "totalPaginas": 0,
  "total": 0
}
```

Quando há resultados, `itens` contém apenas metadados: `id`, `nomeArquivoOriginal`,
`status`, `mensagemErro`, `codigoErro`, `erroRepetivel`, `totalPartes`,
`partesConcluidas`, `criadoEm` e `atualizadoEm`. O campo `texto` não está presente.
A consulta usa projeção no banco, sem carregar textos completos ou caminhos.
O detalhe `GET /transcricoes/{id}` mantém o texto integral. A home busca apenas
o detalhe da última concluída e reutiliza esse resultado durante a sessão. O tamanho é fixo em 20; a ordenação é `criadoEm DESC, id DESC`. Página
negativa retorna `400`; o índice começa em zero.

## Reprocessar uma transcrição

`POST /transcricoes/{id}/reprocessar`, sem corpo, exige sessão do dono e token
CSRF. Responde `202 Accepted`, com `Location` e o mesmo DTO de transcrição em
`PENDENTE`. Apenas jobs em `ERRO`, com original disponível no disco, são aceitos.
O ID, os checkpoints e a duração dos segmentos são preservados. A mensagem de
erro é limpa; não há novo upload nem consumo adicional da cota de arquivos.
Chamadas ao provedor para partes faltantes continuam sujeitas aos limites dele.

Um bloqueio de registro serializa pedidos simultâneos: o primeiro marca
`PENDENTE` antes de enfileirar, e os seguintes recebem `409` enquanto o job
estiver pendente, processando ou concluído. Original indisponível também retorna
`409`; job de outro dono ou inexistente retorna `404`, inclusive para admin.

## Falhas e respostas

| Código | Casos relevantes |
| --- | --- |
| `400` | Entrada inválida, arquivo vazio/extensão inválida, página negativa ou UUID malformado. |
| `401` | Sessão ausente/expirada ou login inválido. |
| `403` | Permissão insuficiente ou proteção CSRF rejeitada. |
| `404` | Job inexistente ou de outra conta. |
| `409` | E-mail já cadastrado; reprocessamento de job fora de `ERRO` ou sem original. |
| `413` | Limite de multipart excedido. |
| `429` | Cota diária do usuário atingida na criação. |
| `500` | Falha interna, como erro ao persistir ou salvar arquivo. |
| `503` | Proxy frontend não consegue conectar ao backend. |

Falhas da API e dos filtros de segurança retornam o envelope abaixo. O proxy
também usa esse contrato quando o backend está indisponível. Respostas produzidas
por infraestrutura externa ainda podem não incluir JSON; clientes mantêm fallback
por status. Motivos técnicos, stack traces, respostas brutas do Gemini e caminhos
locais não são publicados.

```json
{"status":429,"code":"COTA_UPLOAD","message":"Você atingiu o limite de arquivos de hoje. Tente novamente amanhã.","retryable":true}
```

`code` é estável para tratamento pelo cliente; `message` é orientação pública.
`retryable` indica que a condição pode desaparecer com o tempo, não autoriza loop
imediato de retry. Códigos HTTP: `DADOS_INVALIDOS`, `NAO_AUTENTICADO`,
`ACESSO_NEGADO`, `NAO_ENCONTRADO`, `METODO_INVALIDO`, `CONFLITO`,
`ARQUIVO_GRANDE`, `FORMATO_INVALIDO`, `COTA_UPLOAD`, `ERRO_INTERNO` e
`SERVICO_INDISPONIVEL` para 400, 401, 403, 404, 405, 409, 413, 415, 429, 500 e 503.

Uma falha assíncrona permanece consultável por HTTP `200`, com `status: "ERRO"`,
`mensagemErro`, `codigoErro` e `erroRepetivel`. Este último é uma orientação sobre
a causa; a aceitação do endpoint de reprocessamento continua dependendo de dono,
estado e original disponível.

| `codigoErro` | Orientação |
| --- | --- |
| `COTA_PROVEDOR_DIARIA` | Aguardar renovação da cota do serviço. |
| `LIMITE_PROVEDOR` | Aguardar alguns minutos. |
| `PROVEDOR_INDISPONIVEL` | Reprocessar mais tarde após indisponibilidade/rede/timeout. |
| `RESPOSTA_PROVEDOR` | Provedor não retornou conteúdo utilizável; tentar mais tarde. |
| `CONFIGURACAO_PROVEDOR`, `REQUISICAO_PROVEDOR` | Administrador precisa conferir configuração/pedido. |
| `AUDIO_INVALIDO` | Conferir e enviar áudio válido. |
| `ORIGINAL_AUSENTE` | Novo envio necessário. |
| `PROCESSAMENTO_AUDIO`, `CHECKPOINT_INCOMPATIVEL`, `ERRO_INTERNO` | Administrador precisa investigar antes de reprocessar. |

O código e a mensagem são limpos ao reenfileirar. A migration V5 sanitiza
mensagens de jobs antigos com erro, classificando-os como `ERRO_INTERNO`.

Fontes: [controllers](../backend/src/main/java/com/bernardo/transcricao/controller/),
[DTOs](../backend/src/main/java/com/bernardo/transcricao/dto/) e
[Spring Security](../backend/src/main/java/com/bernardo/transcricao/config/SecurityConfig.java).
