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
