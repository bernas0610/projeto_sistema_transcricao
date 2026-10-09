# projeto_sistema_transcricao

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
