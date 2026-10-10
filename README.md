# TakeToo API

Backend do MVP de gestão de projetos audiovisuais. A API permite que profissionais organizem clientes, projetos, versões de vídeo e comentários, enquanto o cliente final acompanha e revisa um projeto por meio de um link de acesso temporário.

## Tecnologias

- Java 21 e Spring Boot 3
- Spring Data JPA e PostgreSQL
- Flyway para migrations
- Spring Security com JWT
- Springdoc OpenAPI / Swagger
- JUnit 5, Mockito e H2 para testes

## Requisitos

- Docker e Docker Compose para executar a API e o PostgreSQL em containers
- Java 21 para executar a API diretamente pelo Maven Wrapper

## Configuração

A aplicação não possui credenciais de banco ou segredo JWT padrão. Crie um arquivo `.env` na raiz do projeto, ao lado de `docker-compose.yml`:

```dotenv
POSTGRES_DB=taketoo
DB_URL=jdbc:postgresql://localhost:5432/taketoo
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

Na raiz do projeto, suba o banco e a API:

```bash
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
5. Publique uma versão em `POST /api/projetos/{projetoId}/versoes`. A publicação move o projeto automaticamente para `REVISAO_CLIENTE`.
6. Gere o token do portal em `POST /api/auth/magic-link`. Somente o profissional dono do projeto pode fazê-lo.
7. Use o token gerado nas rotas `/api/portal/**` para o cliente visualizar, comentar, aprovar ou rejeitar a versão.

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
- O link do cliente pode ser revogado pelo profissional em `POST /api/projetos/{id}/revogar-link`.

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

### Portal do cliente

- `GET /api/portal/projetos/{projetoId}`
- `GET /api/portal/projetos/{projetoId}/versoes`
- `GET` e `POST /api/portal/versoes/{versaoId}/comentarios`
- `PATCH /api/portal/versoes/{versaoId}/status`

## Upload e streaming independentes

O módulo `/api/videos` oferece upload direto e reprodução HLS privada, com Mux como primeiro adaptador de uma interface independente do provedor. É desativado por padrão (`VIDEO_ENABLED=false`) e não altera o fluxo de projetos, versões por URL ou portal do cliente.

Consulte [configuração, endpoints e roteiro de validação](docs/video-streaming.md). Para testar visualmente vídeos verticais/paisagem, use o [laboratório local de vídeo](docs/video-lab.html). O Insomnia inclui o grupo **08. Vídeos independentes**.

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
