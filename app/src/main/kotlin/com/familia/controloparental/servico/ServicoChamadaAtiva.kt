package com.familia.controloparental.servico

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.familia.controloparental.comum.Notificacoes

/**
 * Mantém a chamada viva (microfone/câmara) quando a pessoa sai da app ou apaga o ecrã.
 * Só existe enquanto há uma chamada; mostra uma notificação discreta "Em chamada".
 */
class ServicoChamadaAtiva : Service() {
    companion object {
        private const val ID = 2090
        private const val EXTRA_VIDEO = "video"

        fun iniciar(ctx: Context, video: Boolean) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ServicoChamadaAtiva::class.java).putExtra(EXTRA_VIDEO, video))
            } catch (_: Exception) {
            }
        }

        fun parar(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ServicoChamadaAtiva::class.java)) } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notificacoes.garantirCanais(this)
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val abrir = android.app.PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, Notificacoes.CANAL_SERVICO)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Ndjengue Safe")
            .setContentText(if (video) "Videochamada em curso" else "Chamada de voz em curso")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(abrir)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                var tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (video) tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                ServiceCompat.startForeground(this, ID, n, tipo)
            } else {
                startForeground(ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
