package com.familia.controloparental.admin

import com.google.firebase.auth.FirebaseAuth

/**
 * Sem Cloud Functions nem TTL: sempre que o SUPER ADMINISTRADOR inicia sessão ou abre a app,
 * a limpeza corre sozinha (hora do servidor) e fica registada na auditoria.
 * Para qualquer outra conta não faz nada. Corre no máximo uma vez por abertura da app.
 */
object LimpezaNoLogin {
    @Volatile
    private var aCorrer = false
    @Volatile
    private var ultimaUid: String? = null

    suspend fun executar() {
        val utilizador = FirebaseAuth.getInstance().currentUser ?: return
        if (utilizador.isAnonymous || aCorrer || ultimaUid == utilizador.uid) return
        aCorrer = true
        try {
            val repo = RetencaoRepositorio()
            if (repo.ehSuperAdmin()) {
                if (repo.haExpirados()) repo.executarLimpeza()
                ultimaUid = utilizador.uid
            }
        } catch (_: Exception) {
        } finally {
            aCorrer = false
        }
    }
}
