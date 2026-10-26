package com.familia.controloparental.servico

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.familia.controloparental.comum.*
import com.familia.controloparental.filho.Ambiente
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

/**
 * No telemóvel do pai: fica a ouvir pedidos de liberação e alertas e mostra notificações locais.
 * (No plano gratuito não há notificações push do Firebase; este serviço faz esse papel.)
 */
class ServicoMonitorPai : Service() {

    companion object {
        private const val PREFS = "pai_prefs"
        private const val CHAVE_ATIVO = "monitor_ativo"
        private const val ID_FG = 2001

        fun ativo(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, 0).getBoolean(CHAVE_ATIVO, true)

        fun definirAtivo(ctx: Context, valor: Boolean) {
            ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(CHAVE_ATIVO, valor).apply()
            if (valor) iniciar(ctx) else parar(ctx)
        }

        fun iniciarSeAtivo(ctx: Context) {
            if (ativo(ctx)) iniciar(ctx)
        }

        fun iniciar(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ServicoMonitorPai::class.java))
            } catch (_: Exception) {
            }
        }

        fun parar(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ServicoMonitorPai::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var iniciado = false

    override fun onCreate() {
        super.onCreate()
        Notificacoes.garantirCanais(this)
        val n = NotificationCompat.Builder(this, Notificacoes.CANAL_SERVICO)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Ndjengue Safe")
            .setContentText("A vigiar pedidos e alertas da família")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_FG, n)
            }
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!iniciado) {
            iniciado = true
            escopo.launch { vigiar() }
        }
        return START_STICKY
    }

    private fun abrirApp(): PendingIntent {
        val i = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun vigiar() {
        EscutaChamadas.iniciar(this)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            stopSelf()
            return
        }
        val repo = Ambiente.repo
        val fid = try { repo.buscarFamiliaDoPai(uid) } catch (e: Exception) { null }
        if (fid == null) {
            stopSelf()
            return
        }
        val prefs = getSharedPreferences(PREFS, 0)
        if (prefs.getLong("ultimo_pedido", 0L) == 0L) prefs.edit().putLong("ultimo_pedido", System.currentTimeMillis()).apply()
        if (prefs.getLong("ultimo_alerta", 0L) == 0L) prefs.edit().putLong("ultimo_alerta", System.currentTimeMillis()).apply()

        escopo.launch {
            repo.observarPedidos(fid, 20).collect { lista ->
                val ultimo = prefs.getLong("ultimo_pedido", 0L)
                val novos = lista.filter { it.status == StatusPedidoLiberacao.PENDENTE && it.criadoEm > ultimo }
                if (novos.isNotEmpty()) {
                    prefs.edit().putLong("ultimo_pedido", novos.maxOf { it.criadoEm }).apply()
                    novos.forEach { p ->
                        Notificacoes.mostrar(
                            this@ServicoMonitorPai, 6000 + (p.id.hashCode() and 0xFFF), Notificacoes.CANAL_ALERTAS,
                            "Pedido de liberação",
                            "${p.criancaNome.ifBlank { "A criança" }} quer usar ${p.rotulo.ifBlank { p.alvo }} durante ${p.duracaoMinutos} min.",
                            abrirApp()
                        )
                    }
                }
            }
        }
        escopo.launch {
            repo.observarAlertas(fid, 20).collect { lista ->
                val ultimo = prefs.getLong("ultimo_alerta", 0L)
                val novos = lista.filter { !it.lido && it.criadoEm > ultimo }
                if (novos.isNotEmpty()) {
                    prefs.edit().putLong("ultimo_alerta", novos.maxOf { it.criadoEm }).apply()
                    novos.forEach { a ->
                        Notificacoes.mostrar(
                            this@ServicoMonitorPai, 7000 + (a.id.hashCode() and 0xFFF), Notificacoes.CANAL_ALERTAS,
                            if (a.tipo == TipoAlerta.PEDIDO_AJUDA) "🆘 ${a.criancaNome.ifBlank { "A criança" }} pediu ajuda"
                            else a.criancaNome.ifBlank { "Alerta" },
                            a.mensagem, abrirApp()
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        EscutaChamadas.parar()
        escopo.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
