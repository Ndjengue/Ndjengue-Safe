# Controlo Parental

Projeto Android (Kotlin + Jetpack Compose) de controlo parental, gerido por
uma conta de pai e qualquer número de dispositivos filho emparelhados.
Ver o documento de requisitos completo (artefacto partilhado na conversa)
para a especificação funcional.

## Como abrir e compilar

1. Instala o [Android Studio](https://developer.android.com/studio) (versão atual).
2. Abre esta pasta como projeto. O Android Studio descarrega automaticamente
   o Android SDK e o Gradle na primeira vez que abrires o projeto.
3. Cria um projeto no [Firebase Console](https://console.firebase.google.com),
   regista uma app Android com o package name `com.familia.controloparental`,
   ativa **Authentication** (e-mail/senha) e **Firestore**.
4. Descarrega o `google-services.json` gerado pelo Firebase e coloca-o em
   `app/google-services.json` (este ficheiro nunca é versionado — está no
   `.gitignore`).
5. Compila/corre normalmente a partir do Android Studio.

## Estrutura do código

- `comum/` — modelos de dados, motor de regras (`MotorRegras`), `MainActivity`
  e navegação entre os modos Pai e Filho.
- `pai/` — ecrãs do modo Pai: login/registo, painel de dispositivos, apps e
  regras de URL.
- `filho/` — ecrãs e serviços do modo Filho: emparelhamento, ecrã de bloqueio,
  serviço de acessibilidade (deteção de app em primeiro plano), Device Admin.
- `vpn/` — filtro de domínios via `VpnService` (`FiltroVpnService`) e o
  analisador de pacotes DNS que usa.
- `servico/` — sincronização em segundo plano com o Firestore e arranque
  automático após reiniciar o telemóvel.
- `data/` — `FirestoreRepositorio` (toda a comunicação com o Firestore) e
  `CacheLocal` (cópia local das regras, para funcionar offline).

## Pontos a reforçar antes de produção

Estão assinalados com comentários no código, mas resumindo:

- **`AnalisadorPacoteDns`**: cobre IPv4 + UDP + uma consulta por pacote; não
  recalcula checksums IP/UDP, não trata TCP nem IPv6. Suficiente como base,
  precisa de testes contra tráfego real antes de confiar nele.
- **DNS-over-HTTPS**: os browsers modernos podem contornar o filtro DNS local
  usando DoH; considerar bloquear os endpoints DoH conhecidos ou desativar o
  DoH do browser via política, se for necessário endurecer isto.
- **Filas offline para relatórios**: `registarTempoEcra` e
  `registarBloqueioTentado` falham silenciosamente sem rede; para não perder
  dados, trocar por uma fila local (Room) com reenvio ao reconectar.
- **Regras de segurança do Firestore**: este projeto não inclui
  `firestore.rules` — é essencial configurá-las no Firebase Console para que
  cada família só leia/escreva os seus próprios dados antes de usar em
  produção com dados reais.
