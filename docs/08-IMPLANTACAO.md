# Implantação — P12

## Estado

Pacote de implantação preparado. O site público ainda depende da criação da conta,
da VM, do hostname e da validação no ambiente real. Não há recursos pagos contratados.
Arquivos: `deploy/compose.yml`, `deploy/Caddyfile`, Dockerfiles e `tools/deploy/smoke.py`.

## Ambiente escolhido

Uma VM Ubuntu ARM64 na Oracle Cloud, shape **VM.Standard.A1.Flex**, marcada como
Always Free, na região principal da conta. A oferta consultada em 10/10/2026 tem
franquia conjunta de 2 OCPUs/12 GB RAM e 200 GB de volumes, incluindo boot.
Proposta: uma VM de 2 OCPUs, 12 GB e boot de 50 GB, sem load balancer nem banco
gerenciado. Conferir a indicação de gratuidade na conta antes de criar.
Não confundir recursos Always Free com créditos temporários do trial.

Há falta de capacidade em algumas regiões e a Oracle pode recolher VMs ociosas.
Manter cópias fora da VM; não há garantia de disponibilidade dessa oferta.
O cadastro pode pedir verificação de cartão; o usuário faz essa etapa diretamente
no provedor. Não enviar cartão, senha da conta ou chave SSH privada pelo chat.

Fontes: [oferta Oracle](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm),
[cadastro](https://www.oracle.com/cloud/free/).
O Render gratuito não foi escolhido: não tem disco persistente e o PostgreSQL gratuito
expira em 30 dias ([limites](https://render.com/docs/free)).

Para não comprar domínio, criar um subdomínio gratuito no
[DuckDNS](https://www.duckdns.org/about.jsp) e apontá-lo para o IP público da VM.
O token DuckDNS não vai para o repositório. Atualizar o DNS se o IP mudar.

## Arquitetura e dados

Internet → Caddy HTTPS → backend ou arquivos estáticos. Apenas portas 80 e 443
publicadas; PostgreSQL e backend ficam na rede interna do Compose. O frontend usa
a mesma origem da API. Caddy define os cabeçalhos encaminhados, e o perfil `prod`
usa cookie Secure/HttpOnly/SameSite=Lax. CSRF permanece obrigatório.

Volumes nomeados guardam PostgreSQL 18 (`/var/lib/postgresql`), originais
(`/data/uploads`) e certificados Caddy. O backend roda com UID 10001, tem FFmpeg
e apenas uma instância. Não aumentar réplicas: recuperação e checkpoint ainda não
coordenam múltiplos processadores. Sessões estão em memória e exigem novo login
após reiniciar o backend. Jobs incompletos são retomados pelos checkpoints.

Imagens têm tags de versão principal; aplicar atualizações deliberadamente e
validar antes de usar. Backup de volume não substitui backup lógico do banco.

## Criar e instalar

1. Criar conta Oracle e VM gratuita na região principal, com Ubuntu ARM64,
   subnet pública, IP público e par SSH. Guardar a chave privada localmente.
2. Na regra de rede Oracle, permitir TCP 80 e 443 da internet; restringir SSH
   TCP 22 ao IP do administrador. UDP 443 é opcional para HTTP/3.
3. Conferir também o firewall Ubuntu. Não remover regras sem entender seu efeito.
   Instalar Docker Engine e Compose conforme a
   [documentação Ubuntu](https://docs.docker.com/engine/install/ubuntu/).
4. Criar o hostname DuckDNS apontando para o IP e conferir a resolução pública.
5. No servidor, clonar o repositório em uma pasta própria:

```bash
git clone https://github.com/bernas0610/projeto_sistema_transcricao.git
cd projeto_sistema_transcricao/deploy
cp .env.example .env
chmod 600 .env
```

Editar `.env` no servidor: `SITE_ADDRESS` recebe apenas o hostname (exemplo:
`meu-transcreve.duckdns.org`); preencher senha forte do banco, chave Gemini e
email/senha forte do primeiro administrador. Aspas simples no `.env` evitam
interpolação de `$` em senhas. Não imprimir `docker compose config` com secrets reais.
Variáveis faltantes impedem a subida. `ADMIN_PASSWORD` só cria o primeiro
administrador quando ainda não existe nenhum ADMIN; mudar o arquivo não troca
a senha já cadastrada.

```bash
docker compose --env-file .env -f compose.yml config --quiet
docker compose --env-file .env -f compose.yml up -d --build --wait
docker compose -f compose.yml ps
```

Caddy emite e renova o certificado automaticamente quando DNS e portas estão
corretos ([HTTPS](https://caddyserver.com/docs/automatic-https)). Abrir
`https://SEU_HOSTNAME`. Não usar `down -v`: isso apaga os volumes.

## Conferir antes de considerar publicado

No navegador externo, conferir login, upload, progresso, copiar/exportar,
reprocessamento e gravação com microfone (exige HTTPS). Fazer uma gravação curta
com consentimento e conferir o resultado; essa operação consome cota Gemini.
O smoke automático não faz uploads nem chama Gemini:

```bash
# ADMIN_EMAIL e ADMIN_PASSWORD devem estar no ambiente deste terminal.
python3 ../tools/deploy/smoke.py https://SEU_HOSTNAME
```

As credenciais devem ser fornecidas localmente sem aparecer no histórico do shell.
O teste exige certificado confiável, sessão segura, CSRF, histórico sem texto completo
e logout. Após reiniciar containers, conferir que transcrições/checkpoints e originais
pendentes continuam disponíveis. Fazer backup e uma restauração isolada antes de
armazenar aulas que não tenham outra cópia.

## Backup no Compose

Em janela de manutenção, parar **web e backend** e confirmar que não há outros
escritores. O serviço `backup` é ferramenta manual, não inicia o processador.

```bash
docker compose -f compose.yml stop web backend
docker compose -f compose.yml run --rm backup backup --host db --user transcreve --database transcricao --uploads /data/uploads --output /backups/AAAA-MM-DD
docker compose -f compose.yml run --rm backup verify --backup /backups/AAAA-MM-DD
docker compose -f compose.yml up -d --wait
```

O diretório de saída deve ser novo. Copiar `deploy/data/backups/AAAA-MM-DD` para
outro equipamento e restringir acesso: contém banco com hashes de senhas e textos.
O guard de porta do CLI roda no container da ferramenta; **não detecta outro
backend no cluster**, por isso o comando `stop` e a confirmação dos escritores
são indispensáveis. Nunca executar backup/prune junto ao backend ativo.

Para ensaiar restauração, com os escritores parados, usar outro banco e pasta:

```bash
docker compose -f compose.yml run --rm backup restore --host db --user transcreve --database transcreve_restore_ensaio --uploads /restores/ensaio --backup /backups/AAAA-MM-DD
```

Não aponta a aplicação para esse banco automaticamente. A ferramenta reescreve
caminhos; seguir [P06](06-BACKUP-E-RETENCAO.md) para verificar e planejar recuperação.
Antes de usar originais restaurados no backend, ajustar o dono dos arquivos para
UID/GID 10001 e montar a pasta restaurada no caminho gravado no banco; a ferramenta
de restauração roda como root. O ensaio não altera o volume de uploads em uso.
Retenção permanece manual, padrão de 30 dias, após simulação e backup conferido.

## Atualização e recuperação

Fazer backup offline, guardar o commit anterior, atualizar o checkout e executar
`docker compose up -d --build --wait`. O volume continua preservado. Se houver
migração de schema, voltar apenas a imagem pode ser incompatível: conferir a
migração e restaurar banco/arquivos em ambiente isolado antes de substituir dados.
Não expor logs, `.env`, backups ou uploads pela web.

P12 só estará concluída quando houver URL real, certificado válido, smoke passando
e fluxo de áudio verificado no servidor. Conta/VM/DNS estão pendentes do usuário.

## Acesso temporário pela internet (Cloudflare Quick Tunnel)

Enquanto a Oracle não tiver capacidade para o A1, o sistema local pode ser
acessado em outro computador pelo navegador, sem instalar Java ou Node nele.
O computador servidor precisa permanecer ligado, com backend, frontend e túnel
ativos. Esse acesso temporário não conclui a implantação P12 na Oracle.

Baixar o `cloudflared` somente pela [documentação oficial](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/).
Com o frontend respondendo em `http://127.0.0.1:5173`, executar no PowerShell:

```powershell
cloudflared tunnel --url http://127.0.0.1:5173 --no-autoupdate
```

Se o executável estiver numa pasta local, usar seu caminho no lugar de
`cloudflared`. Manter o processo aberto; `Ctrl+C` encerra o acesso. O terminal
mostra um endereço HTTPS aleatório em `*.trycloudflare.com`, que muda ao reiniciar.
O endereço é público e o tráfego passa pela Cloudflare: ativar somente com
autorização do responsável pelos dados e manter o login da aplicação obrigatório.
Não versionar credenciais, áudios, transcrições ou o executável baixado.

Verificar o link usando outro computador ou celular com dados móveis: a página
deve abrir e `/transcricoes` deve responder HTTP 401 sem autenticação. HTTP 200
na página inicial confirma acesso à interface, mas não valida envio de áudio ou
login completo. Erro 1033 indica túnel desconectado; erro 502 costuma indicar
falha de comunicação com o serviço local. Quick Tunnels são temporários, sem
garantia de disponibilidade; consultar seus limites antes de enviar aulas grandes.
