# Validação real do fluxo independente de vídeo

Execução em 9 de outubro de 2026, com API local e PostgreSQL 17 isolado.
Foram enviados dois arquivos sintéticos de três segundos ao Mux usando o fluxo
de `/api/videos`, sem criar projetos ou versões.

| Verificação | Vertical | Paisagem |
| --- | --- | --- |
| Criação do upload | HTTP 201 | HTTP 201 |
| Envio direto ao Mux | HTTP 200 | HTTP 200 |
| Estado final | PRONTO | PRONTO |
| Dimensões | 360 × 640 | 640 × 360 |
| Proporção | 9:16 | 16:9 |
| Duração | 3 segundos | 3 segundos |
| Webhook assinado e repetição idêntica | 204 / 204 | 204 / 204 |
| Registro após repetição | Sem alterações, inclusive updatedAt | Sem alterações, inclusive updatedAt |
| Webhook sem assinatura | HTTP 401 | HTTP 401 |
| HLS sem token | HTTP 403 | HTTP 403 |

Os callbacks foram simulados localmente com dados reais dos assets prontos e
assinados com o segredo de webhook configurado. Isso valida autenticação e
idempotência do endpoint; não valida entrega do webhook pela infraestrutura do Mux.

Ao final, o banco continha dois vídeos, zero projetos e zero versões.

## Recursos criados no Mux

- Vertical: vídeo local `537204ad-2cf3-4d86-9aca-719947f248ec`, asset Mux `1UmsGFkPrGsyeTI1wgxRzuRiQggTJkEvfda02QzyHWZM`.
- Paisagem: vídeo local `6098f244-020b-493a-bd70-b2008e8565ff`, asset Mux `atppoM021H61lXJk5ZFLNGu1BX7aW7oE56aLZOUpbW01Q`.

Os dois assets foram mantidos no Mux para inspeção. Não foram testadas marca
d'água, notificações ou a renderização do player no frontend nesta execução.

## Reprodução assinada pendente

Os assets usam política de reprodução `signed`. O endpoint local de playback
retornou HTTP 503 porque faltam `MUX_SIGNING_KEY_ID` e `MUX_SIGNING_PRIVATE_KEY`.
A tentativa de criar a chave com o token configurado retornou HTTP 403, inclusive
após a atualização informada das credenciais.

Para concluir, criar um token com Mux Video Read/Write e System Write, atualizar
`MUX_TOKEN_ID` e `MUX_TOKEN_SECRET` no `.env` local e gerar a chave usando
`POST /system/v1/signing-keys`. Salvar o ID e a chave privada somente no ambiente,
reiniciar a API e validar o manifesto HLS com o token emitido pelo backend.

Referência: [criação de signing keys no Mux](https://www.mux.com/docs/api-reference/system/signing-keys/create-signing-key).
