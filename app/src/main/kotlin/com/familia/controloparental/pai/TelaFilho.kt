package com.familia.controloparental.pai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

/** Dados de um filho carregados uma só vez e partilhados pelos separadores. */
class DadosFilho(
    val dispositivo: Dispositivo,
    val apps: List<AppInstalada>,
    val regras: List<RegraUrl>,
    val perfis: List<PerfilHorario>,
    val relatorios: List<RelatorioDiario>,
    val bloqueios: List<RegistoBloqueio>
)

private val SEPARADORES = listOf("Resumo", "Apps", "Sites", "Horários", "Relatórios")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFilho(
    c: ContextoPai,
    did: String,
    aoVoltar: () -> Unit,
    aoEditar: () -> Unit,
    aoAbrirMapa: () -> Unit,
    aoAbrirConversa: () -> Unit = {},
    aoEditarHorario: (perfilId: String, alvo: String) -> Unit,
    aoRemovido: () -> Unit
) {
    val dispositivo = c.dispositivo(did)
    val agora by rememberAgora()

    val apps by remember(did) { c.repo.observarApps(c.fid, did) }.collectAsState(initial = emptyList())
    val regras by remember(did) { c.repo.observarRegrasUrl(c.fid, did) }.collectAsState(initial = emptyList())
    val perfis by remember(did) { c.repo.observarPerfisHorario(c.fid, did) }.collectAsState(initial = emptyList())
    val relatorios by remember(did) { c.repo.observarRelatorios(c.fid, did, 7) }.collectAsState(initial = emptyList())
    val bloqueios by remember(did) { c.repo.observarBloqueios(c.fid, did, 100) }.collectAsState(initial = emptyList())

    var separador by remember { mutableIntStateOf(0) }
    var menuAberto by remember { mutableStateOf(false) }
    var pedirRemocao by remember { mutableStateOf(false) }

    if (dispositivo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Este dispositivo já não existe.")
                TextButton(onClick = aoVoltar) { Text("Voltar") }
            }
        }
        return
    }

    val dados = DadosFilho(dispositivo, apps, regras, perfis, relatorios, bloqueios)
    val online = dispositivo.estaOnline(agora)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dispositivo.nomeExibicao) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    IconButton(onClick = { menuAberto = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais") }
                    DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar perfil") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuAberto = false; aoEditar() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remover dispositivo") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuAberto = false; pedirRemocao = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val ctxF = LocalContext.current
            val escopoF = rememberCoroutineScope()
            Column(Modifier.fundoDeMarca().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCrianca(dispositivo.crianca.fotoBase64, dispositivo.nomeExibicao, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dispositivo.nomeExibicao, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (online) VerdeClaro else Color.White.copy(alpha = 0.55f))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    !dispositivo.emparelhado -> "A aguardar emparelhamento"
                                    online -> "Online agora"
                                    else -> "Offline · ${tempoDecorrido(dispositivo.ultimoSinal, agora)}"
                                },
                                color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        val extra = buildList {
                            if (dispositivo.bateria >= 0) add("🔋 ${dispositivo.bateria}%" + if (dispositivo.aCarregar) " ⚡" else "")
                            if (dispositivo.rede.isNotBlank() && online) add(dispositivo.rede)
                            if (dispositivo.nome.isNotBlank()) add(dispositivo.nome)
                        }.joinToString("  ·  ")
                        if (extra.isNotBlank()) Text(extra, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AcaoRapida(Icons.Filled.Chat, "Conversar", Modifier.weight(1f), aoClicar = aoAbrirConversa)
                    AcaoRapida(Icons.Filled.Map, "Mapa", Modifier.weight(1f), aoClicar = aoAbrirMapa)
                    if (dispositivo.crianca.telefone.isNotBlank()) {
                        AcaoRapida(Icons.Filled.Call, "Ligar", Modifier.weight(1f)) { ligarPara(ctxF, dispositivo.crianca.telefone) }
                    }
                    AcaoRapida(
                        if (dispositivo.ecraBloqueadoAgora) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        if (dispositivo.ecraBloqueadoAgora) "Libertar" else "Bloquear",
                        Modifier.weight(1f), realce = !dispositivo.ecraBloqueadoAgora
                    ) {
                        escopoF.launch {
                            try { c.repo.definirControlosEcra(c.fid, dispositivo.id, bloqueioTotal = !dispositivo.ecraBloqueadoAgora) } catch (_: Exception) {}
                        }
                    }
                }
            }
            ScrollableTabRow(selectedTabIndex = separador, edgePadding = 8.dp) {
                SEPARADORES.forEachIndexed { i, nome ->
                    Tab(selected = separador == i, onClick = { separador = i }, text = { Text(nome) })
                }
            }
            AnimatedContent(
                targetState = separador,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "separador"
            ) { i ->
                when (i) {
                    0 -> SeparadorResumo(c, dados, agora, aoAbrirMapa, aoAbrirConversa)
                    1 -> SeparadorApps(c, dados, aoEditarHorario)
                    2 -> SeparadorSites(c, dados, aoEditarHorario)
                    3 -> SeparadorHorarios(c, dados, aoEditarHorario)
                    else -> SeparadorRelatorios(dados)
                }
            }
        }
    }

    if (pedirRemocao) {
        DialogoPin(
            titulo = "Remover ${dispositivo.nomeExibicao}?",
            texto = "O telemóvel deixa de ser controlado e os dados deste filho são apagados. Confirma com o PIN de administrador.",
            c = c,
            aoCancelar = { pedirRemocao = false },
            aoConfirmar = {
                try {
                    c.repo.removerDispositivo(c.fid, did)
                    pedirRemocao = false
                    aoRemovido()
                    null
                } catch (e: Exception) {
                    traduzirErro(e)
                }
            }
        )
    }
}

/** Pede o PIN de administrador e executa [aoConfirmar] (devolve uma mensagem de erro ou null). */
@Composable
fun DialogoPin(
    titulo: String,
    texto: String,
    c: ContextoPai,
    aoCancelar: () -> Unit,
    aoConfirmar: suspend () -> String?
) {
    var pin by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!aCarregar) aoCancelar() },
        title = { Text(titulo) },
        text = {
            Column {
                Text(texto)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); erro = null },
                    label = { Text("PIN de administrador") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                MensagemErro(erro)
            }
        },
        confirmButton = {
            Button(enabled = !aCarregar, onClick = {
                aCarregar = true
                escopo.launch {
                    try {
                        if (!c.repo.verificarPinAdmin(c.fid, pin)) {
                            erro = "PIN incorreto."
                        } else {
                            erro = aoConfirmar()
                        }
                    } catch (e: Exception) {
                        erro = traduzirErro(e)
                    }
                    aCarregar = false
                }
            }) { Text(if (aCarregar) "Aguarda..." else "Confirmar") }
        },
        dismissButton = { TextButton(enabled = !aCarregar, onClick = aoCancelar) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------ Resumo

@Composable
private fun LinhaProtecao(texto: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
            tint = if (ok) VerdeEsmeralda else CorErro, modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(texto)
    }
}

@Composable
private fun SeparadorResumo(c: ContextoPai, d: DadosFilho, agora: Long, aoAbrirMapa: () -> Unit, aoAbrirConversa: () -> Unit) {
    val ctx = LocalContext.current
    val disp = d.dispositivo
    val hoje = d.relatorios.firstOrNull { it.data == hojeIso() }
    val bloqueiosHoje = d.bloqueios.count { it.em >= inicioDoDia() }
    val loc = disp.localizacaoAtual

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (disp.emparelhado && !disp.protecao.completa) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CorAviso.copy(alpha = 0.16f)),
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = CorAviso)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Proteção incompleta", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Falta ativar no telemóvel da criança: ${disp.protecao.pendentes.joinToString(", ")}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BlocoMetrica(Icons.Filled.Schedule, formatarDuracao(hoje?.totalMs ?: 0L), "Ecrã hoje", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            BlocoMetrica(Icons.Filled.Block, "$bloqueiosHoje", "Bloqueios hoje", CorAviso, Modifier.weight(1f))
            BlocoMetrica(
                if (disp.aCarregar) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                if (disp.bateria >= 0) "${disp.bateria}%" else "—", "Bateria",
                if (disp.bateria in 0..15 && !disp.aCarregar) CorErro else VerdeEsmeralda, Modifier.weight(1f)
            )
        }

        CartaoSecao("Localização", icone = Icons.Filled.LocationOn) {
            if (loc == null) {
                Text("Ainda sem posição. Assim que a criança ativar a localização, aparece aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Última posição ${tempoDecorrido(loc.timestamp, agora)} (precisão ≈ ${loc.precisaoMetros.toInt()} m)")
                Spacer(Modifier.height(10.dp))
                Button(onClick = aoAbrirMapa, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Map, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Seguir em tempo real")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = { abrirNoGoogleMaps(ctx, loc.latitude, loc.longitude, disp.nomeExibicao) }, modifier = Modifier.weight(1f)) {
                        Text("Google Maps")
                    }
                    OutlinedButton(onClick = { navegarAteCrianca(ctx, loc.latitude, loc.longitude) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Directions, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ir até lá")
                    }
                }
            }
        }

        val pr = disp.protecao
        val feitasP = listOf(pr.acessibilidadeAtiva, pr.adminAtivo, pr.vpnAtiva, pr.localizacaoSempre, pr.usoAppsPermitido).count { it }
        CartaoSecao("Proteções · $feitasP/5 ativas", icone = Icons.Filled.Security) {
            LinearProgressIndicator(
                progress = { feitasP / 5f }, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).height(6.dp),
                color = if (feitasP == 5) VerdeEsmeralda else CorAviso, trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            LinhaProtecao("Bloqueio de apps (acessibilidade)", disp.protecao.acessibilidadeAtiva)
            LinhaProtecao("Proteção contra desinstalação", disp.protecao.adminAtivo)
            LinhaProtecao("Filtro de sites", disp.protecao.vpnAtiva)
            LinhaProtecao("Localização sempre ligada", disp.protecao.localizacaoSempre)
            LinhaProtecao("Tempo de ecrã", disp.protecao.usoAppsPermitido)
        }

        CartaoSecao("Controlos do ecrã", icone = Icons.Filled.Tune) {
            val escopo = rememberCoroutineScope()
            var erroCtl by remember { mutableStateOf<String?>(null) }
            fun alterar(pin: Boolean? = null, bloqueio: Boolean? = null, total: Boolean? = null) {
                escopo.launch {
                    try {
                        c.repo.definirControlosEcra(c.fid, disp.id, pin, bloqueio, total)
                        erroCtl = null
                    } catch (e: Exception) {
                        erroCtl = traduzirErro(e)
                    }
                }
            }
            LinhaInterruptor(
                Icons.Filled.Lock,
                if (disp.ecraBloqueadoAgora) "Ecrã bloqueado agora" else "Bloquear o ecrã agora",
                "Cobre já o ecrã inteiro da criança até desligares. Não pede PIN.",
                disp.ecraBloqueadoAgora, { alterar(total = it) }, CorErro
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            LinhaInterruptor(
                Icons.Filled.Block, "Aviso de bloqueio de apps e sites",
                "Mostra o aviso e o pedido de liberação. Desligado, a criança usa tudo livremente.",
                disp.bloqueioEcraAtivo, { alterar(bloqueio = it) }, CorAviso
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            LinhaInterruptor(
                Icons.Filled.Pin, "PIN de administrador",
                "Pedido só ao desinstalar ou desativar a proteção. Desligado, nunca pede o PIN.",
                disp.pinProtecaoAtiva, { alterar(pin = it) }
            )
            MensagemErro(erroCtl)
        }

        CartaoSecao("Contactar ${disp.nomeExibicao}", icone = Icons.Filled.Chat) {
            Button(onClick = aoAbrirConversa, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(10.dp)); Text("Conversar na app (mensagens e chamadas)")
            }
            if (disp.crianca.telefone.isNotBlank() || disp.crianca.email.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    if (disp.crianca.telefone.isNotBlank()) {
                        AcaoContacto(Icons.Filled.Call, "Ligar", Modifier.weight(1f)) { ligarPara(ctx, disp.crianca.telefone) }
                        AcaoContacto(Icons.Filled.Sms, "SMS", Modifier.weight(1f)) { enviarSms(ctx, disp.crianca.telefone) }
                    }
                    if (disp.crianca.email.isNotBlank()) {
                        AcaoContacto(Icons.Filled.Email, "E-mail", Modifier.weight(1f)) { enviarEmail(ctx, disp.crianca.email) }
                    }
                }
                if (disp.crianca.telefone.isNotBlank()) {
                    Text(disp.crianca.telefone, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AcaoContacto(icone: androidx.compose.ui.graphics.vector.ImageVector, rotulo: String, modifier: Modifier, aoClicar: () -> Unit) {
    FilledTonalButton(
        onClick = aoClicar,
        modifier = modifier.heightIn(min = 68.dp),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icone, null, modifier = Modifier.size(24.dp))
            Text(rotulo, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun inicioDoDia(): Long {
    val z = java.time.ZoneId.systemDefault()
    return java.time.LocalDate.now().atStartOfDay(z).toInstant().toEpochMilli()
}

@Suppress("unused")
private val _c = Color.Unspecified
