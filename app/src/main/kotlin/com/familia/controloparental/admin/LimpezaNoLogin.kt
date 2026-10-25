package com.familia.controloparental.admin

import com.google.firebase.auth.FirebaseAuth

/**
 * Sem Cloud Functions nem TTL: sempre que o Super Administrador abre a app ou inicia sessão,
 * apaga o que expirou (usando a hora do servidor). Contas normais não fazem nada.
 */
object LimpezaNoLogin {
    @Volatile
    private var aCorrer = false

    suspend fun executar() {
        if (aCorrer) return
        val utilizador = FirebaseAuth.getInstance().currentUser ?: return
        if (utilizador.isAnonymous) return
        aCorrer = true
        try {
            val repo = RetencaoRepositorio()
            if (repo.ehSuperAdmin() && repo.haExpirados()) repo.executarLimpeza()
        } catch (_: Exception) {
        } finally {
            aCorrer = false
        }
    }
}
