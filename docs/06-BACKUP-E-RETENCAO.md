# 06 — Backup, restauração e retenção

## Política local

- Áudios concluídos continuam sendo removidos pelo processador.
- Originais de jobs com erro têm retenção sugerida de **30 dias**, contada desde
  `atualizado_em`. O prazo é ajustável por `--days`. Reprocessar atualiza a data.
- A limpeza é **manual**, simulada por padrão e exige backup íntegro com cópia de
  cada original elegível. Não existe exclusão automática ao iniciar o aplicativo.
- Banco, contas, textos e checkpoints não são removidos pela retenção de áudio.
- Backups são mantidos até exclusão manual. Faça um antes de manutenção/limpeza
  e após sessões importantes. Mantenha ao menos uma cópia em outro dispositivo;
  o backup local no mesmo disco não protege contra perda desse disco.

O script é `tools/backup/transcreve_backup.py`, Python 3.11 ou superior, com
`pg_dump`, `pg_restore`, `psql` e `createdb` da mesma versão principal do servidor
ou compatível. No Windows ele procura em `PATH`, `PG_BIN` e nas pastas instaladas
em `Program Files/PostgreSQL`. Para instalação em outro lugar, configure `PG_BIN`.
Senha por `PGPASSWORD`, `DB_PASSWORD` ou `.pgpass`; nunca coloque a senha no comando.

## Criar e verificar

Pare o backend e todas as outras instâncias/escritores do banco e uploads.
O script recusa a operação se a porta do backend estiver acessível. Esse teste
de porta é uma proteção auxiliar: informar uma URL errada não prova que o serviço
foi parado. Se usa outra porta/endereço, informe `--backend-url` corretamente.
O frontend pode continuar aberto e ficará sem API durante a manutenção.

Na raiz do projeto, com a senha já configurada no ambiente:

```powershell
python tools/backup/transcreve_backup.py backup --uploads uploads --output backups/2026-10-10-inicial
python tools/backup/transcreve_backup.py verify --backup backups/2026-10-10-inicial
```

Use uma pasta nova a cada execução; backups existentes nunca são sobrescritos.
A pasta contém `database.dump` (formato custom do PostgreSQL), `audio/` e
`manifest.json` com hashes SHA-256. O dump inclui esquema, contas, cotas, textos,
status, checkpoints e histórico de migrations. São copiados somente originais de
`ERRO`, `PENDENTE` e `PROCESSANDO`; partes temporárias são regeneradas pelo FFmpeg.
Um original ausente em job com erro é registrado como ausente; em job em andamento
isso impede concluir o backup. Falha/interrupção deixa pasta incompleta: não a use
para restauração ou limpeza. O manifesto completo só é escrito ao concluir.

Hashes detectam corrupção, não autenticam a origem. Restaure somente seus backups
de fonte confiável: um dump PostgreSQL pode executar SQL. Dumps incluem dados e
hashes de senha; mantenha a pasta privada. Não há criptografia automática nem upload
para nuvem. As pastas `backups/` e `restored-uploads/` são ignoradas pelo Git.

## Testar a restauração

Ainda com backend parado:

```powershell
python tools/backup/transcreve_backup.py restore --backup backups/2026-10-10-inicial --database transcreve_restore_20261010 --uploads restored-uploads/20261010
```

O script aceita apenas **banco novo com prefixo `transcreve_restore_`** e pasta de
uploads nova. `createdb` recusa banco existente; não usa `DROP` nem `--clean`.
Verifica o arquivo antes de restaurar, copia os originais e reescreve os caminhos
dos jobs para a nova pasta. Assim a recuperação não depende do caminho antigo.
Um erro durante restauração pode deixar banco/pasta parcialmente preenchidos;
investigue e escolha outro nome novo para repetir, sem sobrescrever essa tentativa.

Antes de iniciar qualquer processador nessa cópia, compare quantidades de contas,
jobs e checkpoints, abra uma transcrição concluída e confira hashes dos originais.
Jobs recuperáveis podem disparar chamadas ao Gemini ao iniciar o aplicativo.
Para adotar a cópia após perda do ambiente, configure `SPRING_DATASOURCE_URL`
com o banco restaurado e `APP_UPLOAD_DIR` com o caminho absoluto dos novos uploads.
Confirme as migrations e faça login novamente. A API key, senha do banco, configurações
de ambiente e sessões não fazem parte do backup.

## Simular e aplicar retenção

```powershell
python tools/backup/transcreve_backup.py prune --uploads uploads --days 30
```

Somente jobs em `ERRO` anteriores ao corte são elegíveis. `PENDENTE` e
`PROCESSANDO` ficam fora da limpeza. Para aplicar, faça primeiro um backup novo
com o backend parado, confira-o e passe sua pasta:

```powershell
python tools/backup/transcreve_backup.py prune --uploads uploads --days 30 --apply --backup backups/PASTA-DO-BACKUP-NOVO
```

Todos os caminhos são resolvidos dentro da raiz de uploads e todos os originais
elegíveis precisam coincidir com os hashes do backup, do mesmo banco/host/porta,
antes da primeira exclusão. A limpeza remove apenas esses arquivos, preserva
jobs/textos/checkpoints e marca `ORIGINAL_AUSENTE`. Caso haja interrupção entre
remoção e atualização do banco, o job permanece consultável e o endpoint de
reprocessamento verifica a ausência do arquivo. Não existe rollback transacional
entre banco e filesystem; o backup permite recuperação desse caso.

## Validação realizada em 10/10/2026

Oito testes passaram, incluindo round trip no PostgreSQL 18 local: esquema,
conta/cota, job com erro, progresso, duração da divisão, checkpoint com acentos e
bytes do áudio preservados. A retenção real foi testada somente em banco e pasta
temporários; preservou o checkpoint. Também foram cobertos corrupção, caminho
fora da raiz, backend ativo, recusa do banco de uso e simulação sem exclusão.

Foi criado o primeiro backup local em `backups/2026-10-10-inicial`.
Ele foi restaurado em banco isolado e as quantidades de
contas, jobs, checkpoints e partes foram conferidas com o banco de uso. O banco
de verificação foi removido após a conferência; o backup permanece guardado.
Nenhum áudio de uso foi removido e nenhuma chamada ao Gemini foi feita.

Testes isolados: `python -m unittest discover -s tools/backup -v`.
O teste real de PostgreSQL é opcional, habilitado por `RUN_PG_BACKUP_TESTS=1`, e
cria/remove somente bancos próprios com prefixo `transcreve_restore_`. Execute-o
com backend parado e credenciais de teste. No CI padrão esse caso é ignorado.
