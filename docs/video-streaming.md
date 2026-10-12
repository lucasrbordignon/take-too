# Fluxo independente de vídeo

O módulo de `/api/videos` permite validar upload e streaming sem conectar o vídeo ao fluxo atual. Cada vídeo pertence a um profissional. Não há vínculo com projeto, versão ou comentário, mudança de etapa, acesso pelo portal do cliente ou envio de notificações. O contrato anterior com `arquivoUrl` continua funcionando.

Para usar vídeos da biblioteca em versões ou iniciar uploads dentro do projeto,
consulte [vídeos no projeto — etapa 1](videos-no-projeto.md). Essas operações são
explícitas e separadas do upload independente.

## Configuração

O recurso é desativado por padrão e a aplicação continua iniciando sem credenciais do Mux. Para habilitar em um ambiente de validação, configure:

```dotenv
VIDEO_ENABLED=true
VIDEO_PROVIDER=mux
VIDEO_ALLOWED_ORIGINS=http://localhost:5173
VIDEO_PLAYBACK_TTL_SECONDS=3600
MUX_TOKEN_ID=<token de API do ambiente Mux>
MUX_TOKEN_SECRET=<segredo do token de API>
MUX_SIGNING_KEY_ID=<identificador da chave de assinatura de playback>
MUX_SIGNING_PRIVATE_KEY=<PEM da chave privada ou PEM codificado em base64>
MUX_UPLOAD_TIMEOUT_SECONDS=3600
```

As credenciais de banco e `JWT_SECRET` continuam obrigatórias. As variáveis acima são exemplos de configuração, não um arquivo carregado automaticamente pelo Spring. Configure-as no processo/gerenciador de segredos. O token de API, a chave RSA de reprodução e o segredo do webhook são credenciais diferentes, do mesmo ambiente Mux.

O token precisa de permissões Mux Video Read/Write. Para gerar uma chave de reprodução pela API do Mux, precisa também de System Write; preencher apenas o token não configura a assinatura de playback. Salve o ID e a chave privada retornados em `MUX_SIGNING_KEY_ID` e `MUX_SIGNING_PRIVATE_KEY` e reinicie a aplicação após alterar seu ambiente.

Para marca d'água, configure `VIDEO_WATERMARK_ALLOWED_HOSTS=cdn.example.com` (lista separada por vírgulas). A imagem precisa estar disponível por HTTPS em um host explicitamente permitido, com extensão PNG/JPG. Hosts devem ser controlados pelo projeto. A URL precisa permanecer válida para o processamento; prefira uma imagem imutável. A configuração usada é persistida por vídeo e não modifica vídeos anteriores.

Para webhooks, configure `MUX_WEBHOOK_SECRET` e cadastre no Mux o endpoint público HTTPS `POST /api/webhooks/video/mux`. Em desenvolvimento, pode ser usado um túnel para a API. Sem webhook, o fluxo inteiro pode ser validado pelo endpoint de sincronização manual.

Configure também `CORS_ALLOWED_ORIGINS` se o cliente estiver em uma origem diferente das origens locais já permitidas. A origem enviada no pedido deve corresponder exatamente a uma entrada em `VIDEO_ALLOWED_ORIGINS`, sem caminho ou barra final. HTTPS é obrigatório, exceto para `http://localhost` e `http://127.0.0.1`.

## Validação pela página de laboratório

Com a API executando e as variáveis configuradas, sirva apenas a pasta de documentação:

```bash
python3 -m http.server 5173 --bind 127.0.0.1 --directory docs
```

Abra `http://localhost:5173/video-lab.html`, informe o JWT de um profissional e selecione um vídeo. Use **Criar e enviar**, depois **Sincronizar estado** até `PRONTO`, e finalmente **Reproduzir / renovar sessão**. Repita com vídeos 9:16, 16:9 e 1:1 e, opcionalmente, uma logo. O player usa proporção informada pela API, `object-fit: contain`, limite de altura e fullscreen nativo.

A página é uma ferramenta local de validação, não uma alteração do frontend de produção. Não persiste JWTs nem sessões no navegador. Usa HLS nativo quando disponível e carrega HLS.js 1.7.3 do CDN para os demais navegadores; nesse caso precisa de acesso à internet. O envio da página é um PUT simples, sem retomada automática. O endpoint de upload oferece sessão retomável para um cliente com implementação de `HTTP_CONTENT_RANGE`.

## Validação pela API / Insomnia

Todas as rotas de `/api/videos` exigem `Authorization: Bearer <JWT do profissional>`. O Insomnia possui o grupo **08. Vídeos independentes**.

1. Crie uma sessão em `POST /api/videos/uploads`:

```json
{
  "nome": "Teste vertical",
  "origem": "http://localhost:5173",
  "marcaDagua": {
    "imagemUrl": "https://cdn.example.com/logo.png",
    "posicao": "INFERIOR_DIREITA",
    "larguraPercentual": 20,
    "opacidadePercentual": 70,
    "margemPercentual": 2
  }
}
```

`marcaDagua` é opcional. Posições: `SUPERIOR_ESQUERDA`, `SUPERIOR_DIREITA`, `INFERIOR_ESQUERDA`, `INFERIOR_DIREITA` e `CENTRO`. Largura: 1–100%; opacidade: 0–100%; margem: 0–40%. Para `CENTRO`, o adaptador usa margem zero.

2. A resposta contém `video.id`, `url`, `metodo`, `protocolo`, `headers` e `expiraEm`. Copie `video.id` para `video_id` e `url` para `video_upload_url` no Insomnia. Envie o arquivo como corpo binário para a URL externa, usando o método e headers informados. **Não envie o JWT do TakeToo ao provedor.** Para o Mux, o método é PUT e o protocolo é `HTTP_CONTENT_RANGE`.
3. `GET /api/videos/{id}/upload` recupera a mesma sessão enquanto ela está aguardando o arquivo e dentro do prazo. Não renova a validade. Para retomada no Mux, implemente chunks e `Content-Range` ou utilize um cliente compatível; o protocolo deve ser tratado pelo cliente de upload, sem acoplar o player ao Mux.
4. `POST /api/videos/{id}/sincronizar` consulta o provedor e atualiza o estado. `GET /api/videos/{id}` consulta somente os dados locais. Evite sincronização em loop apertado; para a validação manual, use intervalos de alguns segundos.
5. Quando `PRONTO`, `POST /api/videos/{id}/playback` retorna `url`, `tipo`, `expiraEm`, `miniaturaUrl` opcional e os metadados do vídeo. As URLs já contêm autorização temporária; o player não deve enviar o JWT do TakeToo ao CDN. Renove a sessão pela API ao expirar. A validade cobre pelo menos a duração do vídeo mais cinco minutos, ou o TTL configurado, o que for maior.

As miniaturas seguem a mesma autorização do playback, inclusive nas rotas de versão e portal. O adaptador Mux assina a imagem WebP separadamente (audience `t`, largura máxima solicitada de 640 pixels), sem expor IDs específicos no contrato. O frontend carrega imagens conforme os cards entram na área visível, preserva a proporção com `object-contain` e mantém um placeholder quando o provedor não oferece imagem ou ocorre falha. Não é necessário baixar HLS para gerar a miniatura. URLs não devem ser persistidas ou registradas em logs. Consulte a [documentação de imagens assinadas do Mux](https://www.mux.com/docs/guides/secure-video-playback).
6. `GET /api/videos?pagina=0&tamanho=20` lista somente os vídeos do profissional; tamanho máximo 100.
7. `DELETE /api/videos/{id}` cancela upload pendente ou exclui o asset remoto e marca o registro local `EXCLUIDO`. É idempotente e mantém a trilha local. Se o upload terminar durante o cancelamento, sincronize e repita a exclusão.

Estados locais: `CRIANDO_UPLOAD`, `AGUARDANDO_UPLOAD`, `PROCESSANDO`, `PRONTO`, `ERRO`, `CANCELADO`, `EXCLUIDO`. Vídeo ainda não pronto e upload expirado retornam 400; acesso a vídeo de outro profissional retorna 404; cliente do portal não acessa o módulo. Recurso desativado, provedor indisponível ou configuração ausente retornam 503.

## Arquitetura e troca de provedor

- `VideoProvider` define operações e contratos normalizados de upload, consulta, playback, exclusão e autenticação de webhook. Não depende de um SDK externo.
- `VideoProviderRegistry` registra adaptadores por identificador. `VIDEO_PROVIDER` seleciona o adaptador para **novos vídeos**.
- `VideoAsset` persiste o provedor de cada vídeo e suas referências externas. Vídeos existentes continuam usando esse provedor, mesmo depois de mudar a configuração padrão. Mantenha o adaptador antigo e suas credenciais enquanto existirem vídeos nele. Transferir assets entre provedores é uma migração separada.
- `MuxVideoProvider` concentra JSON/HTTP, estados externos, correlação `passthrough`, overlay, verificação HMAC e reprodução RSA. A assinatura aceita PEM PKCS#8 e PKCS#1, incluindo o PEM em base64 retornado pelo Mux.
- Para outro provedor, implemente `VideoProvider` como bean e adicione sua configuração. Cadastre um endpoint específico de webhook que encaminhe o corpo original e o header de assinatura para `VideoService.receberWebhook`. Libere somente esse POST no `SecurityConfig`.
- O contrato de reprodução é URL HLS e MIME type, sem playback ID ou token específico exposto em campos da API. Clientes de upload precisam suportar o protocolo retornado; outro provedor pode exigir um protocolo diferente.

O webhook verifica assinatura e timestamp (tolerância de cinco minutos) antes de interpretar o JSON. O corpo é limitado a 256 KiB. Eventos relevantes provocam reconciliação pelo estado atual no provedor, usando referências persistidas; dados do evento não substituem referências externas. Eventos duplicados não alteram o timestamp local quando nada mudou. Um estado atrasado de processamento não regride um vídeo pronto e eventos não reativam vídeos excluídos.

Criação persiste a correlação antes da chamada externa. Webhooks recebidos enquanto o upload está sendo criado retornam 503 para permitir retry. Atualizações/exclusões são serializadas por lock do vídeo, sem bloquear o projeto. Falhas na consulta não modificam o estado local. Os calls HTTP têm timeout de conexão de 5 s e leitura de 20 s; múltiplos calls de uma operação podem somar esses prazos.

URLs de upload/playback não são persistidas e suas respostas têm `Cache-Control: no-store`. URLs de reprodução já emitidas continuam utilizáveis até expirar, salvo remoção do asset no provedor; desabilitar o módulo impede novas sessões, não revoga URLs já emitidas.

## Limites desta entrega

- As rotas independentes não publicam versões nem enviam e-mail/WhatsApp. Publicação e playback pelo magic link são oferecidos separadamente nas [rotas de projeto/portal](videos-no-projeto.md).
- Não há quotas de armazenamento/duração/tamanho, agenda de limpeza ou verificação de assinatura do arquivo na API: os bytes vão diretamente ao provedor. Apenas validação do arquivo pelo provedor não substitui quotas do produto.
- POST de criação ainda não recebe uma chave de idempotência. Evite retry automático desse POST após timeout; consulte a listagem e verifique o ambiente do provedor. Um upload criado remotamente cujo resultado se perdeu pode precisar de reconciliação operacional; sua correlação é o UUID em `passthrough`.
- Falhas na criação ficam registradas em `ERRO`; para reenviar, crie um novo upload. Uploads abandonados não são removidos automaticamente; use exclusão manual.
- Marca d'água é gravada no processamento. Não há cópia de entrega sem marca nem arquivo original armazenado pela aplicação; não prometa recuperação do original.
- Testes automatizados usam adaptadores simulados e HTTP mockado, sem cobrança ou acesso ao Mux real. Uploads reais vertical/paisagem e webhooks duplicados foram validados; veja as [evidências e pendências da validação real](validacao-mux-real.md). Overlay e reprodução assinada ainda precisam de validação real.

## Referências

- [Mux: upload direto](https://www.mux.com/docs/guides/upload-files-directly)
- [Mux: reprodução privada](https://www.mux.com/docs/guides/secure-video-playback)
- [Mux: marca d'água](https://www.mux.com/docs/guides/add-watermarks-to-your-videos)
- [Mux: assinatura de webhooks](https://www.mux.com/docs/core/verify-webhook-signatures)
- [HLS.js 1.7.3](https://github.com/video-dev/hls.js/releases/tag/v1.7.3)
