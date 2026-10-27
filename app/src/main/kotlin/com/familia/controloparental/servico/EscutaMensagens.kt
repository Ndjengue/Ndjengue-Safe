package com.familia.controloparental.servico

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.familia.controloparental.admin.ComunidadeRepositorio
import com.familia.controloparental.comum.MainActivity
import com.familia.controloparental.comum.Notificacoes
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notificações de mensagens novas com a app fechada (como o WhatsApp): os serviços de proteção
 * já correm em primeiro plano e ouvem aqui as conversas e as mensagens do Firestore.
 * Com a app visível não se notifica. A notificação tem "Responder" direto e abre a conversa.
 */
object EscutaMensagens {
    const val EXTRA_CONVERSA = "abrir_conversa"
    const val CHAVE_RESPOSTA = "resposta_texto"
    private const val PREFS = "escuta_mensagens"

    @Volatile private var regConversas: ListenerRegistration? = null
    @Volatile private var regMensagens: ListenerRegistration? = null
    @Volatile private var uidAtual: String? = null
    private var idsAtuais: List<String> = emptyList()
    private val nomes = HashMap<String, Map<String, String>>() // conversaId -> uid -> nome
    private val historico = HashMap<String, MutableList<Triple<String, String, Long>>>() // conversaId -> (uid, texto, hora)

    fun iniciar(ctx: Context) {
        val app = ctx.applicationContext
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (regConversas != null && uidAtual == uid) return
        parar()
        uidAtual = uid
        val db = FirebaseFirestore.getInstance()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("ultima_$uid")) prefs.edit().putLong("ultima_$uid", System.currentTimeMillis()).apply()

        regConversas = db.collection("conversas").whereArrayContains("participantes", uid)
            .addSnapshotListener { snap, erro ->
                if (erro != null || snap == null) return@addSnapshotListener
                val ids = snap.documents.map { it.id }.take(30)
                for (d in snap.documents) {
                    nomes[d.id] = (d.get("nomes") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
                }
                if (ids == idsAtuais && regMensagens != null) return@addSnapshotListener
                idsAtuais = ids
                regMensagens?.remove()
                regMensagens = null
                if (ids.isEmpty()) return@addSnapshotListener
                regMensagens = db.collection("mensagens").whereIn("conversaId", ids)
                    .addSnapshotListener { ms, e2 ->
                        if (e2 != null || ms == null) return@addSnapshotListener
                        tratar(app, uid, ms.documentChanges)
                    }
            }
    }

    fun parar() {
        regConversas?.remove(); regConversas = null
        regMensagens?.remove(); regMensagens = null
        uidAtual = null
        idsAtuais = emptyList()
    }

    private fun tratar(app: Context, uid: String, mudancas: List<DocumentChange>) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultima = prefs.getLong("ultima_$uid", System.currentTimeMillis())
        var maior = ultima
        val novas = mudancas.filter { it.type == DocumentChange.Type.ADDED }.mapNotNull { ch ->
            val d = ch.document
            if (d.metadata.hasPendingWrites()) return@mapNotNull null
            val autor = d.getString("autorUid") ?: return@mapNotNull null
            val hora = d.getTimestamp("criadoEm", ServerTimestampBehavior.NONE)?.toDate()?.time ?: return@mapNotNull null
            if (autor == uid || hora <= ultima) return@mapNotNull null
            if (hora > maior) maior = hora
            val texto = d.getString("texto").orEmpty().ifBlank { if (!d.getString("imagemBase64").isNullOrBlank()) "📷 Imagem" else "" }
            if (texto.isBlank()) return@mapNotNull null
            arrayOf(d.getString("conversaId") ?: "", autor, texto, hora.toString())
        }.sortedBy { it[3].toLong() }
        if (maior > ultima) prefs.edit().putLong("ultima_$uid", maior).apply()
        if (novas.isEmpty() || MainActivityVisivel.visivel) return
        for (n in novas) {
            val lista = historico.getOrPut(n[0]) { mutableListOf() }
            lista.add(Triple(n[1], n[2], n[3].toLong()))
            while (lista.size > 8) lista.removeAt(0)
        }
        novas.map { it[0] }.distinct().forEach { mostrar(app, it) }
    }

    private fun mostrar(ctx: Context, conversaId: String) {
        try {
            Notificacoes.garantirCanais(ctx)
            if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
            val msgs = historico[conversaId] ?: return
            val mapa = nomes[conversaId] ?: emptyMap()
            val euUid = uidAtual ?: return
            val eu = Person.Builder().setName("Eu").build()
            val estilo = NotificationCompat.MessagingStyle(eu)
            msgs.forEach { (autor, texto, hora) ->
                estilo.addMessage(texto, hora, Person.Builder().setName(mapa[autor] ?: "Mensagem").setKey(autor).build())
            }
            val remetente = mapa[msgs.last().first] ?: "Nova mensagem"
            val id = 4000 + (conversaId.hashCode() and 0x3FFF)
            val abrir = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CONVERSA, conversaId)
            val piAbrir = PendingIntent.getActivity(ctx, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val entrada = RemoteInput.Builder(CHAVE_RESPOSTA).setLabel("Responder").build()
            val rec = Intent(ctx, RespostaMensagemReceiver::class.java).putExtra(EXTRA_CONVERSA, conversaId).putExtra("notif_id", id)
            val piResp = PendingIntent.getBroadcast(
                ctx, id, rec,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            )
            val acao = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Responder", piResp)
                .addRemoteInput(entrada).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false).build()
            val n = NotificationCompat.Builder(ctx, Notificacoes.CANAL_MENSAGENS)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(remetente)
                .setContentText(msgs.last().second)
                .setStyle(estilo)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(piAbrir)
                .addAction(acao)
                .build()
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /** Ao abrir a app, limpa as notificações de mensagens. */
    fun limparNotificacoes(ctx: Context) {
        historico.clear()
        val nm = NotificationManagerCompat.from(ctx)
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).activeNotifications
                .filter { it.id in 4000..(4000 + 0x3FFF) }.forEach { nm.cancel(it.id) }
        } catch (_: Exception) {
        }
    }
}

/** Estado de visibilidade da app (partilhado com as chamadas). */
object MainActivityVisivel {
    val visivel: Boolean get() = EscutaChamadas.appVisivel
}

/** "Responder" na notificação: envia a mensagem sem abrir a app. */
class RespostaMensagemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversaId = intent.getStringExtra(EscutaMensagens.EXTRA_CONVERSA) ?: return
        val id = intent.getIntExtra("notif_id", 0)
        val texto = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EscutaMensagens.CHAVE_RESPOSTA)?.toString()?.trim()
        if (texto.isNullOrBlank()) return
        val pendente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ComunidadeRepositorio().enviarMensagem(conversaId, texto.take(1000))
            } catch (_: Exception) {
            } finally {
                try { NotificationManagerCompat.from(context).cancel(id) } catch (_: Exception) {}
                pendente.finish()
            }
        }
    }
}
