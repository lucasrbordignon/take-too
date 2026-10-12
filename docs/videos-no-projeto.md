# Vídeos no projeto — etapa 1

Upload e processamento continuam separados da publicação. `/api/videos` mantém
a biblioteca independente do profissional. As novas rotas permitem iniciar um
upload no projeto e recuperar esse rascunho ao voltar à tela.

## Upload dentro do projeto

Com JWT do profissional dono e projeto em `EM_EDICAO`:

```http
POST /api/projetos/{projetoId}/videos/uploads
Content-Type: application/json

{
  "nome": "Corte vertical",
  "origem": "https://taketoo.kinkan.cloud"
}
```

O corpo e a resposta são os mesmos do upload independente, incluindo a opção
`marcaDagua`. Envie os bytes diretamente ao provedor pelo método/protocolo
retornados, sem JWT do TakeToo. O vídeo fica na biblioteca e associado ao projeto
como rascunho, sem criar versão nem mudar a etapa. Falhas de criação permanecem
visíveis em `ERRO`; não faça retry automático da criação após timeout.

```http
GET /api/projetos/{projetoId}/videos?pagina=0&tamanho=20
```

Essa listagem paginada retorna apenas rascunhos do projeto, sem vídeos excluídos.
Para continuar o envio, use `GET /api/videos/{videoId}/upload`. Para atualizar o
processamento, use `POST /api/videos/{videoId}/sincronizar` ou o webhook.

## Publicação e seleção de upload anterior

Liste a biblioteca com `GET /api/videos?pagina=0&tamanho=20`. Selecione um vídeo
`PRONTO`, do profissional dono, e publique:

```http
POST /api/projetos/{projetoId}/versoes
Content-Type: application/json

{"videoId": "UUID_DO_VIDEO"}
```

A mesma operação serve para rascunho criado no projeto ou upload anterior da
biblioteca. É permitido reutilizar o mesmo vídeo em diferentes versões/projetos
do mesmo profissional. Publicar exige `EM_EDICAO`, incrementa o número sob lock
do projeto e move para `REVISAO_CLIENTE`. Se o vídeo era rascunho desse projeto,
o vínculo de rascunho é removido; a versão passa a preservar a referência.

O contrato legado `{"arquivoUrl":"https://..."}` continua válido. Informe
exatamente uma origem: `videoId` ou `arquivoUrl`; ambas ou nenhuma retornam 400.

As respostas/listagens de versões mantêm os campos antigos e acrescentam:

- `tipoMidia`: `VIDEO` ou `URL_LEGADA`.
- `videoId`: UUID para vídeo próprio, nulo para legado.
- `video`: metadados com `id`, `status`, `duracaoSegundos`, `largura`, `altura`,
  `proporcao` e `orientacao`; nulo para legado.

Para vídeo próprio, `arquivoUrl` é nulo. Consumidores devem escolher o player por
`tipoMidia` e solicitar playback; não tratar esse campo nulo como falha da API.
Não são expostos identificadores do provedor nem URLs assinadas nas listagens.

## Reprodução autorizada

Profissional dono:

```http
POST /api/projetos/{projetoId}/versoes/{versaoId}/playback
```

Cliente autenticado pelo magic link do projeto:

```http
POST /api/portal/versoes/{versaoId}/playback
```

Ambas retornam `VideoPlaybackDTO` (`video`, `url`, `tipo`, `expiraEm`) com
`Cache-Control: no-store`. O player usa a URL temporária sem enviar JWT ao CDN.
Renove pelo mesmo endpoint, preservando a posição. Links expirados/revogados e
tokens de outro projeto não concedem acesso. Clientes continuam sem acesso à
biblioteca `/api/videos`. A configuração RSA de playback do provedor é exigida.

Versões legadas continuam usando `arquivoUrl`; tentar emitir playback de uma
delas retorna 400. Comentários por tempo e avaliação da última versão continuam
pelas rotas atuais. Versão cujo vídeo deixou de estar pronto não pode ser avaliada.

## Retirar rascunho ou excluir vídeo

```http
DELETE /api/projetos/{projetoId}/videos/{videoId}
```

Retira somente a associação de rascunho; o vídeo permanece na biblioteca. Essa
rota não remove versões publicadas nem exclui o arquivo no provedor.

```http
DELETE /api/videos/{videoId}
```

Exclui vídeo não publicado no provedor e mantém tombstone local; rascunhos são
desvinculados. Vídeos usados por qualquer versão retornam 400, explicando o
bloqueio. Publicação e exclusão compartilham o lock do vídeo para evitar uma
versão referenciar um asset excluído concorrentemente. A remoção definitiva de
conteúdo publicado requer uma política de retenção separada.

## Validação e entrega

- Migration `V7` adiciona os vínculos e exige exatamente uma origem por versão.
- Testes comuns usam H2/Flyway e provedor simulado.
- `VideoConcorrenciaPostgresTest` exige `-Dtaketoo.postgres=true`, banco PostgreSQL
  isolado e configuração `SPRING_DATASOURCE_*` desse banco. Não rode em produção.
- O Insomnia inclui **09. Vídeos no projeto**.
- Este repositório entrega o backend. Telas do projeto, biblioteca e player do
  portal devem consumir os contratos acima no repositório do frontend.
- Imagens, notificações e painel de marca d'água são etapas posteriores.
