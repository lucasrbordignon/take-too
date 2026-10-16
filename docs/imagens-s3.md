# Imagens privadas com S3

O profissional pode enviar JPEG/PNG para a biblioteca ou iniciar um rascunho em um projeto em edição. O envio não publica uma versão. A publicação exige uma ação explícita com `imagemId` pronto e inicia a revisão pelo cliente.

## Configuração

No `.env` do backend:

```dotenv
IMAGEM_ENABLED=true
IMAGEM_PROVIDER=s3
IMAGEM_S3_BUCKET=taketoo
IMAGEM_S3_REGION=sa-east-1
IMAGEM_MAX_BYTES=10485760
IMAGEM_MAX_PIXELS=25000000
AWS_ACCESS_KEY_ID=preencha-no-ambiente
AWS_SECRET_ACCESS_KEY=preencha-no-ambiente
# AWS_SESSION_TOKEN=apenas-para-credenciais-temporarias
```

O adaptador também aceita a cadeia padrão de credenciais AWS, incluindo roles. No Compose, as credenciais são encaminhadas explicitamente ao container. O `.env` não é versionado. Mudanças de ambiente exigem recriar a API:

```bash
./mvnw -DskipTests package
docker compose up -d --build api
```

Aplique [a política IAM](s3/iam-imagens.json) ao usuário/role das credenciais. Configure [o CORS para console AWS](s3/cors-console.json) em S3 → taketoo → Permissões → CORS, preservando regras existentes. [cors-imagens.json](s3/cors-imagens.json) usa o formato da AWS CLI. A aplicação não precisa de permissão para editar ou consultar a configuração CORS; isso é uma tarefa administrativa. Mantenha o bloqueio de acesso público ativado.

## Fluxo HTTP

1. Calcular SHA-256 do arquivo em base64. Autorizar com `POST /api/imagens/uploads` ou `POST /api/projetos/{projetoId}/imagens/uploads`:

```json
{"nome":"Foto de aprovação","tipo":"image/png","tamanhoBytes":12345,"checksum":"SHA256_DO_ARQUIVO_EM_BASE64"}
```

2. Enviar o arquivo binário com `PUT` para `url`, usando **todos** os `headers` retornados. Não enviar JWT, cookie ou JSON ao S3. A autorização dura 15 minutos e assina MIME, checksum e `If-None-Match: *`; reutilizar a URL após um envio concluído retorna 412, protegendo o objeto contra substituição.
3. `POST /api/imagens/{id}/confirmar` valida tamanho, MIME, SHA-256, formato, dimensões e decodificação. A confirmação é idempotente. Arquivo inválido retorna o DTO com `status=ERRO`; o consumidor deve verificar o estado. Um arquivo ainda ausente retorna 400. Falhas de armazenamento retornam 503.
4. Publicar com `POST /api/projetos/{projetoId}/versoes` e `{"imagemId":"UUID"}`. Exatamente uma origem entre `imagemId`, `videoId` e `arquivoUrl` é aceita.
5. Profissional: `POST /api/projetos/{projetoId}/versoes/{versaoId}/imagem`. Cliente: `POST /api/portal/versoes/{versaoId}/imagem`. Ambos retornam URL GET assinada por 10 minutos após autorização. O cliente não acessa a biblioteca.
6. Comentar com `{"texto":"Ajustar contraste"}`. Imagens não aceitam `tempoVideo`; os comentários são ordenados por criação. Aprovação/rejeição preserva as regras existentes de versão mais recente e etapa do projeto.

Biblioteca: `GET /api/imagens?pagina=0&tamanho=20`. Rascunhos: `GET /api/projetos/{projetoId}/imagens`. Renovar autorização pendente: `GET /api/imagens/{id}/upload`. Visualizar item próprio: `POST /api/imagens/{id}/visualizacao`. Excluir: `DELETE /api/imagens/{id}`.

O web oferece upload e biblioteca no mesmo modal, prévias, progresso, confirmação de cancelamento, confirmação de exclusão e “Verificar envio” para recuperar um arquivo já enviado após sair da tela. Para um arquivo ainda não enviado, selecione-o novamente. Imagens publicadas podem ser reutilizadas pelo mesmo profissional e são protegidas contra exclusão. Publicação e exclusão usam o mesmo lock da imagem.

## Escopo e validação

Padrão: JPEG/PNG até 10 MiB e 25 milhões de pixels. O armazenamento é isolado por `ImageStorageProvider`/`ImageStorageRegistry`; URLs e chaves S3 não são persistidas em versões nem expostas em listagens. URLs assinadas funcionam até sua expiração; revogar o magic link bloqueia novas autorizações, sem invalidar uma URL S3 já emitida.

A migration V8 adiciona imagens e permite comentários sem timestamp, preservando vídeos e versões legadas. Os testes comuns usam armazenamento falso, sem credenciais reais. Testes de concorrência são opcionais com `-Dtaketoo.postgres=true` e um PostgreSQL isolado.

Marca d'água em imagens, HEIC/WebP, anotações por coordenada, quota por profissional e limpeza automática de rascunhos não fazem parte desta entrega.

### Validação executada em 16/10/2026

- Suíte Java: 120 testes, 116 passaram e 4 testes PostgreSQL ficaram fora da execução padrão.
- PostgreSQL 16 isolado: 15 testes passaram, incluindo V8, imagens e concorrência de imagens/vídeos.
- Web: 33 testes passaram, lint e build concluídos.
- API HTTP isolada com S3 real: PNG vertical e JPEG horizontal, PUT assinado, sobrescrita 412, confirmação repetida, dimensões, GET assinado, publicação explícita, portal, feedback sem timestamp, rejeição, reutilização em V2, aprovação, exclusão e revogação de magic link. Objetos temporários removidos.
- CORS real validado para a origem de produção; acesso sem assinatura retornou 403.
- Conferência visual em navegador/mobile pendente: não havia navegador conectado à sessão.
