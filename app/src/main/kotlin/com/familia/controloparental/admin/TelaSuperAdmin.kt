package com.familia.controloparental.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

private fun rotuloEstado(e: String) = when (e) {
    "CONCLUIDA" -> "Concluída"
    "CONCLUIDA_COM_ERROS" -> "Concluída com erros"
    "FALHADA" -> "Falhada"
    "EM_CURSO" -> "Em curso"
    else -> e
}

private fun corEstado(e: String) = when (e) {
    "CONCLUIDA" -> VerdeEsmeralda
    "EM_CURSO" -> CorAviso
    else -> CorErro
}

/** Painel do Super Administrador: só abre se existir admins/{uid} (criado à mão na consola). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSuperAdmin(aoVoltar: () -> Unit) {
    val repo = remember { RetencaoRepositorio() }
    var autorizado by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { autorizado = repo.ehSuperAdmin() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel do Super Administrador") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                }
            )
        }
    ) { padding ->
        when (autorizado) {
            null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            false -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Sem permissões. Esta área é exclusiva do Super Administrador.",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            true -> ConteudoAdmin(repo, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ConteudoAdmin(repo: RetencaoRepositorio, modifier: Modifier) {
    var aba by remember { mutableIntStateOf(0) }
    val politica by remember { repo.observarPolitica() }.collectAsState(initial = PoliticaRetencao())
    val limpezas by remember { repo.observarLimpezas() }.collectAsState(initial = emptyList())

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = aba) {
            listOf("Painel", "Retenção", "Histórico").forEachIndexed { i, t ->
                Tab(selected = aba == i, onClick = { aba = i }, text = { Text(t) })
            }
        }
        when (aba) {
            0 -> AbaPainel(repo, politica, limpezas)
            1 -> AbaRetencao(repo, politica)
            else -> AbaHistorico(repo, limpezas)
        }
    }
}

@Composable
private fun AbaPainel(repo: RetencaoRepositorio, politica: PoliticaRetencao, limpezas: List<ExecucaoLimpeza>) {
    var resumo by remember { mutableStateOf<ResumoPainel?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(true) }
    var recarregar by remember { mutableIntStateOf(0) }
    var limpezaAutoFeita by remember { mutableStateOf(false) }

    LaunchedEffect(recarregar) {
        aCarregar = true
        erro = null
        try {
            var r = repo.resumo()
            resumo = r
            // Limpeza oportunista (sem Cloud Functions): se há expirados e a última execução tem mais de 1 h, limpa já.
            val ultima = limpezas.firstOrNull()
            val recente = ultima != null && System.currentTimeMillis() - ultima.inicio < 3_600_000L
            if (r.expiradosPendentes > 0 && !recente && !limpezaAutoFeita) {
                limpezaAutoFeita = true
                repo.executarLimpeza()
                r = repo.resumo()
                resumo = r
            }
        } catch (e: Exception) {
            erro = traduzirErro(e)
        }
        aCarregar = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dados em tempo real", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { recarregar++ }, enabled = !aCarregar) { Icon(Icons.Filled.Refresh, "Atualizar") }
        }
        if (aCarregar) LinearProgressIndicator(Modifier.fillMaxWidth())
        MensagemErro(erro)

        val r = resumo
        if (r != null) {
            CartaoSecao("Utilizadores") {
                LinhaDado("Encarregados registados", r.encarregados.toString())
                LinhaDado("Crianças registadas", r.criancas.toString())
                LinhaDado("Contas ativas (últimos 7 dias)", r.contasAtivas.toString())
            }
            CartaoSecao("Conteúdo armazenado (não expirado)") {
                LinhaDado("Mensagens", r.mensagens.toString())
                LinhaDado("Publicações", r.publicacoes.toString())
                LinhaDado("Comentários", r.comentarios.toString())
                LinhaDado("Reações", r.reacoes.toString())
                LinhaDado("Metadados de chamadas", r.chamadas.toString())
                LinhaDado("Volume de anexos", formatarTamanho(r.bytesAnexos))
                Text(
                    "Anexos ficam no próprio documento (Base64), por isso são apagados com ele e nunca ficam órfãos. O volume inclui expirados ainda por eliminar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CartaoSecao("Eliminação") {
                LinhaDado("Expirados à espera de eliminação", r.expiradosPendentes.toString())
                if (r.expiradosPendentes > 0) {
                    Text(
                        "Já estão inacessíveis nas leituras diretas; a limpeza corre ao abrir este painel (no máximo 1×/hora) e o TTL remove o resto.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        CartaoSecao("Política ativa") {
            LinhaDado("Retenção", if (politica.definida) "${politica.dias} dia(s)" else "${politica.dias} dia(s) (por defeito, ainda não definida)")
            val u = limpezas.firstOrNull()
            if (u == null) {
                Text("Ainda não houve nenhuma limpeza registada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LinhaDado("Última limpeza", formatarDataHora(u.inicio))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado", Modifier.weight(1f))
                    EtiquetaEstado(rotuloEstado(u.estado), corEstado(u.estado))
                }
                LinhaDado("Registos eliminados", (u.mensagens + u.publicacoes + u.comentarios + u.reacoes + u.chamadas).toString())
                LinhaDado("Falhas", u.falhas.toString())
            }
        }
    }
}

@Composable
private fun formatarTamanho(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
}

@Composable
private fun LinhaDado(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(rotulo, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbaRetencao(repo: RetencaoRepositorio, politica: PoliticaRetencao) {
    val escopo = rememberCoroutineScope()
    val rapidas = listOf(1, 2, 7, 30, 60)
    var selecionado by remember(politica.dias) { mutableIntStateOf(politica.dias) }
    var outro by remember(politica.dias) { mutableStateOf(politica.dias !in rapidas) }
    var texto by remember(politica.dias) { mutableStateOf(if (politica.dias !in rapidas) politica.dias.toString() else "") }
    var confirmar by remember { mutableStateOf(false) }
    var a_guardar by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    val diasEscolhidos: Int? = if (outro) texto.toIntOrNull() else selecionado
    val invalido = diasEscolhidos == null || diasEscolhidos !in LimitesRetencao.MIN_DIAS..LimitesRetencao.MAX_DIAS
    val expiraExemplo = if (diasEscolhidos != null && !invalido)
        formatarDataHora(System.currentTimeMillis() + diasEscolhidos * 86_400_000L) else "—"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CartaoSecao("Política de Retenção de Dados") {
            LinhaDado("Período atual", "${politica.dias} dia(s)")
            if (politica.definida) {
                LinhaDado("Última alteração", formatarDataHora(politica.alteradoEm))
                LinhaDado("Alterada por", politica.alteradoPorEmail.ifBlank { "—" })
            }
        }

        CartaoSecao("Novo período") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rapidas.take(3).forEach { d ->
                    FilterChip(selected = !outro && selecionado == d, onClick = { outro = false; selecionado = d },
                        label = { Text(if (d == 1) "1 dia" else "$d dias") })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rapidas.drop(3).forEach { d ->
                    FilterChip(selected = !outro && selecionado == d, onClick = { outro = false; selecionado = d },
                        label = { Text("$d dias") })
                }
                FilterChip(selected = outro, onClick = { outro = true }, label = { Text("Outro") })
            }
            if (outro) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it.filter(Char::isDigit).take(3) },
                    label = { Text("Dias (${LimitesRetencao.MIN_DIAS} a ${LimitesRetencao.MAX_DIAS})") },
                    singleLine = true, isError = invalido,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            LinhaDado("Um conteúdo criado agora expira em", expiraExemplo)
            Text(
                "Abrange: mensagens (texto e áudio), publicações, comentários e reações. As chamadas nunca são gravadas; " +
                    "só os metadados ficam, ${LimitesRetencao.CHAMADAS_DIAS} dias.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ A eliminação é automática e irreversível. Reduzir o período pode apagar conteúdos que já existem.",
                color = CorAviso, style = MaterialTheme.typography.bodyMedium
            )
            MensagemErro(erro)
            if (estado != null) Text(estado!!, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !invalido && !a_guardar && diasEscolhidos != politica.dias,
                onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()
            ) { Text(if (a_guardar) "A aplicar…" else "Guardar nova política") }
        }
    }

    if (confirmar && diasEscolhidos != null) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Confirmar alteração") },
            text = {
                Text(
                    "Passar de ${politica.dias} para $diasEscolhidos dia(s). As datas de expiração de tudo o que já " +
                        "existe serão recalculadas a partir da data de criação e o que ficar expirado será eliminado. " +
                        "Não é possível desfazer."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmar = false
                    a_guardar = true
                    erro = null
                    estado = null
                    escopo.launch {
                        try {
                            repo.guardarPolitica(diasEscolhidos, politica)
                            val n = repo.recalcularExpiracoes(diasEscolhidos) { estado = it }
                            estado = "Política guardada. $n registo(s) recalculado(s). Vai a Histórico para executar a limpeza."
                        } catch (e: Exception) {
                            erro = traduzirErro(e)
                        }
                        a_guardar = false
                    }
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AbaHistorico(repo: RetencaoRepositorio, limpezas: List<ExecucaoLimpeza>) {
    val escopo = rememberCoroutineScope()
    val auditoria by remember { repo.observarAuditoria() }.collectAsState(initial = emptyList())
    var aExecutar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CartaoSecao("Limpeza") {
            Text(
                "O Firestore remove sozinho (TTL) o que expirou. Aqui podes forçar já a eliminação em cascata " +
                    "(publicações → comentários e reações) e ficar com o registo da execução.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MensagemErro(erro)
            Spacer(Modifier.height(10.dp))
            Button(enabled = !aExecutar, modifier = Modifier.fillMaxWidth(), onClick = {
                aExecutar = true
                erro = null
                escopo.launch {
                    try { repo.executarLimpeza() } catch (e: Exception) { erro = traduzirErro(e) }
                    aExecutar = false
                }
            }) { Text(if (aExecutar) "A limpar…" else "Executar limpeza agora") }
        }

        CartaoSecao("Execuções") {
            if (limpezas.isEmpty()) Text("Sem execuções registadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            limpezas.forEach { l ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatarDataHora(l.inicio), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        EtiquetaEstado(rotuloEstado(l.estado), corEstado(l.estado))
                    }
                    Text(
                        "Mensagens ${l.mensagens} · Publicações ${l.publicacoes} · Comentários ${l.comentarios} · " +
                            "Reações ${l.reacoes} · Chamadas ${l.chamadas} · Falhas ${l.falhas}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("ID ${l.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }

        CartaoSecao("Auditoria de alterações") {
            if (auditoria.isEmpty()) Text("Sem alterações registadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            auditoria.forEach { a ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${a.valorAnterior} → ${a.valorNovo} dia(s) · ${a.resultado}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${formatarDataHora(a.criadoEm)} · ${a.adminEmail}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
