package com.familia.controloparental.servico

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familia.controloparental.comum.Notificacoes
import com.familia.controloparental.filho.Ambiente
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pedido de ajuda da criança, no telemóvel dos pais.
 * - Com a permissão "Sobrepor a outras apps": janela por cima de tudo, até ser vista.
 * - Sem ela: notificação com ecrã inteiro (abre SosActivity, mesmo com o ecrã bloqueado).
 * Em ambos os casos toca um alarme e vibra até alguém carregar em "Já vi".
 */
object AlertaSos {
    private const val ID_NOTIF = 9100
    const val EXTRA_FID = "sos_fid"
    const val EXTRA_ALERTA = "sos_alerta"
    const val EXTRA_NOME = "sos_nome"
    const val EXTRA_MSG = "sos_msg"

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vista: View? = null
    private var toque: Ringtone? = null

    fun podeSobrepor(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun mostrar(ctx: Context, fid: String, alertaId: String, nome: String, mensagem: String) {
        val app = ctx.applicationContext
        val temOverlay = try { podeSobrepor(app) } catch (_: Exception) { false }
        notificar(app, fid, alertaId, nome, mensagem, comEcraInteiro = !temOverlay)
        if (temOverlay) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try { janela(app, fid, alertaId, nome, mensagem) } catch (_: Exception) {}
            }
        }
    }

    private fun notificar(ctx: Context, fid: String, alertaId: String, nome: String, msg: String, comEcraInteiro: Boolean) {
        try {
            Notificacoes.garantirCanais(ctx)
            val abrir = Intent(ctx, SosActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_FID, fid).putExtra(EXTRA_ALERTA, alertaId)
                .putExtra(EXTRA_NOME, nome).putExtra(EXTRA_MSG, msg)
            val pi = PendingIntent.getActivity(ctx, 77, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = NotificationCompat.Builder(ctx, Notificacoes.CANAL_SOS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🆘 $nome pediu ajuda")
                .setContentText(msg)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(pi)
            if (comEcraInteiro) b.setFullScreenIntent(pi, true)
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                NotificationManagerCompat.from(ctx).notify(ID_NOTIF, b.build())
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun janela(ctx: Context, fid: String, alertaId: String, nome: String, msg: String) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        vista?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        val v = construirVista(ctx, nome, msg) { visto(ctx, fid, alertaId) }
        val tipo = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, tipo,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        vista = v
        tocar(ctx)
    }

    /** Marca como visto: tira a janela, o alarme e a notificação. */
    fun visto(ctx: Context, fid: String, alertaId: String) {
        val app = ctx.applicationContext
        parar(app)
        if (fid.isNotBlank() && alertaId.isNotBlank()) {
            escopo.launch { try { Ambiente.repo.marcarAlertaLido(fid, alertaId) } catch (_: Exception) {} }
        }
    }

    fun parar(ctx: Context) {
        try { NotificationManagerCompat.from(ctx).cancel(ID_NOTIF) } catch (_: Exception) {}
        try { toque?.stop() } catch (_: Exception) {}
        toque = null
        try { vibrador(ctx)?.cancel() } catch (_: Exception) {}
        vista?.let {
            try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        vista = null
    }

    @Suppress("DEPRECATION")
    private fun vibrador(ctx: Context): Vibrator? = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun tocar(ctx: Context) {
        try {
            if (toque?.isPlaying == true) return
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(ctx, uri)
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
            r.play()
            toque = r
        } catch (_: Exception) {
        }
        try {
            val padrao = longArrayOf(0, 700, 500)
            val vb = vibrador(ctx)
            if (Build.VERSION.SDK_INT >= 26) vb?.vibrate(VibrationEffect.createWaveform(padrao, 0))
            else @Suppress("DEPRECATION") vb?.vibrate(padrao, 0)
        } catch (_: Exception) {
        }
    }

    /** Vista simples (sem Compose) partilhada pela janela e pela atividade. */
    fun construirVista(ctx: Context, nome: String, msg: String, aoVisto: () -> Unit): View {
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val raiz = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2B3261E"))
            setPadding(px(28), px(48), px(28), px(48))
            isClickable = true
        }
        raiz.addView(TextView(ctx).apply {
            text = "🆘"; textSize = 72f; gravity = Gravity.CENTER
        })
        raiz.addView(TextView(ctx).apply {
            text = "${nome.ifBlank { "A criança" }} pediu ajuda"
            textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, px(16), 0, px(8))
        })
        raiz.addView(TextView(ctx).apply {
            text = msg; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(0, 0, 0, px(32))
        })
        raiz.addView(Button(ctx).apply {
            text = "Ver localização"
            isAllCaps = false; textSize = 18f; setTextColor(Color.parseColor("#B3261E"))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = px(28).toFloat() }
            setOnClickListener {
                aoVisto()
                val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (i != null) try { ctx.startActivity(i) } catch (_: Exception) {}
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)).apply { bottomMargin = px(12) })
        raiz.addView(Button(ctx).apply {
            text = "Já vi"
            isAllCaps = false; textSize = 18f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(px(2), Color.WHITE); cornerRadius = px(28).toFloat() }
            setOnClickListener { aoVisto() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)))
        return raiz
    }
}

/** Ecrã inteiro do SOS (usado quando não há permissão de sobreposição), acende o ecrã e fica por cima do bloqueio. */
class SosActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mostrarDe(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mostrarDe(intent)
    }

    private fun mostrarDe(i: Intent) {
        val fid = i.getStringExtra(AlertaSos.EXTRA_FID).orEmpty()
        val alerta = i.getStringExtra(AlertaSos.EXTRA_ALERTA).orEmpty()
        val nome = i.getStringExtra(AlertaSos.EXTRA_NOME).orEmpty()
        val msg = i.getStringExtra(AlertaSos.EXTRA_MSG).orEmpty()
        setContentView(AlertaSos.construirVista(this, nome, msg) {
            AlertaSos.visto(this, fid, alerta)
            finish()
        })
        AlertaSos.tocar(this)
    }
}
