# Preferências de marca d'água — etapa 3, issue #9

O backend salva uma logo privada e preferências por profissional. Novas mídias capturam uma cópia imutável da configuração ativa; trocar/desativar a marca posteriormente não altera os registros anteriores.

**Esta etapa prepara a marca; não a aplica automaticamente ao arquivo.** O campo `marcaDaguaPlanejada` não comprova proteção visual. O painel do profissional pertence à #10 e a integração de processamento/derivados à #11. A opção técnica legada `marcaDagua` de uploads de vídeo continua funcionando separadamente e tem precedência sobre as preferências planejadas.

## API do profissional

Todas as rotas usam o principal autenticado e `ROLE_PROFISSIONAL`, sem aceitar um ID de profissional para conceder acesso. Respostas usam `Cache-Control: no-store`.

- `GET /api/profissionais/me/marca-dagua`: retorna configuração ou defaults desativados, sem criar registro.
- `PUT /api/profissionais/me/marca-dagua`: substitui a configuração completa.
- `POST /api/profissionais/me/marca-dagua/logo/uploads`: autoriza upload direto e privado de uma nova logo, sem herdar a marca anterior.

Defaults: desativada, sem logo, posição `INFERIOR_DIREITA`, largura 15%, opacidade 70%, margem 3%.

### Enviar e escolher logo

1. Calcular SHA-256 em base64 e autorizar o arquivo:

```json
{"nome":"Minha logo","tipo":"image/png","tamanhoBytes":12345,"checksum":"SHA256_DO_ARQUIVO_EM_BASE64"}
```

2. Enviar o binário por PUT à URL retornada, com todos os headers autorizados e sem JWT/cookies da API.
3. Confirmar em `POST /api/imagens/{imagemId}/confirmar`; verificar `status=PRONTA`.
4. Vincular o ID ao salvar as preferências. Uma imagem anterior, pronta e do próprio profissional também pode ser escolhida.

A logo deve ser JPEG/PNG, até **2 MiB**, de **1 a 4096 pixels por dimensão**, além dos limites gerais de imagens. PNG permite transparência. O endpoint de logo limita os bytes na autorização; a confirmação valida o arquivo real; o vínculo às preferências verifica disponibilidade, propriedade e limites específicos da logo. Arquivo inválido/pending/excluído não pode ser vinculado.

Prévia: `POST /api/imagens/{imagemId}/visualizacao` gera a URL temporária normal após autorização. As preferências retornam metadados da logo, nunca sua chave de storage ou URL assinada.

### Salvar preferências

```json
{
  "ativaPadrao": true,
  "logoImagemId": "UUID_DA_LOGO_PRONTA",
  "posicao": "INFERIOR_DIREITA",
  "larguraPercentual": 15,
  "opacidadePercentual": 70,
  "margemPercentual": 3
}
```

Posições: `SUPERIOR_ESQUERDA`, `SUPERIOR_DIREITA`, `INFERIOR_ESQUERDA`, `INFERIOR_DIREITA`, `CENTRO`. Largura e opacidade: 1–100%; margem: 0–40%. Campos de configuração são obrigatórios; `logoImagemId` pode ser nulo somente com `ativaPadrao=false`.

Para desativar mantendo a logo, envie `ativaPadrao=false` com seu ID. Para remover o vínculo atual, envie `ativaPadrao=false` e `logoImagemId=null`, preservando os demais campos obrigatórios. Uma nova logo substitui apenas a preferência atual.

## Snapshots e exclusão

Quando as preferências estão ativas, novos uploads normais de vídeo/imagem recebem `marcaDaguaPlanejada` com ID da logo, posição, largura, opacidade, margem e `capturadoEm`. URLs assinadas não são armazenadas no snapshot. Mídias anteriores à configuração continuam sem snapshot; desativação afeta apenas próximas capturas. Upload dedicado de logo não captura marca, evitando dependências recursivas.

A logo permanece protegida contra exclusão enquanto estiver vinculada às preferências (inclusive desativadas) ou a snapshots de mídias ainda não excluídas. Remover a preferência não elimina referências anteriores. Para liberar uma logo não publicada, desvincule-a e exclua as mídias sem uso que ainda a referenciem; mídias publicadas preservam o histórico.

A criação/atualização de preferências e a captura serializam pelo profissional. O vínculo e a exclusão compartilham o lock da imagem. A migration V9 cria as preferências e referências/JSON de snapshots, com constraints e índices, sem alterar conteúdo existente.

## Contrato interno de processamento

`MarcaDaguaService.prepararParaProcessamento` resolve a logo pelo snapshot e proprietário, sem usar as preferências atuais. Solicita ao `ImageStorageProvider` uma URL temporária de **48 horas**, verifica HTTPS, porta padrão, ausência de credenciais/fragmento e host explicitamente permitido em `VIDEO_WATERMARK_ALLOWED_HOSTS`.

Para o S3 atual, o host regional é `taketoo.s3.sa-east-1.amazonaws.com`; configure a lista sem protocolo ou caminho, preservando outros hosts legítimos. Não torne o bucket público. O método não publica a URL em rotas HTTP nem a persiste e ainda não é chamado automaticamente pelo fluxo de processamento.

Antes de integrar na #11, compatibilize essa janela com o prazo de upload/processamento e a validade das credenciais. Credenciais temporárias podem expirar antes da URL; o consumidor deve validar essa condição operacional. Não confundir o GET normal de prévia (10 minutos) com o contrato interno de processamento (48 horas).

## Validação e próximos passos

Testes comuns usam storage/provedor falsos e não exigem AWS/Mux. Cobrem defaults, autenticação/papel, isolamento, validação, troca/remoção de logo, snapshots imutáveis, upload de logo sem marca, proteção de referências e URLs internas com host/HTTPS controlados. Testes PostgreSQL opcionais cobrem primeira configuração concorrente, exclusão versus vínculo e captura versus atualização.

Próximo passo: #10, consumir esses endpoints no painel. A aplicação visual real em vídeo/imagem, provas do resultado processado, retenção de originais e entrega sem marca continuam na #11.

### Evidências em 16/10/2026

- Suíte Java: 126 passaram; 8 testes PostgreSQL ficaram fora da execução padrão.
- PostgreSQL 16 isolado: 14 testes direcionados de preferências, processamento interno e concorrência passaram. Upgrade V8 → V9 preservou projeto, versão legada e comentário existentes.
- S3 real com API/banco isolados: logos PNG/JPEG privadas enviadas, confirmadas e visualizadas; preferências salvas/trocadas/desativadas; snapshot preservado após troca; exclusão protegida e posterior liberação validadas. Objetos temporários removidos.
- O processamento visual automático e a conferência de marca no arquivo final não foram realizados; pertencem à #11.

### Publicação e verificação na VPS

Backend `5c405a1` publicado com V9 em 16/10/2026. Backup de banco, `.env`, JAR e imagem anterior preservado em `/opt/taketoo/.git/deploy-backups/20261016-233425`; volume PostgreSQL mantido. Preferências autenticadas responderam 200 e ausência de sessão retornou 401. Uma logo PNG temporária foi enviada, confirmada, visualizada e excluída com S3 real, sem alterar preferências de profissionais existentes.

O host regional `taketoo.s3.sa-east-1.amazonaws.com` foi acrescentado à lista permitida de processamento, preservando outros hosts e mantendo o bucket privado. A API respondeu 200 após recriação. Mais quatro regressões de concorrência de imagens/vídeos passaram sobre a V9 final: 18 testes direcionados nas execuções PostgreSQL desta entrega.
