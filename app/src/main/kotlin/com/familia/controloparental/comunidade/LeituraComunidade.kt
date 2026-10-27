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
                            nomesDe(d), ms(d, "criadoEm")
                        )
                    }?.sortedByDescending { it.criadoEm } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun observarMensagens(conversaId: String): Flow<List<MensagemChat>> = callbackFlow {
        val reg = db.collection("mensagens").whereEqualTo("conversaId", conversaId)
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
                            respostaAutorUid = d.getString("respostaAutorUid") ?: "", respostaTexto = d.getString("respostaTexto") ?: ""
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
            trySend(if (d == null || !d.exists()) null else Presenca(d.getBoolean("visivel") ?: true, ms(d, "ultimoEm")))
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
    suspend fun identidadeCrianca(fid: String, did: String): Identidade {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")
        val fam = db.collection("familias").document(fid).get().await()
        val dono = fam.getString("donoUid") ?: throw IllegalStateException("Família não encontrada.")
        val disp = db.collection("familias").document(fid).collection("dispositivos").document(did).get().await()
        val nome = ((disp.get("crianca") as? Map<*, *>)?.get("nome") as? String).orEmpty().ifBlank { "Criança" }
        return Identidade(uid, nome, listOf(Contacto(dono, "Pai/Mãe")), podeAmizade = false, ehCrianca = true)
    }
}
