# Padrões do TakeToo

Estas instruções se aplicam a todo o repositório. Preserve as convenções existentes e atualize este documento quando uma decisão do projeto mudar.

## Contexto e stack

- API de gestão de projetos audiovisuais: profissionais gerenciam clientes, projetos, versões e comentários; clientes revisam por um portal com acesso temporário.
- Java 21, Spring Boot 3.4, Maven Wrapper, Spring Web, Spring Data JPA, Spring Security e JWT.
- PostgreSQL em produção; schema versionado com Flyway. OpenAPI/Swagger com Springdoc.
- Este repositório contém o backend; alterações no player dependem do frontend em seu respectivo projeto.

## Organização e código

- Mantenha o pacote base `com.kinkan.take_too` e a estrutura `controller`, `service`, `repository`, `domain/entity`, `domain/dto`, `domain/enums`, `security`, `config`, `exception` e `util`.
- Controllers cuidam de HTTP, validação de entrada e identidade autenticada; services concentram regras de negócio e transações; repositories cuidam da persistência.
- Use injeção por construtor, campos `final` e `@RequiredArgsConstructor`, conforme o código existente.
- Use records para DTOs, Jakarta Validation nas entradas e DTOs nas respostas; não exponha entidades JPA diretamente.
- Preserve nomes de domínio, rotas e mensagens em português; use a nomenclatura técnica existente para integrações externas.
- Siga a formatação Java do arquivo alterado, normalmente com quatro espaços. Evite reformatação e refatorações fora do escopo.
- Explicite nulabilidade seguindo as anotações existentes. Não suponha que `@NullMarked` no pacote raiz se aplique automaticamente a subpacotes.
- Use UUID para identificadores e `Instant` para timestamps de domínio, conforme as entidades atuais.
- Use `@Transactional` para alterações e `@Transactional(readOnly = true)` para consultas de serviço quando apropriado.

## API e segurança

- Preserve rotas sob `/api`, contratos de DTOs e códigos HTTP existentes; documente alterações incompatíveis e a migração dos consumidores.
- Valide payloads com `@Valid` e documente endpoints com as anotações OpenAPI usadas nos controllers.
- Centralize erros no `GlobalExceptionHandler`, reutilizando as exceções existentes e o formato de resposta atual.
- Obtenha a identidade pelo principal autenticado; nunca confie em um identificador de profissional enviado pelo cliente para conceder acesso.
- Valide propriedade dos recursos no serviço/repository. Profissionais só acessam seus próprios dados; clientes só acessam o projeto associado ao token.
- Preserve expiração e revogação do magic link, isolamento entre projetos e restrições de papel nas novas rotas.
- Configure credenciais por variáveis de ambiente ou gerenciador de segredos. Não versione segredos nem registre JWTs, links de acesso ou URLs assinadas em logs.

## Persistência e regras de negócio

- Crie migrations incrementais em `src/main/resources/db/migration`, no padrão `V<numero>__<descricao>.sql`; não altere migrations já aplicadas.
- Mantenha `spring.jpa.hibernate.ddl-auto=validate`; mudanças de schema devem vir por Flyway.
- Preserve constraints e controle de concorrência, incluindo unicidade de `(projeto_id, numero)` e lock do projeto na criação de versões.
- Fluxo principal: `GRAVACAO_CONCLUIDA → EM_EDICAO → REVISAO_CLIENTE → APROVADO → ENTREGUE`. Preserve também as transições de retorno permitidas por `ProjetoService`.
- Publicar uma versão exige `EM_EDICAO` e move o projeto para `REVISAO_CLIENTE`; projetos entregues não recebem novas versões.
- Somente a versão mais recente, disponível e com projeto em revisão pode ser aprovada/rejeitada pelo cliente. Rejeição mantém o histórico e retorna o projeto para edição.

## Direção aprovada: vídeo e notificações

- Mux é o primeiro adaptador de vídeo. Mantenha contratos de API e domínio independentes do provedor, com integrações isoladas atrás de `VideoProvider` e seleção por `VideoProviderRegistry`.
- O fluxo de `/api/videos` é independente: vídeos pertencem ao profissional e não publicam versões, alteram projetos ou notificam clientes. Upload no projeto usa `/api/projetos/{id}/videos/uploads` e fica em rascunho recuperável; publicar exige ação explícita em `/api/projetos/{id}/versoes` com `videoId` pronto ou `arquivoUrl` legado, exatamente uma origem.
- A mesma mídia pode ser reutilizada em versões/projetos do mesmo profissional. Vídeos usados por versões não podem ser excluídos; publicação e exclusão compartilham lock do vídeo. Playback do cliente é autorizado pela versão/projeto do magic link, sem liberar a biblioteca para clientes.
- Separar upload/processamento de publicação. Só um vídeo pronto pode ser publicado como versão e iniciar a revisão.
- Planejar upload direto retomável, reprodução assinada após autorização e webhooks autenticados com tratamento de duplicidade e eventos fora de ordem.
- Separar estado técnico do vídeo do estado de avaliação da versão; preservar acesso às versões legadas durante a migração.
- Fornecer dimensões/duração ao frontend para player vertical, paisagem e quadrado, mantendo comentários por tempo.
- Marca d'água deve ser aplicada no processamento do vídeo de revisão. Retenção do original e entrega sem marca ainda precisam de definição.
- Notificações devem usar outbox transacional, processamento assíncrono, retries e deduplicação; começar por e-mail e depois WhatsApp, respeitando preferências de contato.

## Validação e documentação

- Use JUnit 5 e Mockito para testes unitários de serviço; siga nomes descritivos como `deveCriarVersaoComSucesso`.
- Testes de integração usam Spring Boot, Spring Security Test/MockMvc e H2 em modo PostgreSQL, com Flyway habilitado em `src/test/resources/application.properties`.
- Ao mudar comportamento, cubra sucesso, transições inválidas e acesso indevido. Para integrações, simule o provedor; testes comuns não devem exigir credenciais reais.
- H2 não garante equivalência com PostgreSQL; valide migrations/concorrência no PostgreSQL quando dependerem de comportamento específico desse banco.
- Comandos: `./mvnw test` para a suíte, `./mvnw -Dtest=VersaoServiceTest test` para teste direcionado e `./mvnw spring-boot:run` para execução local.
- Execução local exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET` (ao menos 32 caracteres); `docker compose up -d` sobe o PostgreSQL.
- Atualize `README.md`, documentação OpenAPI e `docs/insomnia_collection.json` quando os fluxos/endpoints correspondentes mudarem.
- Em alterações somente de documentação, revise o conteúdo e execute `git diff --check`; não é necessário rodar a suíte Java.
- Ao entregar, informe o que mudou, o que foi validado e eventuais limitações reais; não declare testes que não executou.
