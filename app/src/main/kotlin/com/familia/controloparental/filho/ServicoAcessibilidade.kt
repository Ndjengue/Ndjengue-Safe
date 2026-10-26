package com.familia.controloparental.filho

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import kotlinx.coroutines.*

/**
 * Faz duas coisas:
 *  1. Deteta a app em primeiro plano e mostra o ecrã de bloqueio quando as regras (cache local,
 *     por isso também offline) mandam bloquear.
 *  2. Protege contra desinstalação: quando alguém abre o ecrã de desinstalar / desativar o
 *     administrador / parar a app / desligar a acessibilidade, cobre o ecrã com um pedido de PIN.
 */
class ServicoAcessibilidade : AccessibilityService() {

    companion object {
        @Volatile var ativo: Boolean = false

        /** Janelas do sistema onde se pode desinstalar ou desativar a proteção. */
        private val PACOTES_SENSIVEIS = listOf(
            "settings", "packageinstaller", "permissioncontroller", "launcher", "systemui",
            "vending", "securitycenter", "safecenter", "appmanager", "packageinstallerui", "home"
        )

        /** Palavras que indicam uma ação perigosa quando aparecem junto do nome da app. */
        private val ACOES_PERIGOSAS = listOf(
            "desinstal", "uninstall", "forçar", "force stop", "forçar paragem", "limpar dados", "clear data",
            "clear storage", "limpar armazenamento", "desativar", "desativ", "deactivate", "disable",
            "administrador", "administrator", "remover", "remove", "utilizar o serviço", "use service",
            "usar serviço", "turn off"
        )

        private val PACOTES_IGNORADOS = setOf("com.android.systemui", "android")
    }

    private val escopo = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val principal = Handler(Looper.getMainLooper())
    private lateinit var cache: CacheLocal
    private var sobreposicao: SobreposicaoPin? = null
    private var ecraBloqueio: SobreposicaoBloqueio? = null
    private var rotuloApp: String = "Ndjengue Safe"

    @Volatile private var apps: List<AppInstalada> = emptyList()
    @Volatile private var regrasUrl: List<RegraUrl> = emptyList()
    @Volatile private var perfis: List<PerfilHorario> = emptyList()
    @Volatile private var liberacoes: Map<String, Long> = emptyMap()
    @Volatile private var armada: Boolean = false
    @Volatile private var janelaAdmin: Boolean = false
    @Volatile private var pinAtivo: Boolean = true
    @Volatile private var bloqueioAtivo: Boolean = true

    @Volatile private var pacoteAtual: String? = null
    private val ultimoRegistoBloqueio = HashMap<String, Long>()
    private var ultimaVerificacaoConteudo = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ativo = true
        cache = CacheLocal(applicationContext)
        rotuloApp = try { applicationInfo.loadLabel(packageManager).toString() } catch (e: Exception) { rotuloApp }

        sobreposicao = SobreposicaoPin(
            servico = this,
            cache = cache,
            aoValidar = {
                janelaAdmin = true
                AlertasFilho.enviar(
                    this, TipoAlerta.SENHA_ADMIN_USADA,
                    "O PIN de administrador foi usado. A proteção ficou suspensa durante 5 minutos."
                )
            },
            aoCancelar = { performGlobalAction(GLOBAL_ACTION_HOME) },
            aoErrar = {
                AlertasFilho.enviar(this, TipoAlerta.SENHA_ADMIN_ERRADA, "PIN de administrador incorreto.", 20_000L)
            }
        )

        ecraBloqueio = SobreposicaoBloqueio(
            servico = this,
            aoPedir = { chave, rotulo, minutos -> enviarPedido(chave, rotulo, minutos) },
            aoVoltar = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )

        escopo.launch { cache.observarApps().collect { apps = it } }
        escopo.launch { cache.observarRegrasUrl().collect { regrasUrl = it } }
        escopo.launch {
            while (isActive) {
                try {
                    perfis = cache.lerPerfisHorario()
                    liberacoes = cache.lerLiberacoes()
                    armada = cache.lerBool(Ambiente.CHAVE_ARMADA)
                    janelaAdmin = cache.janelaAdminAtiva()
                    pinAtivo = cache.lerBool(Ambiente.CHAVE_PIN_ATIVO, true)
                    bloqueioAtivo = cache.lerBool(Ambiente.CHAVE_BLOQUEIO_ATIVO, true)
                    if (!pinAtivo) principal.post { sobreposicao?.ocultar() }
                } catch (_: Exception) {
                }
                delay(3_000)
            }
        }
        // Reavalia periodicamente (e não depende só dos eventos, que alguns Xiaomi/Redmi perdem):
        // lê a janela ativa, mostra/oculta o bloqueio e apanha o fim de horários.
        escopo.launch {
            while (isActive) {
                delay(1_500)
                val ativa = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
                if (ativa != null && ativa != packageName && ativa !in PACOTES_IGNORADOS &&
                    !ativa.contains("inputmethod") && !ativa.contains("keyboard")
                ) pacoteAtual = ativa
                val p = pacoteAtual ?: continue
                principal.post { reavaliar(p) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pacote = event.packageName?.toString() ?: return
        if (pacote == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pacote !in PACOTES_IGNORADOS && !pacote.contains("inputmethod") && !pacote.contains("keyboard")) {
                    pacoteAtual = pacote
                    avaliarApp(pacote)
                }
                verificarProtecao(pacote, forcar = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> verificarProtecao(pacote, forcar = false)
            else -> Unit
        }
    }

    // ---------- Bloqueio de apps ----------

    private fun avaliarApp(pacote: String) {
        principal.post { reavaliar(pacote) }
    }

    /** Corre na thread principal. Mostra o bloqueio se a app deve estar bloqueada, senão esconde-o. */
    private fun reavaliar(pacote: String) {
        val decisao = if (janelaAdmin || !bloqueioAtivo) null else MotorRegras.decidirApp(pacote, apps, regrasUrl, perfis, liberacoes)
        if (decisao != null) {
            mostrarBloqueio(decisao)
        } else if (ecraBloqueio?.visivel == true) {
            ecraBloqueio?.ocultar()
        }
    }

    private fun enviarPedido(chave: String, rotulo: String, minutos: Int) {
        escopo.launch {
            val fid = cache.lerFamiliaId() ?: return@launch
            val did = cache.lerDispositivoId() ?: return@launch
            val nome = cache.lerTexto(Ambiente.CHAVE_NOME_CRIANCA) ?: ""
            withTimeoutOrNull(4_000) {
                try {
                    Ambiente.repo.criarPedido(
                        fid,
                        PedidoLiberacao(
                            dispositivoId = did, criancaNome = nome, alvo = chave, rotulo = rotulo,
                            tipoAlvo = TipoAlvo.APP, duracaoMinutos = minutos
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun mostrarBloqueio(d: MotorRegras.Decisao) {
        ecraBloqueio?.mostrar(
            d.chave, d.rotulo,
            if (d.tipo == TipoAlvo.URL) "Site bloqueado" else "Bloqueado pelos teus pais",
            d.detalhe
        )
        val agora = System.currentTimeMillis()
        if (agora - (ultimoRegistoBloqueio[d.chave] ?: 0L) > 60_000L) {
            ultimoRegistoBloqueio[d.chave] = agora
            escopo.launch {
                val fid = cache.lerFamiliaId() ?: return@launch
                val did = cache.lerDispositivoId() ?: return@launch
                try {
                    Ambiente.repo.registarBloqueio(fid, did, RegistoBloqueio(tipo = d.tipo, alvo = d.chave, rotulo = d.rotulo))
                } catch (_: Exception) {
                }
            }
        }
    }

    // ---------- Proteção contra desinstalação ----------

    private fun verificarProtecao(pacote: String, forcar: Boolean) {
        if (!armada || janelaAdmin || !pinAtivo) return
        if (sobreposicao?.visivel == true) return
        if (PACOTES_SENSIVEIS.none { pacote.contains(it, ignoreCase = true) }) return

        val agora = System.currentTimeMillis()
        if (!forcar && agora - ultimaVerificacaoConteudo < 600L) return
        ultimaVerificacaoConteudo = agora

        val raiz = try { rootInActiveWindow } catch (e: Exception) { null } ?: return
        if (raiz.packageName?.toString() != pacote) return

        val textos = coletarTextos(raiz)
        val temRotulo = textos.any { it.contains(rotuloApp, ignoreCase = true) }
        if (!temRotulo) return
        val temAcao = textos.any { t -> ACOES_PERIGOSAS.any { t.contains(it, ignoreCase = true) } }
        if (!temAcao) return

        AlertasFilho.enviar(
            this, TipoAlerta.DESINSTALACAO_TENTADA,
            "Tentativa de desinstalar ou alterar a proteção da app.", 60_000L
        )
        principal.post {
            sobreposicao?.mostrar(
                "Proteção dos pais",
                "Para continuar, introduz o PIN de administrador definido pelos teus pais."
            )
        }
    }

    private fun coletarTextos(raiz: AccessibilityNodeInfo): List<String> {
        val textos = ArrayList<String>()
        val pilha = ArrayDeque<AccessibilityNodeInfo>()
        pilha.addLast(raiz)
        var visitados = 0
        while (pilha.isNotEmpty() && visitados < 500) {
            val no = pilha.removeLast()
            visitados++
            no.text?.toString()?.let { if (it.isNotBlank()) textos.add(it) }
            no.contentDescription?.toString()?.let { if (it.isNotBlank()) textos.add(it) }
            for (i in 0 until no.childCount) {
                val filho = try { no.getChild(i) } catch (e: Exception) { null }
                if (filho != null) pilha.addLast(filho)
            }
        }
        return textos
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        ativo = false
        AlertasFilho.enviar(
            this, TipoAlerta.ACESSIBILIDADE_DESATIVADA,
            "O serviço de acessibilidade foi desativado. O bloqueio de apps deixou de funcionar.", 60_000L
        )
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ativo = false
        sobreposicao?.destruir()
        ecraBloqueio?.ocultar()
        escopo.cancel()
        super.onDestroy()
    }
}
