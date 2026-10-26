package com.familia.controloparental.data

import com.familia.controloparental.comum.*
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Único ponto de acesso ao Firestore (plano gratuito Spark: sem Functions, sem Storage).
 *
 * Estrutura:
 *   familias/{fid}                           donoUid, emailRecuperacao
 *   familias/{fid}/membros/{uid}             papel = "pai" | "filho"
 *   familias/{fid}/segredo/admin             hash, sal (PIN de administrador)
 *   familias/{fid}/dispositivos/{did}        perfil da criança, heartbeat, localização, proteções
 *     .../apps/{pacote}  .../regrasUrl/{id}  .../perfisHorario/{id}
 *     .../relatorios/{AAAA-MM-DD}  .../bloqueios/{auto}  .../localizacoes/{auto}
 *   familias/{fid}/pedidos/{id}              pedidos de liberação
 *   familias/{fid}/alertas/{id}              alertas para os pais
 *   convites/{codigo}                        código de emparelhamento (6 dígitos)
 */
class FirestoreRepositorio(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ---------- Referências ----------

    private fun familiaRef(fid: String) = db.collection("familias").document(fid)
    private fun dispositivoRef(fid: String, did: String) =
        familiaRef(fid).collection("dispositivos").document(did)

    // ---------- Fluxos ----------

    private fun <T> Query.fluxo(mapear: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val registo = addSnapshotListener { snap, _ ->
            if (snap != null) trySend(mapear(snap))
        }
        awaitClose { registo.remove() }
    }

    private fun <T> DocumentReference.fluxoDoc(mapear: (DocumentSnapshot) -> T): Flow<T> = callbackFlow {
        val registo = addSnapshotListener { snap, _ ->
            if (snap != null) trySend(mapear(snap))
        }
        awaitClose { registo.remove() }
    }

    // ---------- Família e PIN de administrador ----------

    suspend fun criarFamilia(donoUid: String, emailRecuperacao: String, pin: String): String {
        val fid = db.collection("familias").document().id
        val sal = Seguranca.gerarSal()
        val hash = Seguranca.calcularHash(pin, sal)
        val lote = db.batch()
        lote.set(
            familiaRef(fid),
            mapOf("donoUid" to donoUid, "emailRecuperacao" to emailRecuperacao, "criadoEm" to System.currentTimeMillis())
        )
        lote.set(familiaRef(fid).collection("membros").document(donoUid), mapOf("papel" to "pai"))
        lote.set(
            familiaRef(fid).collection("segredo").document("admin"),
            mapOf("hash" to hash, "sal" to sal, "atualizadoEm" to System.currentTimeMillis())
        )
        lote.commit().await()
        return fid
    }

    suspend fun buscarFamiliaDoPai(donoUid: String): String? {
        val r = db.collection("familias").whereEqualTo("donoUid", donoUid).limit(1).get().await()
        return r.documents.firstOrNull()?.id
    }

    suspend fun alterarPinAdmin(fid: String, novoPin: String) {
        val sal = Seguranca.gerarSal()
        val hash = Seguranca.calcularHash(novoPin, sal)
        familiaRef(fid).collection("segredo").document("admin")
            .set(mapOf("hash" to hash, "sal" to sal, "atualizadoEm" to System.currentTimeMillis())).await()
    }

    /** Devolve (hash, sal) do PIN de administrador. */
    suspend fun obterSegredoAdmin(fid: String): Pair<String, String>? {
        val d = familiaRef(fid).collection("segredo").document("admin").get().await()
        val hash = d.getString("hash") ?: return null
        val sal = d.getString("sal") ?: return null
        return hash to sal
    }

    fun observarSegredoAdmin(fid: String): Flow<Pair<String, String>?> =
        familiaRef(fid).collection("segredo").document("admin").fluxoDoc { d ->
            val h = d.getString("hash")
            val s = d.getString("sal")
            if (h != null && s != null) h to s else null
        }

    /** Valida o PIN contra o servidor (usado pelo pai ao confirmar ações sensíveis). */
    suspend fun verificarPinAdmin(fid: String, pin: String): Boolean {
        val segredo = obterSegredoAdmin(fid) ?: return false
        return Seguranca.verificar(pin, segredo.second, segredo.first)
    }

    // ---------- Emparelhamento ----------

    /** O pai cria primeiro o dispositivo "pendente" com o perfil da criança. */
    suspend fun criarDispositivoPendente(fid: String, perfil: PerfilCrianca): String {
        val ref = familiaRef(fid).collection("dispositivos").document()
        ref.set(
            mapOf(
                "nome" to "",
                "emparelhado" to false,
                "onlineDeclarado" to false,
                "criadoEm" to System.currentTimeMillis(),
                "crianca" to mapaDePerfil(perfil)
            )
        ).await()
        return ref.id
    }

    suspend fun gerarConvite(fid: String, did: String): Pair<String, Long> {
        val expiraEm = System.currentTimeMillis() + 10 * 60_000L
        repeat(8) {
            val codigo = (100000..999999).random().toString()
            val ref = db.collection("convites").document(codigo)
            val existente = ref.get().await()
            val livre = !existente.exists() || (existente.getLong("expiraEm") ?: 0L) < System.currentTimeMillis()
            if (livre) {
                ref.set(mapOf("familiaId" to fid, "dispositivoId" to did, "expiraEm" to expiraEm)).await()
                return codigo to expiraEm
            }
        }
        throw IllegalStateException("Não foi possível gerar um código. Tente novamente.")
    }

    suspend fun lerConvite(codigo: String): Convite? {
        val d = db.collection("convites").document(codigo).get().await()
        if (!d.exists()) return null
        val c = Convite(
            codigo = codigo,
            familiaId = d.getString("familiaId") ?: return null,
            dispositivoId = d.getString("dispositivoId") ?: return null,
            expiraEm = d.getLong("expiraEm") ?: 0L
        )
        return if (c.expiraEm < System.currentTimeMillis()) null else c
    }

    /** Executado no telemóvel da criança após validar o código. */
    suspend fun concluirEmparelhamento(convite: Convite, uidCrianca: String, modeloAparelho: String) {
        val fid = convite.familiaId
        val did = convite.dispositivoId
        familiaRef(fid).collection("membros").document(uidCrianca)
            .set(mapOf("papel" to "filho", "codigo" to convite.codigo, "dispositivoId" to did)).await()
        dispositivoRef(fid, did).update(
            mapOf(
                "nome" to modeloAparelho,
                "emparelhado" to true,
                "uidAutenticacao" to uidCrianca,
                "onlineDeclarado" to true,
                "ultimoSinal" to FieldValue.serverTimestamp()
            )
        ).await()
        db.collection("convites").document(convite.codigo).delete()
    }

    suspend fun cancelarConvite(codigo: String) {
        db.collection("convites").document(codigo).delete().await()
    }

    // ---------- Dispositivos ----------

    fun observarDispositivos(fid: String): Flow<List<Dispositivo>> =
        familiaRef(fid).collection("dispositivos").fluxo { s -> s.documents.map { it.paraDispositivo() } }

    /**
     * Emite null apenas quando o servidor confirma que o dispositivo foi apagado
     * (um "não existe" vindo só da cache offline é ignorado).
     */
    fun observarDispositivo(fid: String, did: String): Flow<Dispositivo?> = callbackFlow {
        val registo = dispositivoRef(fid, did).addSnapshotListener { snap, _ ->
            if (snap != null) {
                if (snap.exists()) trySend(snap.paraDispositivo())
                else if (!snap.metadata.isFromCache) trySend(null)
            }
        }
        awaitClose { registo.remove() }
    }

    suspend fun atualizarPerfilCrianca(fid: String, did: String, perfil: PerfilCrianca) {
        dispositivoRef(fid, did).update("crianca", mapaDePerfil(perfil)).await()
    }

    suspend fun enviarHeartbeat(
        fid: String, did: String, bateria: Int, aCarregar: Boolean, rede: String, protecao: EstadoProtecao
    ) {
        dispositivoRef(fid, did).update(
            mapOf(
                "ultimoSinal" to FieldValue.serverTimestamp(),
                "onlineDeclarado" to true,
                "bateria" to bateria,
                "aCarregar" to aCarregar,
                "rede" to rede,
                "protecao" to mapOf(
                    "vpnAtiva" to protecao.vpnAtiva,
                    "acessibilidadeAtiva" to protecao.acessibilidadeAtiva,
                    "adminAtivo" to protecao.adminAtivo,
                    "localizacaoPermitida" to protecao.localizacaoPermitida,
                    "localizacaoSempre" to protecao.localizacaoSempre,
                    "usoAppsPermitido" to protecao.usoAppsPermitido,
                    "notificacoesPermitidas" to protecao.notificacoesPermitidas
                )
            )
        ).await()
        com.familia.controloparental.admin.AtividadeRegisto.registar()
    }

    /** Melhor esforço: chamado quando o telemóvel está a desligar. */
    suspend fun marcarOffline(fid: String, did: String) {
        dispositivoRef(fid, did).update(
            mapOf("onlineDeclarado" to false, "ultimoSinal" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun removerDispositivo(fid: String, did: String) {
        val ref = dispositivoRef(fid, did)
        val uid = ref.get().await().getString("uidAutenticacao")
        for (sub in listOf("apps", "regrasUrl", "perfisHorario", "relatorios", "bloqueios", "localizacoes")) {
            apagarColecao(ref.collection(sub))
        }
        ref.delete().await()
        if (!uid.isNullOrBlank()) {
            familiaRef(fid).collection("membros").document(uid).delete().await()
        }
        for (nome in listOf("pedidos", "alertas")) {
            val docs = familiaRef(fid).collection(nome).whereEqualTo("dispositivoId", did).get().await()
            docs.documents.chunked(400).forEach { grupo ->
                val lote = db.batch()
                grupo.forEach { lote.delete(it.reference) }
                lote.commit().await()
            }
        }
    }

    private suspend fun apagarColecao(col: CollectionReference) {
        while (true) {
            val docs = col.limit(400).get().await()
            if (docs.isEmpty) break
            val lote = db.batch()
            docs.documents.forEach { lote.delete(it.reference) }
            lote.commit().await()
        }
    }

    // ---------- Localização ----------

    suspend fun atualizarLocalizacao(fid: String, did: String, p: LocalizacaoPonto) {
        dispositivoRef(fid, did).update(
            "localizacaoAtual",
            mapOf(
                "latitude" to p.latitude,
                "longitude" to p.longitude,
                "precisaoMetros" to p.precisaoMetros.toDouble(),
                "velocidadeMs" to p.velocidadeMs.toDouble(),
                "timestamp" to p.timestamp
            )
        ).await()
    }

    suspend fun registarHistoricoLocalizacao(fid: String, did: String, p: LocalizacaoPonto) {
        dispositivoRef(fid, did).collection("localizacoes").add(
            mapOf(
                "latitude" to p.latitude,
                "longitude" to p.longitude,
                "precisaoMetros" to p.precisaoMetros.toDouble(),
                "timestamp" to p.timestamp
            )
        ).await()
    }

    fun observarHistoricoLocalizacao(fid: String, did: String, limite: Long = 60): Flow<List<LocalizacaoPonto>> =
        dispositivoRef(fid, did).collection("localizacoes")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(limite)
            .fluxo { s ->
                s.documents.map {
                    LocalizacaoPonto(
                        latitude = it.getDouble("latitude") ?: 0.0,
                        longitude = it.getDouble("longitude") ?: 0.0,
                        precisaoMetros = (it.getDouble("precisaoMetros") ?: 0.0).toFloat(),
                        timestamp = it.getLong("timestamp") ?: 0L
                    )
                }
            }

    /** Os pais ligam/desligam, a partir da app, o pedido de PIN e o ecrã de bloqueio no telemóvel da criança. */
    suspend fun definirControlosEcra(fid: String, did: String, pinProtecao: Boolean? = null, bloqueioEcra: Boolean? = null) {
        val campos = HashMap<String, Any>()
        if (pinProtecao != null) campos["pinProtecaoAtiva"] = pinProtecao
        if (bloqueioEcra != null) campos["bloqueioEcraAtivo"] = bloqueioEcra
        if (campos.isNotEmpty()) dispositivoRef(fid, did).update(campos).await()
    }

    /** Pede ao telemóvel da criança para enviar a localização com mais frequência até [ateMs]. */
    suspend fun definirRastreioAoVivo(fid: String, did: String, ateMs: Long) {
        dispositivoRef(fid, did).update("rastreioAoVivoAte", ateMs).await()
    }

    // ---------- Apps ----------

    fun observarApps(fid: String, did: String): Flow<List<AppInstalada>> =
        dispositivoRef(fid, did).collection("apps").fluxo { s -> s.documents.map { it.paraApp() } }

    /**
     * Sincroniza a lista de apps instaladas sem tocar nas regras definidas pelos pais.
     * Remove os documentos de apps que já não existem no telemóvel.
     */
    suspend fun sincronizarApps(fid: String, did: String, instaladas: List<AppInstalada>) {
        val col = dispositivoRef(fid, did).collection("apps")
        val existentes = col.get().await().documents.associateBy { it.id }
        val novas = instaladas.associateBy { it.nomePacote }
        val operacoes = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

        novas.forEach { (pacote, app) ->
            val atual = existentes[pacote]
            val precisaEscrever = atual == null ||
                atual.getString("nome") != app.nome ||
                (atual.getString("icone").isNullOrBlank() && !app.iconeBase64.isNullOrBlank())
            if (precisaEscrever) {
                val dados = mutableMapOf<String, Any?>(
                    "pacote" to pacote,
                    "nome" to app.nome,
                    "instaladoEm" to app.instaladoEm
                )
                if (!app.iconeBase64.isNullOrBlank()) dados["icone"] = app.iconeBase64
                if (atual == null) dados["regra"] = RegraApp.PERMITIDO.name
                operacoes.add { it.set(col.document(pacote), dados, SetOptions.merge()) }
            }
        }
        existentes.keys.filter { it !in novas }.forEach { pacote ->
            operacoes.add { it.delete(col.document(pacote)) }
        }
        operacoes.chunked(400).forEach { grupo ->
            val lote = db.batch()
            grupo.forEach { it(lote) }
            lote.commit().await()
        }
    }

    suspend fun definirRegraApp(fid: String, did: String, pacote: String, regra: RegraApp, perfilHorarioId: String?) {
        dispositivoRef(fid, did).collection("apps").document(pacote).set(
            mapOf(
                "pacote" to pacote,
                "regra" to regra.name,
                "perfilHorarioId" to if (regra == RegraApp.BLOQUEADO_POR_HORARIO) perfilHorarioId else null
            ),
            SetOptions.merge()
        ).await()
    }

    // ---------- Regras de sites / redes sociais ----------

    fun observarRegrasUrl(fid: String, did: String): Flow<List<RegraUrl>> =
        dispositivoRef(fid, did).collection("regrasUrl").fluxo { s -> s.documents.map { it.paraRegraUrl() } }

    /** Cria (id vazio) ou atualiza uma regra. Devolve o id. */
    suspend fun guardarRegraUrl(fid: String, did: String, regra: RegraUrl): String {
        val col = dispositivoRef(fid, did).collection("regrasUrl")
        val ref = if (regra.id.isBlank()) col.document() else col.document(regra.id)
        ref.set(
            mapOf(
                "rotulo" to regra.rotulo,
                "tipo" to regra.tipo.name,
                "dominios" to regra.dominios,
                "pacotes" to regra.pacotes,
                "ativo" to regra.ativo,
                "perfilHorarioId" to regra.perfilHorarioId
            )
        ).await()
        return ref.id
    }

    suspend fun removerRegraUrl(fid: String, did: String, regraId: String) {
        dispositivoRef(fid, did).collection("regrasUrl").document(regraId).delete().await()
    }

    // ---------- Perfis de horário ----------

    fun observarPerfisHorario(fid: String, did: String): Flow<List<PerfilHorario>> =
        dispositivoRef(fid, did).collection("perfisHorario").fluxo { s -> s.documents.map { it.paraPerfil() } }

    suspend fun guardarPerfilHorario(fid: String, did: String, perfil: PerfilHorario): String {
        val col = dispositivoRef(fid, did).collection("perfisHorario")
        val ref = if (perfil.id.isBlank()) col.document() else col.document(perfil.id)
        val janelas = perfil.janelasPorDia.entries
            .filter { it.value.isNotEmpty() }
            .associate { (dia, lista) -> dia.name to lista.map { mapOf("inicio" to it.inicio, "fim" to it.fim) } }
        ref.set(mapOf("nome" to perfil.nome, "janelasPorDia" to janelas)).await()
        return ref.id
    }

    /** Apaga o perfil e limpa as referências em apps e regras de sites. */
    suspend fun removerPerfilHorario(fid: String, did: String, perfilId: String) {
        val disp = dispositivoRef(fid, did)
        val lote = db.batch()
        disp.collection("apps").whereEqualTo("perfilHorarioId", perfilId).get().await().documents.forEach {
            lote.update(it.reference, mapOf("perfilHorarioId" to null, "regra" to RegraApp.PERMITIDO.name))
        }
        disp.collection("regrasUrl").whereEqualTo("perfilHorarioId", perfilId).get().await().documents.forEach {
            lote.update(it.reference, "perfilHorarioId", null)
        }
        lote.delete(disp.collection("perfisHorario").document(perfilId))
        lote.commit().await()
    }

    // ---------- Pedidos de liberação ----------

    suspend fun criarPedido(fid: String, pedido: PedidoLiberacao): String {
        val ref = familiaRef(fid).collection("pedidos").document()
        ref.set(
            mapOf(
                "dispositivoId" to pedido.dispositivoId,
                "criancaNome" to pedido.criancaNome,
                "alvo" to pedido.alvo,
                "rotulo" to pedido.rotulo,
                "tipoAlvo" to pedido.tipoAlvo.name,
                "status" to StatusPedidoLiberacao.PENDENTE.name,
                "criadoEm" to System.currentTimeMillis(),
                "duracaoMinutos" to pedido.duracaoMinutos,
                "decididoEm" to 0L,
                "duracaoAprovadaMinutos" to 0
            )
        ).await()
        return ref.id
    }

    fun observarPedidos(fid: String, limite: Long = 60): Flow<List<PedidoLiberacao>> =
        familiaRef(fid).collection("pedidos")
            .orderBy("criadoEm", Query.Direction.DESCENDING).limit(limite)
            .fluxo { s -> s.documents.map { it.paraPedido() } }

    fun observarPedidosDoDispositivo(fid: String, did: String): Flow<List<PedidoLiberacao>> =
        familiaRef(fid).collection("pedidos").whereEqualTo("dispositivoId", did)
            .fluxo { s -> s.documents.map { it.paraPedido() }.sortedByDescending { it.criadoEm }.take(30) }

    suspend fun responderPedido(fid: String, pedidoId: String, aprovado: Boolean, minutos: Int) {
        familiaRef(fid).collection("pedidos").document(pedidoId).update(
            mapOf(
                "status" to if (aprovado) StatusPedidoLiberacao.APROVADO.name else StatusPedidoLiberacao.RECUSADO.name,
                "decididoEm" to System.currentTimeMillis(),
                "duracaoAprovadaMinutos" to if (aprovado) minutos else 0
            )
        ).await()
    }

    // ---------- Alertas ----------

    suspend fun enviarAlerta(fid: String, alerta: Alerta) {
        familiaRef(fid).collection("alertas").add(
            mapOf(
                "dispositivoId" to alerta.dispositivoId,
                "criancaNome" to alerta.criancaNome,
                "tipo" to alerta.tipo.name,
                "mensagem" to alerta.mensagem,
                "criadoEm" to System.currentTimeMillis(),
                "lido" to false
            )
        ).await()
    }

    fun observarAlertas(fid: String, limite: Long = 80): Flow<List<Alerta>> =
        familiaRef(fid).collection("alertas")
            .orderBy("criadoEm", Query.Direction.DESCENDING).limit(limite)
            .fluxo { s -> s.documents.map { it.paraAlerta() } }

    suspend fun marcarAlertaLido(fid: String, alertaId: String) {
        familiaRef(fid).collection("alertas").document(alertaId).update("lido", true).await()
    }

    suspend fun marcarTodosAlertasLidos(fid: String, ids: List<String>) {
        ids.chunked(400).forEach { grupo ->
            val lote = db.batch()
            grupo.forEach { lote.update(familiaRef(fid).collection("alertas").document(it), "lido", true) }
            lote.commit().await()
        }
    }

    // ---------- Relatórios e histórico de bloqueios ----------

    /** Grava o total do dia (valores absolutos calculados no telemóvel; idempotente). */
    suspend fun guardarTempoEcraDia(fid: String, did: String, data: String, porApp: Map<String, Long>) {
        val codificado = porApp.entries.associate { it.key.replace(".", "%2E") to it.value }
        dispositivoRef(fid, did).collection("relatorios").document(data).set(
            mapOf(
                "data" to data,
                "tempoEcraPorApp" to codificado,
                "totalMs" to porApp.values.sum(),
                "atualizadoEm" to System.currentTimeMillis()
            )
        ).await()
    }

    fun observarRelatorios(fid: String, did: String, dias: Long = 7): Flow<List<RelatorioDiario>> =
        dispositivoRef(fid, did).collection("relatorios")
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING).limit(dias)
            .fluxo { s ->
                s.documents.map { d ->
                    val bruto = d.get("tempoEcraPorApp") as? Map<*, *> ?: emptyMap<String, Any>()
                    val mapa = bruto.entries.associate { (k, v) ->
                        k.toString().replace("%2E", ".") to ((v as? Number)?.toLong() ?: 0L)
                    }
                    RelatorioDiario(
                        data = d.id,
                        tempoEcraPorApp = mapa,
                        totalMs = d.getLong("totalMs") ?: mapa.values.sum()
                    )
                }
            }

    suspend fun registarBloqueio(fid: String, did: String, r: RegistoBloqueio) {
        dispositivoRef(fid, did).collection("bloqueios").add(
            mapOf(
                "tipo" to r.tipo.name,
                "alvo" to r.alvo,
                "rotulo" to r.rotulo,
                "em" to System.currentTimeMillis()
            )
        ).await()
    }

    fun observarBloqueios(fid: String, did: String, limite: Long = 100): Flow<List<RegistoBloqueio>> =
        dispositivoRef(fid, did).collection("bloqueios")
            .orderBy("em", Query.Direction.DESCENDING).limit(limite)
            .fluxo { s ->
                s.documents.map {
                    RegistoBloqueio(
                        id = it.id,
                        tipo = enumDe(it.getString("tipo"), TipoAlvo.APP),
                        alvo = it.getString("alvo") ?: "",
                        rotulo = it.getString("rotulo") ?: "",
                        em = it.getLong("em") ?: 0L
                    )
                }
            }

    // ---------- Conversões ----------

    private fun mapaDePerfil(p: PerfilCrianca): Map<String, Any?> = mapOf(
        "nome" to p.nome,
        "telefone" to p.telefone,
        "email" to p.email,
        "fotoBase64" to p.fotoBase64
    )

    private inline fun <reified E : Enum<E>> enumDe(valor: String?, padrao: E): E =
        try { if (valor == null) padrao else enumValueOf<E>(valor) } catch (e: Exception) { padrao }

    private fun DocumentSnapshot.paraDispositivo(): Dispositivo {
        val ultimo = getTimestamp("ultimoSinal", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: 0L
        val loc = if (get("localizacaoAtual") != null) {
            LocalizacaoPonto(
                latitude = getDouble("localizacaoAtual.latitude") ?: 0.0,
                longitude = getDouble("localizacaoAtual.longitude") ?: 0.0,
                precisaoMetros = (getDouble("localizacaoAtual.precisaoMetros") ?: 0.0).toFloat(),
                velocidadeMs = (getDouble("localizacaoAtual.velocidadeMs") ?: 0.0).toFloat(),
                timestamp = getLong("localizacaoAtual.timestamp") ?: 0L
            )
        } else null
        return Dispositivo(
            id = id,
            nome = getString("nome") ?: "",
            crianca = PerfilCrianca(
                nome = getString("crianca.nome") ?: "",
                telefone = getString("crianca.telefone") ?: "",
                email = getString("crianca.email") ?: "",
                fotoBase64 = getString("crianca.fotoBase64")
            ),
            emparelhado = getBoolean("emparelhado") ?: false,
            onlineDeclarado = getBoolean("onlineDeclarado") ?: false,
            ultimoSinal = ultimo,
            bateria = (getLong("bateria") ?: -1L).toInt(),
            aCarregar = getBoolean("aCarregar") ?: false,
            rede = getString("rede") ?: "",
            localizacaoAtual = loc,
            rastreioAoVivoAte = getLong("rastreioAoVivoAte") ?: 0L,
            protecao = EstadoProtecao(
                vpnAtiva = getBoolean("protecao.vpnAtiva") ?: false,
                acessibilidadeAtiva = getBoolean("protecao.acessibilidadeAtiva") ?: false,
                adminAtivo = getBoolean("protecao.adminAtivo") ?: false,
                localizacaoPermitida = getBoolean("protecao.localizacaoPermitida") ?: false,
                localizacaoSempre = getBoolean("protecao.localizacaoSempre") ?: false,
                usoAppsPermitido = getBoolean("protecao.usoAppsPermitido") ?: false,
                notificacoesPermitidas = getBoolean("protecao.notificacoesPermitidas") ?: false
            ),
            uidAutenticacao = getString("uidAutenticacao") ?: "",
            pinProtecaoAtiva = getBoolean("pinProtecaoAtiva") ?: true,
            bloqueioEcraAtivo = getBoolean("bloqueioEcraAtivo") ?: true
        )
    }

    private fun DocumentSnapshot.paraApp(): AppInstalada = AppInstalada(
        nomePacote = getString("pacote") ?: id,
        nome = getString("nome") ?: "",
        iconeBase64 = getString("icone"),
        instaladoEm = getLong("instaladoEm") ?: 0L,
        regra = enumDe(getString("regra"), RegraApp.PERMITIDO),
        perfilHorarioId = getString("perfilHorarioId")
    )

    private fun DocumentSnapshot.paraRegraUrl(): RegraUrl {
        fun lista(campo: String): List<String> =
            (get(campo) as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return RegraUrl(
            id = id,
            rotulo = getString("rotulo") ?: "",
            tipo = enumDe(getString("tipo"), TipoRegraUrl.PERSONALIZADO),
            dominios = lista("dominios"),
            pacotes = lista("pacotes"),
            ativo = getBoolean("ativo") ?: false,
            perfilHorarioId = getString("perfilHorarioId")
        )
    }

    private fun DocumentSnapshot.paraPerfil(): PerfilHorario {
        val bruto = get("janelasPorDia") as? Map<*, *> ?: emptyMap<String, Any>()
        val janelas = mutableMapOf<DiaSemana, List<JanelaHorario>>()
        bruto.forEach { (chave, valor) ->
            val dia = try { DiaSemana.valueOf(chave.toString()) } catch (e: Exception) { null }
            if (dia != null) {
                janelas[dia] = (valor as? List<*>)?.mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    JanelaHorario(m["inicio"]?.toString() ?: "00:00", m["fim"]?.toString() ?: "24:00")
                } ?: emptyList()
            }
        }
        return PerfilHorario(id = id, nome = getString("nome") ?: "", janelasPorDia = janelas)
    }

    private fun DocumentSnapshot.paraPedido(): PedidoLiberacao = PedidoLiberacao(
        id = id,
        dispositivoId = getString("dispositivoId") ?: "",
        criancaNome = getString("criancaNome") ?: "",
        alvo = getString("alvo") ?: "",
        rotulo = getString("rotulo") ?: "",
        tipoAlvo = enumDe(getString("tipoAlvo"), TipoAlvo.APP),
        status = enumDe(getString("status"), StatusPedidoLiberacao.PENDENTE),
        criadoEm = getLong("criadoEm") ?: 0L,
        duracaoMinutos = (getLong("duracaoMinutos") ?: 15L).toInt(),
        decididoEm = getLong("decididoEm") ?: 0L,
        duracaoAprovadaMinutos = (getLong("duracaoAprovadaMinutos") ?: 0L).toInt()
    )

    private fun DocumentSnapshot.paraAlerta(): Alerta = Alerta(
        id = id,
        dispositivoId = getString("dispositivoId") ?: "",
        criancaNome = getString("criancaNome") ?: "",
        tipo = enumDe(getString("tipo"), TipoAlerta.INFO),
        mensagem = getString("mensagem") ?: "",
        criadoEm = getLong("criadoEm") ?: 0L,
        lido = getBoolean("lido") ?: false
    )
}
