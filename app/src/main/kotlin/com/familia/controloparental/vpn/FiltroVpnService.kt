package com.familia.controloparental.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.filho.AlertasFilho
import com.familia.controloparental.filho.Ambiente
import com.familia.controloparental.filho.EcraBloqueioActivity
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Filtro DNS local. A VPN só encaminha para si o tráfego dirigido ao "servidor DNS" 10.8.0.1 e a
 * resolvedores públicos conhecidos (8.8.8.8, 1.1.1.1...). Todo o resto da internet segue o caminho
 * normal, por isso não há impacto na velocidade. Consultas a domínios bloqueados recebem NXDOMAIN;
 * as restantes são reencaminhadas para o DNS real através de um socket protegido.
 */
class FiltroVpnService : VpnService() {

    companion object {
        private const val TAG = "FiltroVpn"
        const val ACAO_PARAR = "com.familia.controloparental.PARAR_VPN"
        private const val IP_DNS_VIRTUAL = "10.8.0.1"
        private val DNS_PUBLICOS = listOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222", "208.67.220.220"
        )

        @Volatile var ativo: Boolean = false

        fun iniciar(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, FiltroVpnService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível iniciar a VPN: ${e.message}")
            }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var leitor: Thread? = null
    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pool = Executors.newFixedThreadPool(6)
    private val bloqueioEscrita = Any()

    @Volatile private var regras: List<RegraUrl> = emptyList()
    @Volatile private var perfis: List<PerfilHorario> = emptyList()
    @Volatile private var liberacoes: Map<String, Long> = emptyMap()
    @Volatile private var dnsReais: List<String> = emptyList()
    private var dnsReaisAtualizadoEm = 0L

    private val ultimoAviso = HashMap<String, Long>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            parar()
            return START_NOT_STICKY
        }
        iniciarVpn()
        return START_STICKY
    }

    private fun iniciarVpn() {
        if (tun != null) return
        val b = Builder()
            .setSession("Ndjengue VPN")
            .addAddress("10.8.0.2", 32)
            .addDnsServer(IP_DNS_VIRTUAL)
            .addRoute(IP_DNS_VIRTUAL, 32)
            .setMtu(1500)
            .setBlocking(true)
        DNS_PUBLICOS.forEach { b.addRoute(it, 32) }

        val interfaceVpn = try { b.establish() } catch (e: Exception) { null }
        if (interfaceVpn == null) {
            ativo = false
            stopSelf()
            return
        }
        tun = interfaceVpn
        ativo = true

        escopo.launch {
            val cache = CacheLocal(applicationContext)
            while (isActive) {
                try {
                    regras = cache.lerRegrasUrl()
                    perfis = cache.lerPerfisHorario()
                    liberacoes = cache.lerLiberacoes()
                } catch (_: Exception) {
                }
                delay(4_000)
            }
        }

        leitor = Thread({ correr(interfaceVpn) }, "filtro-dns").also { it.start() }
    }

    private fun correr(interfaceVpn: ParcelFileDescriptor) {
        val entrada = FileInputStream(interfaceVpn.fileDescriptor)
        val saida = FileOutputStream(interfaceVpn.fileDescriptor)
        val buf = ByteArray(32767)
        while (ativo) {
            val n = try { entrada.read(buf) } catch (e: Exception) { break }
            if (n < 0) break
            if (n == 0) continue
            val consulta = AnalisadorPacoteDns.analisar(buf, n) ?: continue
            tratar(consulta, saida)
        }
    }

    private fun tratar(c: AnalisadorPacoteDns.ConsultaDns, saida: FileOutputStream) {
        val dominio = c.dominio

        // DNS-over-HTTPS conhecido: bloqueia para os browsers voltarem ao DNS do sistema (filtrado).
        if (CatalogoRedes.dominiosDoh.any { MotorRegras.dominioCorresponde(dominio, it) }) {
            escrever(saida, AnalisadorPacoteDns.construirNxDomain(c))
            return
        }

        val decisao = MotorRegras.decidirDominio(dominio, regras, perfis, liberacoes)
        if (decisao != null) {
            escrever(saida, AnalisadorPacoteDns.construirNxDomain(c))
            avisarBloqueio(decisao, dominio)
            return
        }

        pool.execute {
            val resposta = encaminhar(c)
            if (resposta != null) escrever(saida, AnalisadorPacoteDns.envolver(c, resposta))
        }
    }

    private fun escrever(saida: FileOutputStream, pacote: ByteArray) {
        try {
            synchronized(bloqueioEscrita) { saida.write(pacote) }
        } catch (_: Exception) {
        }
    }

    private fun avisarBloqueio(d: MotorRegras.Decisao, dominio: String) {
        val agora = System.currentTimeMillis()
        if (agora - (ultimoAviso[d.chave] ?: 0L) < 60_000L) return
        ultimoAviso[d.chave] = agora

        val intent = Intent(this, EcraBloqueioActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EcraBloqueioActivity.EXTRA_CHAVE, d.chave)
            putExtra(EcraBloqueioActivity.EXTRA_ROTULO, d.rotulo)
            putExtra(EcraBloqueioActivity.EXTRA_TIPO, TipoAlvo.URL.name)
            putExtra(EcraBloqueioActivity.EXTRA_DETALHE, d.detalhe)
        }
        val pi = PendingIntent.getActivity(
            this, d.chave.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Notificacoes.mostrar(
            this, 4000 + (d.chave.hashCode() and 0xFF), Notificacoes.CANAL_AVISOS,
            "Site bloqueado", "${d.rotulo}: toca para pedir liberação aos teus pais.", pi
        )

        escopo.launch {
            val cache = CacheLocal(applicationContext)
            val fid = cache.lerFamiliaId() ?: return@launch
            val did = cache.lerDispositivoId() ?: return@launch
            try {
                Ambiente.repo.registarBloqueio(fid, did, RegistoBloqueio(tipo = TipoAlvo.URL, alvo = d.chave, rotulo = "${d.rotulo} ($dominio)"))
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Reencaminhamento ----------

    private fun atualizarDnsReais() {
        val agora = System.currentTimeMillis()
        if (dnsReais.isNotEmpty() && agora - dnsReaisAtualizadoEm < 30_000L) return
        dnsReaisAtualizadoEm = agora
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lista = ArrayList<String>()
            for (rede in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(rede) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                val lp = cm.getLinkProperties(rede) ?: continue
                lp.dnsServers.filterIsInstance<Inet4Address>().forEach { a ->
                    val ip = a.hostAddress ?: return@forEach
                    if (ip != IP_DNS_VIRTUAL) lista.add(ip)
                }
            }
            dnsReais = lista.distinct()
        } catch (_: Exception) {
        }
    }

    private fun encaminhar(c: AnalisadorPacoteDns.ConsultaDns): ByteArray? {
        atualizarDnsReais()
        val destino = c.ipDestino()
        val candidatos = ArrayList<String>()
        if (destino != IP_DNS_VIRTUAL) candidatos.add(destino)
        candidatos.addAll(dnsReais)
        candidatos.add("1.1.1.1")
        candidatos.add("8.8.8.8")
        val consulta = c.payloadDns()
        for (servidor in candidatos.distinct().take(3)) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)
                socket.soTimeout = 3_000
                socket.connect(InetAddress.getByName(servidor), 53)
                socket.send(DatagramPacket(consulta, consulta.size))
                val rb = ByteArray(4096)
                val p = DatagramPacket(rb, rb.size)
                socket.receive(p)
                return rb.copyOf(p.length)
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
        return null
    }

    // ---------- Ciclo de vida ----------

    private fun parar() {
        ativo = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopSelf()
    }

    override fun onRevoke() {
        ativo = false
        AlertasFilho.enviar(
            this, TipoAlerta.VPN_DESATIVADA,
            "O filtro de sites foi desligado no telemóvel da criança.", 60_000L
        )
        super.onRevoke()
    }

    override fun onDestroy() {
        ativo = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        escopo.cancel()
        pool.shutdownNow()
        super.onDestroy()
    }
}
