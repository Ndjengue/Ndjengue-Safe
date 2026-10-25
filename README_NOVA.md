# Nova app — Super Administrador e retenção (plano Spark, sem custos)

Cópia da Ndjengue VPN v2.1 (congelada na etiqueta `v2.1-original` do outro repositório).
`applicationId` próprio (`com.familia.controloparental.nova`) para poder coexistir no telemóvel.

## Decisão tecnológica
O código existente usa **Cloud Firestore** (+ Firebase Auth). Mantém-se. **Sem Supabase.**
Sem Cloud Functions nem Storage (exigem plano Blaze). Os requisitos que dependem deles foram adaptados:

| Requisito | Adaptação sem custos |
|---|---|
| RF-SA-001 custom claims | Documento `admins/{uid}` criado à mão na consola; as regras impedem qualquer escrita de clientes |
| RF-RET-005/007 Cloud Function agendada | TTL do Firestore em `expiresAt` (diário, gratuito) + botão "Executar limpeza agora" no painel |
| RF-RET-002 ficheiros no Storage | Sem Storage: anexos em Base64 (≤ ~450 KB) no próprio documento → não há ficheiros órfãos |
| Vídeo | Não suportado (precisa de Storage) |
| RF-RET-010 registo por execução | `limpezas/{id}` escrito pelo painel; limpezas feitas só pelo TTL não geram registo |
| Contas ativas | Indisponível (exige Admin SDK) |

## Modelo de dados
`config/retencao {dias, alteradoPor, alteradoPorEmail, alteradoEm}` · `admins/{uid}` · `auditoria/{id}` (só criar) ·
`limpezas/{id}` · `conversas/{id} {participantes}` · `mensagens`, `publicacoes`, `comentarios`, `reacoes`
(cada um com `criadoEm` = hora do servidor e `expiresAt`) · `chamadas` (só metadados, 30 dias).

## Regras (firestore.rules)
- `expiresAt` nunca pode passar de `criadoEm + dias` (+5 min de tolerância): cliente não prolonga.
- Leitura só se `expiresAt > request.time` → conteúdo expirado fica inacessível antes de ser apagado.
- Só o super admin altera a política (1–365 dias), recalcula `expiresAt` e apaga.
- Comentários/reações expiram, no máximo, com a publicação; a limpeza apaga-os em cascata.
- Trade-off: o super admin tem leitura sobre estas coleções para contar/apagar; a app só usa `count()` e `delete`.

## Passos manuais (uma vez)
1. Criar um **novo projeto Firebase** (Spark), registar a app `com.familia.controloparental.nova`,
   ativar Authentication (e-mail/senha e anónimo) e Firestore; colocar o `google-services.json` em `app/`.
2. Publicar `firestore.rules`.
3. Criar `config/retencao` com `dias` = 7 (número) e o documento `admins/<UID do teu utilizador>` (campo qualquer).
4. Firestore → TTL → criar políticas para o campo `expiresAt` nos grupos de coleção:
   `mensagens`, `publicacoes`, `comentarios`, `reacoes`, `chamadas`.

## Testes a executar (checklist)
Selecionar 1/2/7/30/60 e período personalizado (0, 366, vazio recusados) · utilizador comum não vê o painel e
a escrita em `config/retencao` é recusada · mensagem criada com `expiresAt` > limite é recusada ·
reduzir política recalcula e a limpeza apaga · cascata publicação → comentários/reações · conteúdo expirado
não é lido · chamadas sem áudio/vídeo · auditoria criada a cada alteração e não editável.

## Ainda por fazer
Ecrãs de chat, publicações e chamadas (só existem os repositórios `ComunidadeRepositorio`); requisitos por especificar.
