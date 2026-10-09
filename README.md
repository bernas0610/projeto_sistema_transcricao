# projeto_sistema_transcricao

## Estrutura do repositório

```text
projeto_sistema_transcricao/
├── backend/
│   ├── .mvn/wrapper/
│   ├── src/
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
├── .gitattributes
├── .gitignore
└── README.md
```

O projeto Spring Boot e o Maven Wrapper ficam em `backend/`. Execute os comandos
Maven a partir dessa pasta. O front-end será adicionado separadamente.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Configure `DB_PASSWORD` e `GEMINI_API_KEY` no ambiente de execução. No IntelliJ,
importe `backend/pom.xml` e use `backend/` como diretório de trabalho.

Os uploads persistentes ficam, por padrão, em `uploads/` na raiz do repositório,
fora do Git. O backend acessa essa pasta como `../uploads`, preservando os dados
e os caminhos absolutos já gravados no banco antes da reorganização. Para usar
outro local, configure `APP_UPLOAD_DIR`, preferencialmente com um caminho absoluto.
Relatórios locais de testes anteriores em `target/` também não são versionados;
novos builds e relatórios ficam em `backend/target/`.

## Recuperação após reinício

Ao terminar a inicialização, a aplicação reenfileira as transcrições `PENDENTE`
e `PROCESSANDO`, da mais antiga para a mais recente, no executor já usado pelos
uploads. Transcrições `CONCLUIDA` e `ERRO` não são retomadas.

O processamento recomeça do início: partes temporárias antigas são removidas e
o áudio original é dividido novamente. Chamadas ao Gemini feitas antes da queda
podem ser repetidas e consumir cota. Ainda não há progresso salvo por parte.
Mantenha o diretório de uploads persistente e use o mesmo diretório de trabalho
nos reinícios, pois os caminhos dos arquivos podem ser relativos.

Se o original estiver ausente, o job passa para `ERRO` com uma mensagem explicativa.
Uma interrupção da thread durante o desligamento preserva o status recuperável;
o original só é apagado depois de salvar a transcrição concluída.

Esta recuperação pressupõe **uma única instância** da aplicação. Para executar
várias instâncias no mesmo banco, será necessário coordenar a posse dos jobs.

## Limites do Gemini

Um HTTP 429 com uma violação de cota diária explícita (`QuotaFailure`, identificador
ou métrica contendo `PerDay`/`per_day`) encerra a operação imediatamente. Mensagens
explícitas como `daily quota` e `daily limit` também são reconhecidas. O job fica
em `ERRO` com uma mensagem sobre a cota e o áudio original é preservado.
Ele não é reenfileirado automaticamente quando a cota renova.

Limites por minuto e respostas 429 sem indicação clara de cota diária mantêm o
retry de até cinco tentativas. A presença de `retryDelay` não torna uma cota
diária temporária. Falhas de rede e HTTP 5xx também mantêm as tentativas existentes.

Os limites são do projeto/modelo no Gemini e devem ser consultados no AI Studio;
não há um número diário fixo no código. Consulte a
[documentação de limites](https://ai.google.dev/gemini-api/docs/rate-limits).

## Contas e limite por usuário

Novos uploads e consultas exigem login. Cada transcrição nova pertence ao usuário
autenticado; consultar um ID de outro usuário retorna `404`. Transcrições anteriores
à migration V2 continuam no banco, sem dono, e não podem ser consultadas pela API.
Para disponibilizá-las, associe seus IDs explicitamente a uma conta pelo banco.
A recuperação após reinício continua processando os jobs antigos.

O padrão é **5 arquivos enviados por usuário por dia**, configurável em
`app.uso.limite-arquivos-diario`. O dia renova à meia-noite no fuso
`app.uso.fuso` (padrão: `America/Sao_Paulo`). O sexto upload retorna `429`;
uploads inválidos ou falhas no registro não consomem a cota. Uma transcrição
aceita conta mesmo que posteriormente falhe no processamento. Recuperar o mesmo
job após reinício não conta como outro upload. A reserva usa bloqueio no banco
para proteger contra uploads simultâneos.

Essa cota por arquivo é independente da cota compartilhada do Gemini: um arquivo
pode exigir várias chamadas, conforme sua duração. Cinco arquivos aceitos não
garantem disponibilidade de cota para transcrevê-los no mesmo dia.

### Fluxo de autenticação

A autenticação usa sessão (`JSESSIONID`), senhas BCrypt e proteção CSRF mantida
pelo Spring Security. O cliente deve preservar o cookie entre as requisições.

1. `GET /auth/csrf`: retorna `token`, `headerName` e `parameterName`. Envie o token
   no header indicado em todos os POSTs, incluindo cadastro, login e logout.
2. `POST /auth/cadastro`: JSON `{"email":"voce@example.com","senha":"senha12345"}`.
   Retorna `201`; e-mail duplicado retorna `409`. A senha precisa ter 8 a 72 caracteres
   e no máximo 72 bytes UTF-8. Cadastrar não efetua login automaticamente.
3. `POST /auth/login`: formulário `application/x-www-form-urlencoded` com
   `email` e `password`. Retorna `204` no sucesso ou `401` para credenciais inválidas.
4. Consulte `/auth/csrf` novamente após o login, pois o token anterior é renovado.
5. `GET /auth/me`: retorna ID, e-mail, `limiteArquivosDiario` e `arquivosEnviadosHoje`.
6. `POST /transcricoes`: multipart com `arquivo`, cookie de sessão e header CSRF.
   `GET /transcricoes/{id}` consulta somente uma transcrição da própria conta.
7. `POST /auth/logout`: encerra a sessão (`204`). Obtenha outro token antes de novo login.

Em produção, sirva a aplicação em HTTPS e configure
`server.servlet.session.cookie.secure=true`. Sessões são locais à aplicação e
precisam de um novo login após reiniciá-la; os jobs persistidos continuam recuperáveis.

### Testes locais sem Gemini ou PostgreSQL

Os testes de autenticação executam as migrations em H2 com modo PostgreSQL e
substituem o processador e o provedor para não consumir cota externa:

```powershell
cd backend
.\mvnw.cmd '-Dtest=AuthIntegrationTest,GeminiRetryTest,TranscricaoRecoveryTest,TranscricaoProcessorTest,AudioServiceTest' test
```
