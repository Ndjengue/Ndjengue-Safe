package com.familia.controloparental.servico

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.filho.Ambiente
import kotlinx.coroutines.launch

/** Depois de reiniciar (ou atualizar a app) volta a ligar a proteção, se o telemóvel estiver emparelhado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val acao = intent.action ?: return
        if (acao != Intent.ACTION_BOOT_COMPLETED &&
            acao != Intent.ACTION_MY_PACKAGE_REPLACED &&
            acao != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val app = context.applicationContext
        val pendente = goAsync()
        Ambiente.escopo.launch {
            try {
                if (CacheLocal(app).estaEmparelhado()) ServicoSincronizacao.iniciar(app)
            } finally {
                pendente.finish()
            }
        }
    }
}
