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

- Java 21
- Docker e Docker Compose (recomendado para executar o PostgreSQL)

## Configuração

A aplicação não possui credenciais de banco ou segredo JWT padrão. Configure as variáveis antes de iniciá-la:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/taketoo'
export DB_USERNAME='admin'
export DB_PASSWORD='password'
export JWT_SECRET='troque-por-um-segredo-aleatorio-com-ao-menos-32-caracteres'
```

`JWT_SECRET` deve ter pelo menos 32 caracteres. Em produção, forneça os valores pelo gerenciador de segredos ou pelas variáveis do ambiente de deploy.

## Executando localmente

Suba o banco PostgreSQL:

```bash
docker compose up -d
```

Com as variáveis configuradas, inicie a API:

```bash
./mvnw spring-boot:run
```

A aplicação ficará disponível em `http://localhost:8080`.

As migrations do Flyway são aplicadas automaticamente ao iniciar.

## Documentação da API

Com a aplicação em execução, acesse:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/api/ping`

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
