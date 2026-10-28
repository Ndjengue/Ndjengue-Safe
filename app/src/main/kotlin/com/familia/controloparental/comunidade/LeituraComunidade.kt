package com.familia.controloparental.comunidade

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.familia.controloparental.data.lerRobusto

/**
 * Leituras da comunidade. As queries não usam orderBy com filtros (evita índices compostos);
 * ordena-se e filtra-se o que já expirou no cliente. As regras negam o acesso direto (get) a expirados.
 */
class LeituraComunidade(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun ms(d: DocumentSnapshot, campo: String): Long =
        d.getTimestamp(campo, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

    private fun nomesDe(d: DocumentSnapshot): Map<String, String> =
        (d.get("nomes") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()

    fun observarConversas(): Flow<List<Conversa>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close()
            return@callbackFlow
        }
        val reg = db.collection("conversas").whereArrayContains("participantes", uid)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.map { d ->
                        Conversa(
                            d.id,
                            (d.get("participantes") as? List<*>)?.map { it.toString() } ?: emptyList(),
                            nomesDe(d), ms(d, "criadoEm"),
                            ultimaTexto = d.getString("ultimaTexto") ?: "", ultimaEm = ms(d, "ultimaEm"),
                            ultimaAutor = d.getString("ultimaAutor") ?: "",
                            lidoEm = (d.get("lidoEm") as? Map<*, *>)?.entries?.associate {
                                it.key.toString() to ((it.value as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L)
                            } ?: emptyMap()
                        )
                    }?.sortedByDescending { it.criadoEm } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun observarMensagens(conversaIds: List<String>): Flow<List<MensagemChat>> = callbackFlow {
        val ids = conversaIds.distinct().take(30)
        if (ids.isEmpty()) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val reg = db.collection("mensagens").whereIn("conversaId", ids)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                val agora = System.currentTimeMillis()
                trySend(
                    snap?.documents?.map { d ->
                        MensagemChat(
                            d.id, d.getString("autorUid") ?: "", d.getString("texto") ?: "", ms(d, "criadoEm"), ms(d, "expiresAt"),
                            editadoEm = ms(d, "editadoEm"),
                            respostaAutorUid = d.getString("respostaAutorUid") ?: "", respostaTexto = d.getString("respostaTexto") ?: "",
                            imagemBase64 = d.getString("imagemBase64") ?: "", reencaminhada = d.getBoolean("reencaminhada") == true
                        )
                    }?.filter { it.expiresAt > agora }?.sortedBy { it.criadoEm } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun observarPresenca(uid: String): Flow<Presenca?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null); close(); return@callbackFlow
        }
        val reg = db.collection("presenca").document(uid).addSnapshotListener { d, erro ->
            if (erro != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val hora = if (d == null || !d.exists()) 0L else ms(d, "ultimoEm")
            if (d != null) com.familia.controloparental.data.RelogioServidor.amostra("pres:$uid", hora, !d.metadata.isFromCache && !d.metadata.hasPendingWrites())
            trySend(if (d == null || !d.exists()) null else Presenca(d.getBoolean("visivel") ?: true, hora))
        }
        awaitClose { reg.remove() }
    }

    fun observarPublicacoes(): Flow<List<Publicacao>> = callbackFlow {
        val reg = db.collection("publicacoes").orderBy("criadoEm", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                val agora = System.currentTimeMillis()
                trySend(
                    snap?.documents?.map { d ->
                        Publicacao(
                            d.id, d.getString("autorUid") ?: "", d.getString("autorNome") ?: "", d.getString("texto") ?: "",
                            d.getString("imagemBase64"), ms(d, "criadoEm"), ms(d, "expiresAt")
                        )
                    }?.filter { it.expiresAt > agora } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun observarComentarios(publicacaoId: String): Flow<List<ComentarioPub>> = callbackFlow {
        val reg = db.collection("comentarios").whereEqualTo("publicacaoId", publicacaoId)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                val agora = System.currentTimeMillis()
                trySend(
                    snap?.documents?.filter { ms(it, "expiresAt") > agora }?.map { d ->
                        ComentarioPub(d.id, d.getString("autorUid") ?: "", d.getString("autorNome") ?: "", d.getString("texto") ?: "", ms(d, "criadoEm"))
                    }?.sortedBy { it.criadoEm } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    /** Devolve os UIDs de quem reagiu (uma reação por pessoa na interface). */
    fun observarReacoes(publicacaoId: String): Flow<List<String>> = callbackFlow {
        val reg = db.collection("reacoes").whereEqualTo("publicacaoId", publicacaoId)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                val agora = System.currentTimeMillis()
                trySend(snap?.documents?.filter { ms(it, "expiresAt") > agora }?.mapNotNull { it.getString("autorUid") }?.distinct() ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ---------- Amizade por código (só do lado dos pais) ----------

    suspend fun gerarCodigoAmizade(meuNome: String): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")
        repeat(5) {
            val codigo = (100000..999999).random().toString()
            val ref = db.collection("amizades").document(codigo)
            if (!ref.get().await().exists()) {
                ref.set(mapOf("uid" to uid, "nome" to meuNome, "expiraEm" to System.currentTimeMillis() + 15 * 60_000L)).await()
                return codigo
            }
        }
        throw IllegalStateException("Não foi possível gerar um código. Tente de novo.")
    }

    /** Usa o código de outra pessoa e abre a conversa entre as duas. */
    suspend fun usarCodigoAmizade(codigo: String, meuNome: String): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")
        val ref = db.collection("amizades").document(codigo.trim())
        val snap = ref.get().await()
        if (!snap.exists() || (snap.getLong("expiraEm") ?: 0L) < System.currentTimeMillis()) {
            throw IllegalArgumentException("Código inválido ou expirado.")
        }
        val outro = snap.getString("uid") ?: throw IllegalArgumentException("Código inválido.")
        require(outro != uid) { "Esse código é seu." }
        val id = com.familia.controloparental.admin.ComunidadeRepositorio()
            .criarConversa(listOf(outro), mapOf(uid to meuNome, outro to (snap.getString("nome") ?: "Amigo")))
        try {
            ref.delete().await()
        } catch (_: Exception) {
        }
        return id
    }

    /** Lado da criança: o único contacto é o encarregado da família. */
    suspend fun identidadeCrianca(fid: String, did: String, cache: com.familia.controloparental.data.CacheLocal? = null): Identidade {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")
        // Sem ligação (ou ainda a ligar): usa o que ficou guardado da última vez.
        val donoGuardado = cache?.lerTexto("chat_dono_uid")
        val nomeGuardado = cache?.lerTexto("chat_nome")
        try {
            val fam = db.collection("familias").document(fid).lerRobusto()
            val dono = fam.getString("donoUid") ?: throw IllegalStateException("Família não encontrada.")
            val disp = db.collection("familias").document(fid).collection("dispositivos").document(did).lerRobusto()
            val nome = ((disp.get("crianca") as? Map<*, *>)?.get("nome") as? String).orEmpty().ifBlank { "Criança" }
            cache?.guardarTexto("chat_dono_uid", dono)
            cache?.guardarTexto("chat_nome", nome)
            return Identidade(uid, nome, listOf(Contacto(dono, "Pai/Mãe")), podeAmizade = false, ehCrianca = true)
        } catch (e: Exception) {
            if (!donoGuardado.isNullOrBlank()) {
                return Identidade(uid, nomeGuardado.orEmpty().ifBlank { "Criança" }, listOf(Contacto(donoGuardado, "Pai/Mãe")), podeAmizade = false, ehCrianca = true)
            }
            throw e
        }
    }
}
