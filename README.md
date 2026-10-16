# TakeToo API

Backend do MVP de gestão de projetos audiovisuais. A API permite que profissionais organizem clientes, projetos, versões de vídeo/imagem e comentários, enquanto o cliente final acompanha e revisa um projeto por meio de um link de acesso temporário.

## Estado do MVP

Vídeos e imagens estão integrados aos projetos e publicados no backend e no web. O acompanhamento de backend, frontend e operação fica nas [issues deste repositório](https://github.com/lucasrbordignon/take-too/issues), com o [plano de entrega na issue #14](https://github.com/lucasrbordignon/take-too/issues/14).

### Disponível

- Gestão de clientes, projetos, etapas, versões e comentários, com isolamento por profissional.
- Biblioteca de vídeos Mux, upload no projeto em rascunho e reutilização de uploads anteriores.
- Biblioteca de imagens JPEG/PNG em S3 privado, upload no projeto e confirmação do arquivo antes de publicar.
- Publicação explícita de uma origem por versão: `videoId`, `imagemId` ou `arquivoUrl` legado.
- Portal com magic link temporário/revogável, HLS e imagens por URLs assinadas após autorização.
- Comentários por tempo em vídeos e gerais em imagens; aprovação/rejeição da versão mais recente.
- Exclusão de mídias sem uso e proteção das vinculadas ao histórico, com locks contra publicação/exclusão simultâneas.
- No web: modal único de upload/biblioteca, histórico Vn → V1 e feedback em overlay sem interromper o vídeo.

### Pendente para fechar a distribuição

- Preferências e logo do profissional, painel de marca d'água e aplicação da configuração no processamento das mídias de revisão.
- Definir retenção de originais e entrega/download sem marca.
- Completar critérios do backlog de imagens: WebP e zoom acessível no visualizador. Hoje o upload aceita apenas JPEG/PNG.
- Conferência visual em 360–390 px e desktop, acessibilidade, falhas de rede e expiração de sessões no fluxo real.
- Fechar limites operacionais por profissional e o roteiro de operação do piloto.

E-mail/WhatsApp, anotações por coordenadas e migração de assets entre provedores ficam para uma etapa posterior. O compartilhamento do magic link é manual.

## Tecnologias

- Java 21 e Spring Boot 3
- Spring Data JPA e PostgreSQL
- Flyway para migrations
- Spring Security com JWT
- Springdoc OpenAPI / Swagger
- JUnit 5, Mockito e H2 para testes

## Requisitos

- Docker e Docker Compose para executar a API e o PostgreSQL em containers
- Java 21 para gerar o JAR e executar a API diretamente pelo Maven Wrapper

## Configuração

A aplicação não possui credenciais de banco ou segredo JWT padrão. Crie um arquivo `.env` na raiz do projeto, ao lado de `docker-compose.yml`:

```dotenv
POSTGRES_DB=taketoo
DB_URL=jdbc:postgresql://localhost:5433/taketoo
DB_USERNAME=taketoo
DB_PASSWORD=substitua-por-uma-senha-aleatoria
JWT_SECRET=substitua-por-um-segredo-aleatorio-com-ao-menos-32-caracteres
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174,http://localhost:8080
```

Inclua em `CORS_ALLOWED_ORIGINS` a origem exata do frontend, com protocolo e porta, sem caminho ou barra final. Uma origem não permitida retorna HTTP 403 (`Invalid CORS request`), inclusive em endpoints públicos.

Substitua os valores de senha e segredo antes de executar. `JWT_SECRET` deve ter pelo menos 32 caracteres. Não versione o `.env`, que já está ignorado pelo Git. Em produção, forneça os valores pelo gerenciador de segredos ou pelas variáveis do ambiente de deploy.

## Executando com Docker Compose

Não é necessário executar `export` ou carregar o `.env` no terminal: o Docker Compose lê esse arquivo automaticamente e encaminha as variáveis declaradas em `environment` para os containers.

Variáveis já exportadas no terminal têm prioridade sobre o `.env`. Se a conexão aparecer com duas URLs concatenadas, limpe as variáveis antigas antes de subir os containers:

```bash
unset POSTGRES_DB DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET CORS_ALLOWED_ORIGINS
docker compose up -d --force-recreate
```

`POSTGRES_DB` deve conter apenas o nome (`taketoo`); `DB_URL` contém a URL JDBC completa. A recriação preserva o volume do banco.

O Dockerfile copia o JAR gerado localmente. Na raiz do projeto, gere o artefato antes de construir a imagem e subir a API:

```bash
./mvnw clean package
docker compose up -d --build
docker compose logs -f api
```

A API ficará disponível em `http://localhost:8082`. O Compose aguarda o banco ficar saudável antes de iniciar a API; as migrations do Flyway são aplicadas automaticamente na inicialização.

Dentro do container, o Compose configura `DB_URL` com o host `db`; o valor `localhost` do `.env` serve para execução fora dos containers. O PostgreSQL é publicado apenas em `127.0.0.1:5433`. Para conectar pelo Beekeeper, use PostgreSQL, host `127.0.0.1`, porta `5433`, banco de `POSTGRES_DB`, usuário de `DB_USERNAME` e senha de `DB_PASSWORD` no `.env`, sem SSL para essa conexão local.

Para encerrar os containers preservando os dados do banco:

```bash
docker compose down
```

Alterar usuário, senha ou nome do banco no `.env` não reconfigura um banco já inicializado no volume `postgres_data`. Nesse caso, use as credenciais existentes ou altere o banco explicitamente.

**Vídeo/Mux:** o Compose encaminha as variáveis `VIDEO_*` e `MUX_*` declaradas no serviço `api`. Para habilitar o módulo, configure `VIDEO_ENABLED=true`, as credenciais e a origem do frontend no `.env`, conforme a [configuração do módulo de vídeo](docs/video-streaming.md). Após alterar o `.env`, execute `docker compose up -d` para aplicar os valores. Reprodução assinada exige também o par de chaves de signing.

## Executando pelo Maven Wrapper

Para executar fora do Docker, disponibilize um PostgreSQL acessível no endereço de `DB_URL`. Para usar o banco do Compose, suba `docker compose up -d db` e configure `DB_URL=jdbc:postgresql://localhost:5433/taketoo`, ajustando o nome do banco conforme `POSTGRES_DB`.

O Spring não carrega o `.env` automaticamente. Em Bash ou Zsh, carregue um `.env` local confiável com valores compatíveis com a sintaxe do shell e inicie a API:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

A aplicação ficará disponível em `http://localhost:8080`. As migrations também são aplicadas automaticamente nesse modo.

## Documentação da API

Com a aplicação em execução pelo Docker Compose, acesse:

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`
- Health check: `http://localhost:8082/api/ping`

Se executar pelo Maven Wrapper, use a porta `8080` nesses endereços.

Para testar endpoints de profissional no Swagger, obtenha um token pelo login ou registro e informe-o no botão **Authorize** como `Bearer <token>`.

## Fluxo principal

1. Registre um profissional em `POST /api/auth/register` ou faça login em `POST /api/auth/login`.
2. Crie e liste clientes em `/api/clientes`.
3. Crie um projeto para um cliente vinculado em `/api/projetos`.
4. Mova o projeto de `GRAVACAO_CONCLUIDA` para `EM_EDICAO`.
5. Envie um vídeo ou imagem em rascunho no projeto, ou selecione uma mídia pronta da biblioteca. Upload não inicia revisão.
6. Publique em `POST /api/projetos/{projetoId}/versoes` com exatamente uma origem: `videoId`, `imagemId` ou `arquivoUrl` legado. A publicação move o projeto para `REVISAO_CLIENTE`.
7. Gere o token do portal em `POST /api/auth/magic-link`. Somente o profissional dono do projeto pode fazê-lo.
8. Use o token gerado nas rotas `/api/portal/**` para o cliente visualizar, comentar, aprovar ou rejeitar a versão.

Quando uma versão é rejeitada, ela é mantida no histórico e o projeto retorna para `EM_EDICAO`. O profissional então publica a próxima versão.

## Máquina de estados

```text
GRAVACAO_CONCLUIDA -> EM_EDICAO -> REVISAO_CLIENTE -> APROVADO -> ENTREGUE
                                      |                 |
                                      +-> EM_EDICAO <---+
```

- A passagem para `REVISAO_CLIENTE` é feita exclusivamente ao criar uma versão.
- O cliente só pode aprovar ou rejeitar a versão mais recente, disponível e cujo projeto esteja em revisão.
- Projetos entregues não aceitam novas versões.

## Autorização e isolamento de dados

- Profissionais usam JWT com papel `ROLE_PROFISSIONAL` e acessam apenas seus próprios clientes, projetos, versões e comentários.
- Clientes usam JWT temporário com papel `ROLE_CLIENTE`, vinculado a um único projeto e ao cliente daquele projeto.
- O link do cliente pode ser revogado pelo profissional em `POST /api/projetos/{id}/revogar-link`. Aprovação e revogação são operações distintas.
- Rotas protegidas retornam 401 para token ausente/inválido/expirado ou magic link revogado; 403 para papel sem permissão ou origem CORS não autorizada. A sessão do profissional pode ser renovada em `/api/auth/refresh`; magic links não usam esse refresh.
- URLs assinadas já emitidas continuam válidas até a expiração; revogar o portal bloqueia a emissão de novas URLs.

## Endpoints principais

### Profissional

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/profissionais/me`
- `GET /api/dashboard/metricas`
- `GET` e `POST /api/clientes`
- `GET /api/clientes/{id}`
- `GET` e `POST /api/projetos`
- `GET /api/projetos/{id}`
- `GET /api/projetos/{id}/atividades`
- `PATCH /api/projetos/{id}/status`
- `POST /api/projetos/{id}/revogar-link`
- `GET` e `POST /api/projetos/{projetoId}/versoes`
- `GET` e `POST /api/versoes/{versaoId}/comentarios`
- `POST /api/auth/magic-link`
- Biblioteca e uploads: `/api/videos/**` e `/api/imagens/**`
- Rascunhos: `/api/projetos/{projetoId}/videos/**` e `/api/projetos/{projetoId}/imagens/**`
- `POST /api/projetos/{projetoId}/versoes/{versaoId}/playback`
- `POST /api/projetos/{projetoId}/versoes/{versaoId}/imagem`

### Portal do cliente

- `GET /api/portal/projetos/{projetoId}`
- `GET /api/portal/projetos/{projetoId}/versoes`
- `GET` e `POST /api/portal/versoes/{versaoId}/comentarios`
- `PATCH /api/portal/versoes/{versaoId}/status`
- `POST /api/portal/versoes/{versaoId}/playback`
- `POST /api/portal/versoes/{versaoId}/imagem`

## Upload e streaming independentes

O módulo `/api/videos` oferece upload direto e reprodução HLS privada, com Mux como primeiro adaptador de uma interface independente do provedor. É desativado por padrão (`VIDEO_ENABLED=false`) e não altera o fluxo de projetos, versões por URL ou portal do cliente.

Consulte [configuração, endpoints e roteiro de validação](docs/video-streaming.md). Para testar visualmente vídeos verticais/paisagem, use o [laboratório local de vídeo](docs/video-lab.html). O Insomnia inclui o grupo **08. Vídeos independentes**.

## Vídeos no projeto

Projetos em edição podem iniciar uploads em rascunho e publicar versões por `videoId` pronto, incluindo uploads anteriores da biblioteca. Versões por `arquivoUrl` continuam disponíveis. O portal emite reprodução temporária após autorizar o projeto do magic link; vídeos publicados não podem ser excluídos.

Consulte [contratos, roteiro de integração do frontend e exclusão](docs/videos-no-projeto.md). O Insomnia inclui **09. Vídeos no projeto**. A migration `V7` é aplicada ao iniciar a API; para gerar o JAR atualizado antes do deploy, execute `./mvnw clean package`.

## Testes

```bash
./mvnw test
```

Os testes de integração usam H2 em memória, configurado em `src/test/resources/application.properties`. Os testes cobrem regras de negócio, transições de projeto, versões, magic link e autorização por papel.

## Estrutura do projeto

```text
src/main/java/com/kinkan/take_too/
├── config/       # Segurança, CORS e OpenAPI
├── controller/   # Endpoints HTTP
├── domain/       # Entidades, DTOs e enums
├── repository/   # Acesso a dados
├── security/     # JWT e filtro de autenticação
├── service/      # Regras de negócio
└── util/         # Utilitários
```

As sessões de playback também retornam `miniaturaUrl` opcional: uma imagem assinada para identificar vídeos na biblioteca e na seleção de versões. O Mux usa as mesmas credenciais de signing já configuradas. Consulte [vídeo e streaming](docs/video-streaming.md) para o contrato e autorização.

## Imagens em projetos e biblioteca

Imagens JPEG/PNG podem ser enviadas à biblioteca ou em rascunho no projeto, reutilizadas e publicadas explicitamente com `imagemId`. O cliente revisa por magic link e comenta sem timestamp. Imagens publicadas são protegidas contra exclusão.

Configure `IMAGEM_ENABLED=true`, `IMAGEM_PROVIDER=s3`, `IMAGEM_S3_BUCKET`, `IMAGEM_S3_REGION` e as credenciais AWS no ambiente do backend. Veja [configuração IAM/CORS, limites e fluxo completo](docs/imagens-s3.md). O frontend oferece a biblioteca em `/imagens` e a opção **Imagem** no modal de publicação.

### Ambiente de imagens

```dotenv
IMAGEM_ENABLED=true
IMAGEM_PROVIDER=s3
IMAGEM_S3_BUCKET=taketoo
IMAGEM_S3_REGION=sa-east-1
IMAGEM_MAX_BYTES=10485760
IMAGEM_MAX_PIXELS=25000000
AWS_ACCESS_KEY_ID=preencha-somente-no-ambiente-do-backend
AWS_SECRET_ACCESS_KEY=preencha-somente-no-ambiente-do-backend
# AWS_SESSION_TOKEN=apenas-para-credenciais-temporarias
```

A política IAM é aplicada ao usuário/role das credenciais; o CORS é aplicado ao bucket. Mantenha o bucket privado. O web calcula o SHA-256, envia o binário ao S3 com os headers autorizados e solicita confirmação à API. A imagem só fica pronta após validação de tamanho, checksum, formato, dimensões e decodificação. Padrão: 10 MiB e 25 milhões de pixels. Imagens não recebem marca d'água nesta entrega.

## Deploy e verificação

O ambiente atual usa backend em Docker Compose na VPS (`/opt/taketoo`) e frontend na Vercel. O Dockerfile não executa Maven: gere o JAR localmente, envie para `target/` na VPS, atualize o código e recrie apenas `api`. Antes de atualizar, preserve backup do PostgreSQL, `.env`, JAR e imagem Docker anteriores. Não remova o volume do banco.

```bash
# Local: validar e gerar o JAR
./mvnw clean package
# Transfira target/take-too-0.0.1-SNAPSHOT.jar para target/ na VPS.

# Na VPS, após backup e transferência do JAR:
git pull --ff-only
docker compose up -d --build api
docker compose ps
docker compose logs --tail=100 api
curl -i http://127.0.0.1:8082/api/ping
```

Mudanças no `.env` exigem recriação do container; `docker compose restart` não reaplica variáveis. Flyway aplica as migrations no startup; a V8 acrescenta imagens e preserva versões legadas. Para rollback, use o artefato/imagem e o ambiente preservados; não reverta nem apague migrations aplicadas sem avaliar os dados e a compatibilidade.

### Validação registrada em 16/10/2026

- Suíte Java: 116 passaram; 4 testes PostgreSQL são opcionais na execução padrão.
- PostgreSQL 16 isolado: 15 testes passaram, incluindo V8 e concorrência de imagens/vídeos.
- API com S3 real: PNG vertical e JPEG horizontal, upload, confirmação, visualização, publicação, feedback, rejeição, reutilização, aprovação e exclusão.
- Produção: PNG/JPEG enviados, validados e excluídos; biblioteca autenticada e CORS pelo domínio público; reprodução Mux existente preservada.
- A conferência visual em navegador/mobile ainda está pendente. Consulte [evidências e limitações](docs/imagens-s3.md).
