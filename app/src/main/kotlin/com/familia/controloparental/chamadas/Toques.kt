package com.familia.controloparental.chamadas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Toque de chamada a receber (com vibração) e sinal de "a chamar" para quem liga. */
class ToqueEntrada(private val ctx: Context) {
    private var toque: Ringtone? = null
    private var vibrador: Vibrator? = null

    fun iniciar() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            toque = RingtoneManager.getRingtone(ctx, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        } catch (_: Exception) {
        }
        try {
            vibrador = if (Build.VERSION.SDK_INT >= 31)
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
        } catch (_: Exception) {
        }
    }

    fun parar() {
        try { toque?.stop() } catch (_: Exception) {}
        try { vibrador?.cancel() } catch (_: Exception) {}
        toque = null
        vibrador = null
    }
}

class ToqueSaida {
    private var gerador: ToneGenerator? = null
    @Volatile
    private var ativo = false

    fun iniciar() {
        try {
            gerador = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            ativo = true
            Thread {
                while (ativo) {
                    try { gerador?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500) } catch (_: Exception) {}
                    try { Thread.sleep(4000) } catch (_: Exception) { break }
                }
            }.start()
        } catch (_: Exception) {
        }
    }

    fun parar() {
        ativo = false
        try { gerador?.stopTone() } catch (_: Exception) {}
        try { gerador?.release() } catch (_: Exception) {}
        gerador = null
    }
}
