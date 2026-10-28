package com.familia.controloparental.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Lê um documento sem falhar logo que o Firestore ainda está a ligar-se
 * ("Failed to get document because the client is offline" aparece nos primeiros segundos,
 * mesmo com internet). Tenta de novo com pausas e, no fim, usa a cópia guardada no telemóvel.
 */
suspend fun DocumentReference.lerRobusto(tentativas: Int = 4): DocumentSnapshot {
    var ultimo: Exception? = null
    repeat(tentativas) { i ->
        try {
            return get().await()
        } catch (e: Exception) {
            ultimo = e
            delay(700L * (i + 1))
        }
    }
    try {
        return get(Source.CACHE).await()
    } catch (_: Exception) {
    }
    throw ultimo ?: IllegalStateException("Sem ligação")
}

/**
 * Hora do servidor. Os "visto por último" e o online/offline vêm com a hora do SERVIDOR;
 * se o relógio do telemóvel estiver adiantado ou atrasado, o estado ficava errado.
 * Cada atualização em tempo real chega ~1 s depois de escrita, por isso a menor diferença
 * observada entre o relógio local e a hora do servidor é uma boa estimativa do desfasamento.
 */
object RelogioServidor {
    private val amostras = ArrayDeque<Long>()
    private val ultimaPorChave = HashMap<String, Long>()
    @Volatile private var desvio = 0L

    /** Hora atual segundo o servidor (aprox.). */
    fun agora(): Long = System.currentTimeMillis() - desvio

    /** Chamar quando chega uma atualização AO VIVO (não da cópia local) com a hora do servidor. */
    @Synchronized
    fun amostra(chave: String, horaServidor: Long, aoVivo: Boolean) {
        if (horaServidor <= 0L) return
        val anterior = ultimaPorChave.put(chave, horaServidor)
        if (!aoVivo || anterior == null || anterior == horaServidor) return
        amostras.addLast(System.currentTimeMillis() - horaServidor)
        while (amostras.size > 8) amostras.removeFirst()
        desvio = amostras.min()
    }
}
