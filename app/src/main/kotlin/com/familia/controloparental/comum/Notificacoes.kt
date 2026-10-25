package com.familia.controloparental.comum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Canais e envio de notificações locais (não há FCM no plano gratuito). */
object Notificacoes {
    const val CANAL_SERVICO = "servico"
    const val CANAL_AVISOS = "avisos"
    const val CANAL_ALERTAS = "alertas_pais"

    fun garantirCanais(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_SERVICO, "Proteção ativa", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra que a proteção está a funcionar."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_AVISOS, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Sites bloqueados e respostas aos pedidos."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ALERTAS, "Alertas dos filhos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pedidos de liberação, SOS e tentativas de desinstalação."
            }
        )
    }

    fun mostrar(
        ctx: Context,
        id: Int,
        canal: String,
        titulo: String,
        texto: String,
        aoClicar: PendingIntent? = null
    ) {
        try {
            garantirCanais(ctx)
            val n = NotificationCompat.Builder(ctx, canal)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .apply { if (aoClicar != null) setContentIntent(aoClicar) }
                .build()
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                NotificationManagerCompat.from(ctx).notify(id, n)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
}
