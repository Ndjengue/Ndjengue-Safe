package com.familia.controloparental.pai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCrianca(dispositivo.crianca.fotoBase64, dispositivo.nomeExibicao, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(dispositivo.nomeExibicao, style = MaterialTheme.typography.titleLarge)
                    when {
                        !dispositivo.emparelhado -> EtiquetaEstado("A aguardar emparelhamento", CorAviso)
                        online -> EtiquetaEstado("Online agora", VerdeEsmeralda)
                        else -> EtiquetaEstado("Offline · ${tempoDecorrido(dispositivo.ultimoSinal, agora)}", TextoSecundario)
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

        CartaoSecao("Estado do telemóvel") {
            Text(if (disp.estaOnline(agora)) "Online agora" else "Offline · visto ${tempoDecorrido(disp.ultimoSinal, agora)}")
            if (disp.nome.isNotBlank()) Text("Aparelho: ${disp.nome}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (disp.bateria >= 0) {
                Text(
                    "Bateria: ${disp.bateria}%" + if (disp.aCarregar) " (a carregar)" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (disp.rede.isNotBlank()) Text("Rede: ${disp.rede}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        CartaoSecao("Localização") {
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

        CartaoSecao("Proteções") {
            LinhaProtecao("Bloqueio de apps (acessibilidade)", disp.protecao.acessibilidadeAtiva)
            LinhaProtecao("Proteção contra desinstalação", disp.protecao.adminAtivo)
            LinhaProtecao("Filtro de sites", disp.protecao.vpnAtiva)
            LinhaProtecao("Localização sempre ligada", disp.protecao.localizacaoSempre)
            LinhaProtecao("Tempo de ecrã", disp.protecao.usoAppsPermitido)
        }

        CartaoSecao("Controlos do ecrã (ligar / desligar)") {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (disp.ecraBloqueadoAgora) "Ecrã BLOQUEADO agora" else "Bloquear o ecrã agora", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Cobre já o ecrã inteiro da criança até desligares aqui. Não pede PIN.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = disp.ecraBloqueadoAgora, onCheckedChange = { alterar(total = it) })
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ecrã de bloqueio de apps e sites", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Mostra o aviso e o pedido de liberação quando algo está bloqueado. Desligado, a criança usa tudo livremente.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = disp.bloqueioEcraAtivo, onCheckedChange = { alterar(bloqueio = it) })
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PIN de administrador", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pede o PIN só ao tentar desinstalar ou desativar a proteção. Desligado, o telemóvel nunca pede o PIN.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = disp.pinProtecaoAtiva, onCheckedChange = { alterar(pin = it) })
            }
            MensagemErro(erroCtl)
        }

        CartaoSecao("Hoje") {
            Text("Tempo de ecrã: ${formatarDuracao(hoje?.totalMs ?: 0L)}")
            Text("Bloqueios: $bloqueiosHoje", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        CartaoSecao("Contactar ${disp.nomeExibicao}") {
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
