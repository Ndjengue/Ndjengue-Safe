package com.familia.controloparental.servico

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familia.controloparental.chamadas.Sinalizacao
import com.familia.controloparental.comum.MainActivity
import com.familia.controloparental.comum.Notificacoes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Faz a chamada tocar mesmo com a app fechada ou em segundo plano: os serviços da proteção
 * (pais e criança) já correm em primeiro plano e ouvem aqui os pedidos de chamada do Firestore.
 * Com a app aberta quem toca é o próprio ecrã de chamada, por isso aqui não se duplica.
 */
object EscutaChamadas {
    const val ID_NOTIFICACAO = 2080
    const val EXTRA_CHAMADA = "chamada_id"
    const val EXTRA_ACAO = "chamada_acao"
    const val ACAO_ATENDER = "atender"

    @Volatile
    var appVisivel = false

    @Volatile
    private var registo: ListenerRegistration? = null
    @Volatile
    private var uidAtual: String? = null

    fun iniciar(ctx: Context) {
        val app = ctx.applicationContext
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (registo != null && uidAtual == uid) return
        parar()
        uidAtual = uid
        registo = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("sinais").whereEqualTo("para", uid)
            .addSnapshotListener { snap, erro ->
                if (erro != null || snap == null) return@addSnapshotListener
                val agora = System.currentTimeMillis()
                val c = snap.documents.firstOrNull {
                    it.getString("estado") == Sinalizacao.ESTADO_A_TOCAR && (it.getLong("tocaAte") ?: 0L) > agora
                }
                if (c == null || appVisivel) {
                    NotificationManagerCompat.from(app).cancel(ID_NOTIFICACAO)
                } else {
                    mostrar(app, c.id, c.getString("deNome") ?: "Chamada", c.getBoolean("video") == true, c.getLong("tocaAte") ?: 0L)
                }
            }
    }

    fun parar() {
        registo?.remove()
        registo = null
        uidAtual = null
    }

    private fun mostrar(ctx: Context, id: String, nome: String, video: Boolean, tocaAte: Long) {
        try {
            Notificacoes.garantirCanais(ctx)
            val abrir = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CHAMADA, id)
            val piAbrir = PendingIntent.getActivity(ctx, 1, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val piAtender = PendingIntent.getActivity(
                ctx, 2, Intent(abrir).putExtra(EXTRA_ACAO, ACAO_ATENDER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val piRecusar = PendingIntent.getBroadcast(
                ctx, 3, Intent(ctx, RecusarChamadaReceiver::class.java).putExtra(EXTRA_CHAMADA, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, Notificacoes.CANAL_CHAMADAS)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(nome)
                .setContentText(if (video) "Videochamada a receber…" else "Chamada de voz a receber…")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter((tocaAte - System.currentTimeMillis()).coerceIn(1_000L, 60_000L))
                .setContentIntent(piAbrir)
                .setFullScreenIntent(piAbrir, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Recusar", piRecusar)
                .addAction(android.R.drawable.ic_menu_call, "Atender", piAtender)
                .build()
            n.flags = n.flags or android.app.Notification.FLAG_INSISTENT
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                NotificationManagerCompat.from(ctx).notify(ID_NOTIFICACAO, n)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}

/** "Recusar" na notificação de chamada. */
class RecusarChamadaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EscutaChamadas.EXTRA_CHAMADA) ?: return
        NotificationManagerCompat.from(context).cancel(EscutaChamadas.ID_NOTIFICACAO)
        val pendente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Sinalizacao().recusar(id) } finally { pendente.finish() }
        }
    }
}
