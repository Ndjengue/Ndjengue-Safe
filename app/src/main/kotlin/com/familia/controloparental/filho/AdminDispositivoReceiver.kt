package com.familia.controloparental.filho

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.familia.controloparental.comum.TipoAlerta

/**
 * Administrador do dispositivo: o Android deixa de oferecer "Desinstalar" enquanto estiver ativo,
 * e a desativação passa pelo ecrã de PIN (ServicoAcessibilidade).
 */
class AdminDispositivoReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AlertasFilho.enviar(
            context, TipoAlerta.DESINSTALACAO_TENTADA,
            "Alguém tentou desativar a proteção contra desinstalação.", 30_000L
        )
        return "Desativar isto permite desinstalar o Ndjengue VPN e os teus pais serão avisados."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AlertasFilho.enviar(
            context, TipoAlerta.ADMIN_DESATIVADO,
            "A proteção contra desinstalação foi desativada.", 30_000L
        )
    }
}
