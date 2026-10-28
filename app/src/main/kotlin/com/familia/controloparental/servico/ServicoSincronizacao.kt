package com.familia.controloparental.servico

import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.filho.AlertasFilho
import com.familia.controloparental.filho.Ambiente
import com.familia.controloparental.filho.LeitorProtecao
import com.familia.controloparental.vpn.FiltroVpnService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.LocalDate
import java.time.ZoneId

/**
 * Coração do telemóvel da criança. Serviço em primeiro plano que:
 *  - mantém a cache local (apps, regras, horários, PIN) sempre atualizada a partir do Firestore;
 *  - envia um sinal de vida a cada 45 s (é isto que mostra "online/offline" ao pai);
 *  - envia a localização (a cada ~1 min; a cada ~5 s quando o pai abre o mapa em direto);
 *  - envia o tempo de ecrã do dia e a lista de apps instaladas;
 *  - avisa os pais quando uma proteção é desligada;
 *  - aplica as liberações aprovadas e apaga tudo localmente se o pai remover o dispositivo.
 */
class ServicoSincronizacao : Service() {

    companion object {
        private const val ID_NOTIFICACAO = 1001

        fun iniciar(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ServicoSincronizacao::class.java))
            } catch (_: Exception) {
            }
        }

        fun reiniciar(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ServicoSincronizacao::class.java))
            } catch (_: Exception) {
            }
            iniciar(ctx)
        }
    }

    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var cache: CacheLocal
    private val repo get() = Ambiente.repo

    private var iniciado = false
    private var fid = ""
    private var did = ""

    // Localização
    private lateinit var clienteLoc: FusedLocationProviderClient
    private var callbackLoc: LocationCallback? = null
    private var modoAoVivo = false
    @Volatile private var rastreioAte = 0L
    private var ultimoEnvioLoc = 0L
    private var ultimoHistorico = 0L
    private var ultimoPontoHistorico: Location? = null

    private var estadoAnterior: EstadoProtecao? = null
    private val gatilhoApps = Channel<Unit>(Channel.CONFLATED)
    private var ultimoTotalUso = -1L

    private var receptorDesligar: BroadcastReceiver? = null
    private var receptorPacotes: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Notificacoes.garantirCanais(this)
        entrarEmPrimeiroPlano()
        cache = CacheLocal(applicationContext)
        clienteLoc = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!iniciado) {
            iniciado = true
            EscutaChamadas.iniciar(this)
        EscutaMensagens.iniciar(this)
        PulsoPresenca.iniciar(this, "sincronizacao")
            escopo.launch { arrancar() }
        }
        return START_STICKY
    }

    private fun entrarEmPrimeiroPlano() {
        val notificacao = NotificationCompat.Builder(this, Notificacoes.CANAL_SERVICO)
            .setContentTitle(getString(com.familia.controloparental.R.string.app_name))
            .setContentText("Proteção ativa")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        try {
            val temLoc = LeitorProtecao.localizacaoPermitida(this)
            if (Build.VERSION.SDK_INT >= 34) {
                var tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (temLoc) tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                ServiceCompat.startForeground(this, ID_NOTIFICACAO, notificacao, tipo)
            } else if (Build.VERSION.SDK_INT >= 29 && temLoc) {
                ServiceCompat.startForeground(this, ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(ID_NOTIFICACAO, notificacao)
            }
        } catch (e: Exception) {
            try {
                startForeground(ID_NOTIFICACAO, notificacao)
            } catch (_: Exception) {
                stopSelf()
            }
        }
    }

    private suspend fun arrancar() {
        val f = cache.lerFamiliaId()
        val d = cache.lerDispositivoId()
        if (f == null || d == null) {
            stopSelf()
            return
        }
        fid = f
        did = d

        registarReceptores()

        // Se o filtro de sites estava autorizado, volta a ligá-lo (ex.: depois de reiniciar).
        if (android.net.VpnService.prepare(this) == null && !FiltroVpnService.ativo) {
            FiltroVpnService.iniciar(this)
        }

        // --- Dados vindos dos pais -> cache local ---
        escopo.launch { repo.observarApps(fid, did).collect { cache.guardarApps(it) } }
        escopo.launch { repo.observarRegrasUrl(fid, did).collect { cache.guardarRegrasUrl(it) } }
        escopo.launch { repo.observarPerfisHorario(fid, did).collect { cache.guardarPerfisHorario(it) } }
        escopo.launch {
            repo.observarSegredoAdmin(fid).collect { s -> if (s != null) cache.guardarSegredoAdmin(s.first, s.second) }
        }
        escopo.launch { observarDispositivo() }
        escopo.launch { observarPedidos() }

        // --- Tarefas periódicas ---
        escopo.launch { while (isActive) { enviarSinalDeVida(); delay(45_000) } }
        escopo.launch { while (isActive) { sincronizarApps(); withTimeoutOrNull(10 * 60_000L) { gatilhoApps.receive() } } }
        escopo.launch { delay(20_000); while (isActive) { enviarTempoEcra(); delay(5 * 60_000L) } }
        escopo.launch { while (isActive) { ajustarLocalizacao(); delay(15_000) } }
    }

    // ---------- Dispositivo (perfil, rastreio em direto, remoção) ----------

    private suspend fun observarDispositivo() {
        repo.observarDispositivo(fid, did).collect { disp ->
            if (disp == null) {
                desemparelharLocalmente()
                return@collect
            }
            rastreioAte = disp.rastreioAoVivoAte
            cache.guardarTexto(Ambiente.CHAVE_NOME_CRIANCA, disp.crianca.nome)
            cache.guardarTexto("crianca_telefone", disp.crianca.telefone)
            cache.guardarBool(Ambiente.CHAVE_PIN_ATIVO, disp.pinProtecaoAtiva)
            cache.guardarBool(Ambiente.CHAVE_BLOQUEIO_ATIVO, disp.bloqueioEcraAtivo)
            cache.guardarBool(Ambiente.CHAVE_BLOQUEIO_TOTAL, disp.ecraBloqueadoAgora)
        }
    }

    private suspend fun desemparelharLocalmente() {
        try {
            startService(Intent(this, FiltroVpnService::class.java).setAction(FiltroVpnService.ACAO_PARAR))
        } catch (_: Exception) {
        }
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.removeActiveAdmin(LeitorProtecao.componenteAdmin(this))
        } catch (_: Exception) {
        }
        cache.limparTudo()
        stopSelf()
    }

    // ---------- Pedidos aprovados ----------

    private suspend fun observarPedidos() {
        val notificados = HashSet<String>()
        var primeira = true
        repo.observarPedidosDoDispositivo(fid, did).collect { lista ->
            val agora = System.currentTimeMillis()
            for (p in lista) {
                if (p.status == StatusPedidoLiberacao.APROVADO && p.liberadoAte > agora) {
                    cache.guardarLiberacao(p.alvo, p.liberadoAte)
                }
                val decidido = p.status == StatusPedidoLiberacao.APROVADO || p.status == StatusPedidoLiberacao.RECUSADO
                if (decidido && notificados.add(p.id) && !primeira) {
                    val texto = if (p.status == StatusPedidoLiberacao.APROVADO)
                        "Os teus pais permitiram \"${p.rotulo}\" durante ${p.duracaoAprovadaMinutos} min."
                    else "Os teus pais não permitiram \"${p.rotulo}\" agora."
                    Notificacoes.mostrar(
                        this, 5000 + (p.id.hashCode() and 0xFFF), Notificacoes.CANAL_AVISOS,
                        if (p.status == StatusPedidoLiberacao.APROVADO) "Pedido aprovado" else "Pedido recusado",
                        texto
                    )
                } else if (decidido) {
                    notificados.add(p.id)
                }
            }
            primeira = false
        }
    }

    // ---------- Sinal de vida ----------

    private fun temRede(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val rede = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(rede) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun enviarSinalDeVida() {
        val estado = LeitorProtecao.ler(this)
        detetarMudancas(estado)

        if (estado.adminAtivo && estado.acessibilidadeAtiva) {
            cache.guardarBool(Ambiente.CHAVE_ARMADA, true)
        }
        if (!temRede()) return

        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val nivel = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val escala = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percentagem = if (nivel >= 0 && escala > 0) nivel * 100 / escala else -1
        val estadoBat = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val aCarregar = estadoBat == BatteryManager.BATTERY_STATUS_CHARGING || estadoBat == BatteryManager.BATTERY_STATUS_FULL

        withTimeoutOrNull(15_000) {
            try {
                repo.enviarHeartbeat(fid, did, percentagem, aCarregar, LeitorProtecao.descreverRede(this@ServicoSincronizacao), estado)
            } catch (_: Exception) {
            }
        }
    }

    /** Avisa os pais quando uma proteção que estava ligada passa a desligada. */
    private fun detetarMudancas(novo: EstadoProtecao) {
        val antes = estadoAnterior
        estadoAnterior = novo
        if (antes == null) return
        if (antes.acessibilidadeAtiva && !novo.acessibilidadeAtiva) {
            AlertasFilho.enviar(this, TipoAlerta.ACESSIBILIDADE_DESATIVADA, "O serviço de acessibilidade foi desligado.", 120_000L)
        }
        if (antes.adminAtivo && !novo.adminAtivo) {
            AlertasFilho.enviar(this, TipoAlerta.ADMIN_DESATIVADO, "A proteção contra desinstalação foi desligada.", 120_000L)
        }
        if (antes.vpnAtiva && !novo.vpnAtiva) {
            AlertasFilho.enviar(this, TipoAlerta.VPN_DESATIVADA, "O filtro de sites foi desligado.", 120_000L)
        }
        if (antes.localizacaoPermitida && !novo.localizacaoPermitida) {
            AlertasFilho.enviar(this, TipoAlerta.LOCALIZACAO_DESATIVADA, "A permissão de localização foi retirada.", 120_000L)
        }
    }

    // ---------- Apps instaladas ----------

    private suspend fun sincronizarApps() {
        if (!temRede()) return
        try {
            val apps = LeitorProtecao.listarAppsLancaveis(this, comIcones = true)
            repo.sincronizarApps(fid, did, apps)
        } catch (_: Exception) {
        }
    }

    // ---------- Tempo de ecrã ----------

    private suspend fun enviarTempoEcra() {
        if (!LeitorProtecao.usoAppsPermitido(this) || !temRede()) return
        try {
            val uso = calcularUsoHoje()
            val total = uso.values.sum()
            if (total == ultimoTotalUso) return
            ultimoTotalUso = total
            repo.guardarTempoEcraDia(fid, did, LocalDate.now().toString(), uso)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun calcularUsoHoje(): Map<String, Long> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val inicio = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val agora = System.currentTimeMillis()
        val eventos = usm.queryEvents(inicio, agora)
        val ev = UsageEvents.Event()
        val abertos = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> abertos[pkg] = ev.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val i = abertos.remove(pkg)
                    if (i != null && ev.timeStamp > i) total[pkg] = (total[pkg] ?: 0L) + (ev.timeStamp - i)
                }
            }
        }
        abertos.forEach { (pkg, i) ->
            total[pkg] = (total[pkg] ?: 0L) + (agora - i).coerceAtMost(30 * 60_000L)
        }
        return total.filter { it.value >= 1_000L && it.key != packageName && it.key != "com.android.systemui" }
    }

    // ---------- Localização ----------

    private fun ajustarLocalizacao() {
        if (!LeitorProtecao.localizacaoPermitida(this)) {
            pararLocalizacao()
            return
        }
        val aoVivo = rastreioAte > System.currentTimeMillis()
        if (callbackLoc != null && aoVivo == modoAoVivo) return
        pararLocalizacao()
        modoAoVivo = aoVivo

        val pedido = if (aoVivo) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(3_000L)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
                .setMinUpdateIntervalMillis(30_000L)
                .build()
        }
        val cb = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                resultado.lastLocation?.let { aoReceberLocalizacao(it) }
            }
        }
        callbackLoc = cb
        try {
            clienteLoc.requestLocationUpdates(pedido, cb, Looper.getMainLooper())
            if (!aoVivo) {
                clienteLoc.lastLocation.addOnSuccessListener { l -> if (l != null) aoReceberLocalizacao(l) }
            }
        } catch (e: SecurityException) {
            callbackLoc = null
        }
    }

    private fun pararLocalizacao() {
        callbackLoc?.let {
            try { clienteLoc.removeLocationUpdates(it) } catch (_: Exception) {}
        }
        callbackLoc = null
    }

    private fun aoReceberLocalizacao(l: Location) {
        val agora = System.currentTimeMillis()
        val intervalo = if (modoAoVivo) 4_000L else 55_000L
        if (agora - ultimoEnvioLoc < intervalo) return
        if (!temRede()) return
        ultimoEnvioLoc = agora

        val ponto = LocalizacaoPonto(l.latitude, l.longitude, l.accuracy, l.speed, agora)
        val guardarHistorico = run {
            val ult = ultimoPontoHistorico
            val passou = agora - ultimoHistorico > 5 * 60_000L
            val moveu = ult == null || l.distanceTo(ult) > 50f
            (passou && moveu) || agora - ultimoHistorico > 15 * 60_000L
        }
        if (guardarHistorico) {
            ultimoHistorico = agora
            ultimoPontoHistorico = l
        }
        escopo.launch {
            try {
                repo.atualizarLocalizacao(fid, did, ponto)
                if (guardarHistorico) repo.registarHistoricoLocalizacao(fid, did, ponto)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Receptores ----------

    private fun registarReceptores() {
        val rDesligar = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // O telemóvel está a desligar: tenta avisar já o pai (o tempo é curto).
                try {
                    runBlocking { withTimeoutOrNull(1_800) { repo.marcarOffline(fid, did) } }
                } catch (_: Exception) {
                }
            }
        }
        receptorDesligar = rDesligar
        val filtroDesligar = IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
        }
        ContextCompat.registerReceiver(this, rDesligar, filtroDesligar, ContextCompat.RECEIVER_EXPORTED)

        val rPacotes = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                escopo.launch {
                    delay(3_000)
                    gatilhoApps.trySend(Unit)
                }
            }
        }
        receptorPacotes = rPacotes
        val filtroPacotes = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, rPacotes, filtroPacotes, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        EscutaChamadas.parar()
        EscutaMensagens.parar()
        PulsoPresenca.parar("sincronizacao")
        pararLocalizacao()
        receptorDesligar?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receptorPacotes?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        escopo.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
