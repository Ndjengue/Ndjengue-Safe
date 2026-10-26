package com.familia.controloparental.filho

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import com.familia.controloparental.vpn.FiltroVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Objetos partilhados pelos serviços e receivers do lado da criança. */
object Ambiente {
    val repo: FirestoreRepositorio by lazy { FirestoreRepositorio() }
    val escopo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    const val CHAVE_ARMADA = "protecao_armada"
    const val CHAVE_PIN_ATIVO = "pin_protecao_ativa"
    const val CHAVE_BLOQUEIO_ATIVO = "bloqueio_ecra_ativo"
    const val CHAVE_BLOQUEIO_TOTAL = "ecra_bloqueado_agora"
    const val CHAVE_NOME_CRIANCA = "crianca_nome"
}

/** Lê o estado real das proteções do telemóvel da criança. */
object LeitorProtecao {

    fun componenteAdmin(ctx: Context) = ComponentName(ctx, AdminDispositivoReceiver::class.java)

    fun acessibilidadeAtiva(ctx: Context): Boolean {
        val componente = ComponentName(ctx, ServicoAcessibilidade::class.java)
        val ativos = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return ativos.split(':').any {
            it.equals(componente.flattenToString(), true) || it.equals(componente.flattenToShortString(), true)
        }
    }

    fun adminAtivo(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(componenteAdmin(ctx))
    }

    fun usoAppsPermitido(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val modo = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return modo == AppOpsManager.MODE_ALLOWED
    }

    fun localizacaoPermitida(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun localizacaoSempre(ctx: Context): Boolean {
        if (!localizacaoPermitida(ctx)) return false
        if (Build.VERSION.SDK_INT < 29) return true
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun notificacoesPermitidas(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun semRestricaoBateria(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun ler(ctx: Context): EstadoProtecao = EstadoProtecao(
        vpnAtiva = FiltroVpnService.ativo,
        acessibilidadeAtiva = acessibilidadeAtiva(ctx),
        adminAtivo = adminAtivo(ctx),
        localizacaoPermitida = localizacaoPermitida(ctx),
        localizacaoSempre = localizacaoSempre(ctx),
        usoAppsPermitido = usoAppsPermitido(ctx),
        notificacoesPermitidas = notificacoesPermitidas(ctx)
    )

    fun descreverRede(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val rede = cm.activeNetwork ?: return "Sem ligação"
            val caps = cm.getNetworkCapabilities(rede) ?: return "Sem ligação"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Ligado"
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Apps que a criança consegue abrir (inclui YouTube, Chrome, etc. mesmo sendo de sistema). */
    fun listarAppsLancaveis(ctx: Context, comIcones: Boolean): List<AppInstalada> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolvidos = pm.queryIntentActivities(intent, 0)
        return resolvidos
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val pacote = ri.activityInfo.packageName
                val instalado = try { pm.getPackageInfo(pacote, 0).firstInstallTime } catch (e: Exception) { 0L }
                AppInstalada(
                    nomePacote = pacote,
                    nome = ri.loadLabel(pm).toString(),
                    iconeBase64 = if (comIcones) {
                        try { Imagens.iconeParaBase64(ri.loadIcon(pm)) } catch (e: Exception) { null }
                    } else null,
                    instaladoEm = instalado
                )
            }
            .sortedBy { it.nome.lowercase() }
    }
}

/** Envia alertas aos pais (com limite de frequência para não inundar o Firestore). */
object AlertasFilho {
    private val ultimos = ConcurrentHashMap<String, Long>()

    fun enviar(ctx: Context, tipo: TipoAlerta, mensagem: String, intervaloMinMs: Long = 30_000L) {
        val app = ctx.applicationContext
        val chave = tipo.name
        val agora = System.currentTimeMillis()
        val anterior = ultimos[chave] ?: 0L
        if (agora - anterior < intervaloMinMs) return
        ultimos[chave] = agora
        Ambiente.escopo.launch {
            val cache = CacheLocal(app)
            val fid = cache.lerFamiliaId() ?: return@launch
            val did = cache.lerDispositivoId() ?: return@launch
            val nome = cache.lerTexto(Ambiente.CHAVE_NOME_CRIANCA) ?: ""
            try {
                Ambiente.repo.enviarAlerta(
                    fid, Alerta(dispositivoId = did, criancaNome = nome, tipo = tipo, mensagem = mensagem)
                )
            } catch (_: Exception) {
            }
        }
    }
}
