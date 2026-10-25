package com.familia.controloparental.admin

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Sem Cloud Functions nem TTL: o login (ou abertura da app) de QUALQUER utilizador dispara a limpeza.
 *  - Super Administrador: limpeza completa com registo (usa a hora do servidor).
 *  - Outros utilizadores: limpeza silenciosa, sem ecrã nem aviso. As regras só deixam apagar
 *    documentos cujo `expiresAt` já passou (verificado pelo servidor, não pelo relógio do telemóvel).
 * Corre no máximo uma vez por abertura da app.
 */
object LimpezaNoLogin {
    @Volatile
    private var aCorrer = false
    @Volatile
    private var ultimaUid: String? = null

    suspend fun executar() {
        val utilizador = FirebaseAuth.getInstance().currentUser ?: return
        if (aCorrer || ultimaUid == utilizador.uid) return
        aCorrer = true
        try {
            val repo = RetencaoRepositorio()
            if (repo.ehSuperAdmin()) {
                if (repo.haExpirados()) repo.executarLimpeza()
            } else {
                limpezaSilenciosa(utilizador.uid)
            }
            ultimaUid = utilizador.uid
        } catch (_: Exception) {
        } finally {
            aCorrer = false
        }
    }

    private suspend fun limpezaSilenciosa(uid: String) {
        val db = FirebaseFirestore.getInstance()
        val agora = Timestamp.now()

        suspend fun apagar(consulta: com.google.firebase.firestore.Query) {
            repeat(3) { // no máximo 3 lotes por coleção em cada abertura
                val docs = try { consulta.limit(400).get().await().documents } catch (_: Exception) { return }
                if (docs.isEmpty()) return
                try {
                    val lote = db.batch()
                    docs.forEach { lote.delete(it.reference) }
                    lote.commit().await()
                } catch (_: Exception) { return }
                if (docs.size < 400) return
            }
        }

        for (c in listOf("publicacoes", "comentarios", "reacoes")) {
            apagar(db.collection(c).whereLessThanOrEqualTo("expiresAt", agora))
        }
        val conversas = try {
            db.collection("conversas").whereArrayContains("participantes", uid).get().await().documents.map { it.id }
        } catch (_: Exception) { emptyList() }
        for (id in conversas) {
            // Só igualdade (sem índice composto); o filtro por data é feito aqui.
            val docs = try {
                db.collection("mensagens").whereEqualTo("conversaId", id).get().await().documents
                    .filter { (it.getTimestamp("expiresAt")?.seconds ?: Long.MAX_VALUE) <= agora.seconds }
            } catch (_: Exception) { emptyList() }
            docs.chunked(400).forEach { grupo ->
                try {
                    val lote = db.batch()
                    grupo.forEach { lote.delete(it.reference) }
                    lote.commit().await()
                } catch (_: Exception) { }
            }
        }
    }
}
