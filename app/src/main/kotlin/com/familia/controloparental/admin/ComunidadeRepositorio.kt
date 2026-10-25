package com.familia.controloparental.admin

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Escritas de conteúdo sujeito à retenção (chat, publicações, comentários, reações, chamadas).
 * Todos os documentos levam criadoEm (hora do servidor) e expiresAt calculado a partir da política
 * ativa; as regras do Firestore recusam datas que prolonguem a retenção.
 * Anexos (imagem/áudio curto) ficam em Base64 dentro do próprio documento: não há Storage,
 * por isso não há ficheiros órfãos.
 */
class ComunidadeRepositorio(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        const val MAX_ANEXO_BASE64 = 600_000 // o documento Firestore tem limite de 1 MiB
    }

    private val milisPorDia = 86_400_000L

    private suspend fun diasAtivos(): Int =
        try {
            (db.collection("config").document("retencao").get().await().getLong("dias") ?: LimitesRetencao.PADRAO_DIAS.toLong()).toInt()
        } catch (e: Exception) {
            LimitesRetencao.PADRAO_DIAS
        }

    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")

    private fun validarAnexo(b64: String?) {
        if (b64 != null) require(b64.length <= MAX_ANEXO_BASE64) { "O anexo é demasiado grande." }
    }

    private suspend fun expira(limiteMs: Long? = null): Timestamp {
        val calculado = System.currentTimeMillis() + diasAtivos() * milisPorDia - 60_000L
        return Timestamp(Date(if (limiteMs != null) minOf(calculado, limiteMs) else calculado))
    }

    suspend fun criarConversa(outrosParticipantes: List<String>, nomes: Map<String, String> = emptyMap()): String {
        val ref = db.collection("conversas").document()
        ref.set(
            mapOf(
                "participantes" to (outrosParticipantes + uid()).distinct(), "nomes" to nomes,
                "criadoEm" to FieldValue.serverTimestamp()
            )
        ).await()
        return ref.id
    }

    suspend fun enviarMensagem(conversaId: String, texto: String, audioBase64: String? = null) {
        validarAnexo(audioBase64)
        db.collection("mensagens").add(
            hashMapOf(
                "conversaId" to conversaId, "autorUid" to uid(), "texto" to texto, "audioBase64" to audioBase64,
                "anexoBytes" to (audioBase64?.length ?: 0).toLong(),
                "criadoEm" to FieldValue.serverTimestamp(), "expiresAt" to expira()
            )
        ).await()
    }

    suspend fun criarPublicacao(texto: String, imagemBase64: String? = null, audioBase64: String? = null, autorNome: String = ""): String {
        validarAnexo(imagemBase64); validarAnexo(audioBase64)
        val ref = db.collection("publicacoes").document()
        ref.set(
            hashMapOf(
                "autorUid" to uid(), "autorNome" to autorNome, "texto" to texto, "imagemBase64" to imagemBase64, "audioBase64" to audioBase64,
                "anexoBytes" to ((imagemBase64?.length ?: 0) + (audioBase64?.length ?: 0)).toLong(),
                "criadoEm" to FieldValue.serverTimestamp(), "expiresAt" to expira()
            )
        ).await()
        return ref.id
    }

    /** Comentários e reações nunca vivem mais do que a publicação (expiresAt = o dela). */
    suspend fun comentar(publicacaoId: String, texto: String, autorNome: String = "") {
        val pub = db.collection("publicacoes").document(publicacaoId).get().await()
        val limite = pub.getTimestamp("expiresAt")?.toDate()?.time
        db.collection("comentarios").add(
            hashMapOf(
                "publicacaoId" to publicacaoId, "autorUid" to uid(), "autorNome" to autorNome, "texto" to texto,
                "criadoEm" to FieldValue.serverTimestamp(), "expiresAt" to expira(limite)
            )
        ).await()
    }

    suspend fun reagir(publicacaoId: String, tipo: String = "GOSTO") {
        val pub = db.collection("publicacoes").document(publicacaoId).get().await()
        val limite = pub.getTimestamp("expiresAt")?.toDate()?.time
        db.collection("reacoes").add(
            hashMapOf(
                "publicacaoId" to publicacaoId, "autorUid" to uid(), "tipo" to tipo,
                "criadoEm" to FieldValue.serverTimestamp(), "expiresAt" to expira(limite)
            )
        ).await()
    }

    /** Só metadados. Nunca se grava áudio nem vídeo de chamadas. Retenção própria de 30 dias. */
    suspend fun registarChamada(participantes: List<String>, duracaoSegundos: Long, resultado: String, video: Boolean) {
        db.collection("chamadas").add(
            hashMapOf(
                "participantes" to (participantes + uid()).distinct(), "duracaoSegundos" to duracaoSegundos,
                "resultado" to resultado, "video" to video,
                "criadoEm" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + LimitesRetencao.CHAMADAS_DIAS * milisPorDia - 60_000L))
            )
        ).await()
    }
}
