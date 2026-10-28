package com.familia.controloparental.servico

import android.content.Context
import com.familia.controloparental.admin.ComunidadeRepositorio
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Presença de quem usa o chat (pais e criança): enquanto a app está aberta ou o serviço de proteção
 * está ligado, marca "online" a cada 30 s — não só dentro do ecrã do chat.
 * "Aparecer offline" é uma escolha de cada pessoa e vale para todos os que a veem.
 */
object PulsoPresenca {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val origens = mutableSetOf<String>()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("presenca", Context.MODE_PRIVATE)

    fun visivel(ctx: Context): Boolean = !prefs(ctx).getBoolean("offline", false)

    fun definirVisivel(ctx: Context, visivel: Boolean) {
        prefs(ctx).edit().putBoolean("offline", !visivel).apply()
        escopo.launch { enviar(ctx.applicationContext) }
    }

    private suspend fun enviar(ctx: Context) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        try { ComunidadeRepositorio().atualizarPresenca(visivel(ctx)) } catch (_: Exception) {}
    }

    @Synchronized
    fun iniciar(ctx: Context, origem: String) {
        origens.add(origem)
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = escopo.launch {
            while (isActive) {
                enviar(app)
                delay(30_000)
            }
        }
    }

    @Synchronized
    fun parar(origem: String) {
        origens.remove(origem)
        if (origens.isEmpty()) { job?.cancel(); job = null }
    }
}
