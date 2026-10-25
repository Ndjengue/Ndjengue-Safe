package com.familia.controloparental.admin

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Marca a última atividade da conta (atividade/{uid}) no máximo de 6 em 6 horas.
 * Alimenta "contas ativas" no painel do Super Administrador sem Cloud Functions.
 */
object AtividadeRegisto {
    @Volatile
    private var ultimo = 0L

    suspend fun registar() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val agora = System.currentTimeMillis()
        if (agora - ultimo < 6 * 3_600_000L) return
        try {
            FirebaseFirestore.getInstance().collection("atividade").document(u.uid)
                .set(mapOf("ultimaAtividade" to FieldValue.serverTimestamp())).await()
            ultimo = agora
        } catch (_: Exception) {
        }
    }
}
