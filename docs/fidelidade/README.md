# Avaliação de fidelidade — corpus v1

Cinco referências prontas para gravação em `referencias/`. **Ainda não há medição
com voz real:** os testes do comparador verificam a métrica, não a qualidade do Gemini.

1. Grave cada texto em voz natural, sem ler o nome do arquivo. Use o mesmo microfone
   e ambiente silencioso. Leia números como escritos e faça pausas naturais na amostra 05.
2. Salve cada áudio com o mesmo nome-base da referência, por exemplo
   `01-cotidiano.webm`. Confira a prévia antes de enviar pelo Transcreve.
3. Baixe os cinco `.txt` concluídos em uma pasta local. Não corrija os textos gerados.
   Ajuste apenas o nome do arquivo para corresponder à referência, se necessário.
4. Execute na raiz do projeto (Node 22 ou superior):

```powershell
node tools/fidelity/evaluate.mjs --transcripts "C:\caminho\transcricoes" --output "backend/target/fidelidade/baseline.json" --model "modelo-utilizado"
```

O comando não envia áudio, não chama APIs e não precisa de chave. Cada gravação
enviada pela interface consome a cota normal de upload e as chamadas do provedor.
Use uma conta de teste: o corpus tem cinco arquivos. Confira o modelo configurado
no backend ao preencher `--model`; o relatório registra o valor informado, sem verificá-lo.

O relatório conta substituições, omissões e inserções e calcula WER por amostra
e agregada, ponderada pelo número de palavras de referência. WER menor é melhor;
pode superar 100% se houver muitas palavras extras. Caixa e pontuação são ignoradas,
acentos e números são preservados. Assim, `vinte` e `20` contam como uma troca:
revise essas diferenças manualmente antes de concluir que são erros de significado.
Arquivos ausentes geram relatório incompleto e saída 1, nunca uma medição de sucesso.

Revise especialmente nomes, negações, números e omissões. Anote o ambiente,
microfone, data, modelo e alterações de prompt fora dos logs de produção. Para
comparar ajustes, use os mesmos áudios e compare os relatórios sem selecionar apenas
as melhores amostras. Este corpus pequeno é uma referência inicial, não representa
todos os sotaques, ruídos ou gravações longas. A qualidade de timestamps, identificação
de falantes e pontuação exige avaliação separada.

Não ajustar o prompt a partir de um único erro (`flow`/`flor`). Primeiro produzir a
baseline completa; depois testar um ajuste por vez e registrar a diferença de WER
e os erros críticos. Nenhum ajuste de prompt foi aplicado nesta entrega.
