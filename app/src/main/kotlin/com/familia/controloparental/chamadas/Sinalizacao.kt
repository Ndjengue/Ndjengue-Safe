package com.familia.controloparental.chamadas

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/** Pedido de chamada a tocar no telemóvel de quem recebe. */
data class ChamadaEntrada(val id: String, val deUid: String, val deNome: String, val video: Boolean, val tocaAte: Long)

/** Chamada em curso (ou a iniciar), vista de qualquer um dos lados. */
data class DadosChamada(val id: String, val chamador: Boolean, val outroUid: String, val outroNome: String, val video: Boolean)

/**
 * Sinalização WebRTC pelo Firestore (gratuito). O documento sinais/{id} só leva as descrições de sessão
 * (SDP) e os endereços de rede (ICE) necessários para ligar; NUNCA áudio nem vídeo.
 * Apaga-se no fim da chamada e tem expiresAt (TTL) de segurança. Não há histórico de chamadas.
 */
class Sinalizacao(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        const val TOCA_MS = 45_000L
        const val ESTADO_A_TOCAR = "A_TOCAR"
        const val ESTADO_ATENDIDA = "ATENDIDA"
        const val ESTADO_RECUSADA = "RECUSADA"
        const val ESTADO_TERMINADA = "TERMINADA"
    }

    fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Sessão inválida.")

    fun ref(id: String): DocumentReference = db.collection("sinais").document(id)

    /** O chamador cria o pedido (ainda sem oferta SDP). */
    suspend fun criar(paraUid: String, meuNome: String, video: Boolean): String {
        val id = UUID.randomUUID().toString()
        val agora = System.currentTimeMillis()
        ref(id).set(
            hashMapOf(
                "de" to uid(), "para" to paraUid, "deNome" to meuNome, "video" to video,
                "estado" to ESTADO_A_TOCAR, "tocaAte" to agora + TOCA_MS,
                "criadoEm" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(agora + 10 * 60_000L))
            )
        ).await()
        return id
    }

    suspend fun publicarOferta(id: String, sdp: String) {
        ref(id).update("oferta", sdp).await()
    }

    suspend fun publicarResposta(id: String, sdp: String) {
        ref(id).update(mapOf("resposta" to sdp, "estado" to ESTADO_ATENDIDA)).await()
    }

    suspend fun publicarCandidato(id: String, souChamador: Boolean, mid: String?, indice: Int, sdp: String) {
        val campo = if (souChamador) "candidatosDe" else "candidatosPara"
        ref(id).update(campo, FieldValue.arrayUnion("${mid.orEmpty()}|$indice|$sdp")).await()
    }

    suspend fun recusar(id: String) {
        try {
            ref(id).update("estado", ESTADO_RECUSADA).await()
        } catch (_: Exception) {
        }
    }

    suspend fun terminar(id: String) {
        try {
            ref(id).delete().await()
        } catch (_: Exception) {
        }
    }

    /** UID atual (muda quando a pessoa entra ou sai da conta). */
    fun observarUid(): Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarEntrada(): Flow<ChamadaEntrada?> = observarUid().flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else callbackFlow {
            val reg = db.collection("sinais").whereEqualTo("para", uid).addSnapshotListener { snap, erro ->
                if (erro != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val agora = System.currentTimeMillis()
                val c = snap?.documents?.firstOrNull { (it.getString("estado") == ESTADO_A_TOCAR) && (it.getLong("tocaAte") ?: 0L) > agora }
                trySend(
                    c?.let {
                        ChamadaEntrada(it.id, it.getString("de") ?: "", it.getString("deNome") ?: "Chamada", it.getBoolean("video") == true, it.getLong("tocaAte") ?: 0L)
                    }
                )
            }
            awaitClose { reg.remove() }
        }
    }
}
