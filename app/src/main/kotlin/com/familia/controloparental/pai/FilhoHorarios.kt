package com.familia.controloparental.pai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Resumo de um perfil

fun resumirPerfil(p: PerfilHorario): String {
    val ordem = DiaSemana.ordemApresentacao
    fun chave(dia: DiaSemana): String =
        (p.janelasPorDia[dia] ?: emptyList()).sortedBy { MotorRegras.minutos(it.inicio) }.joinToString(", ") { "${it.inicio}–${it.fim}" }

    val partes = ArrayList<String>()
    var i = 0
    while (i < ordem.size) {
        val k = chave(ordem[i])
        var j = i
        while (j + 1 < ordem.size && chave(ordem[j + 1]) == k) j++
        val dias = if (i == j) ordem[i].abreviatura else "${ordem[i].abreviatura}–${ordem[j].abreviatura}"
        partes.add("$dias: ${if (k.isEmpty()) "bloqueado" else k}")
        i = j + 1
    }
    return partes.joinToString("\n")
}

// ------------------------------------------------------------------ Separador "Horários"

@Composable
fun SeparadorHorarios(
    c: ContextoPai,
    d: DadosFilho,
    aoEditarHorario: (perfilId: String, alvo: String) -> Unit
) {
    val did = d.dispositivo.id
    val escopo = rememberCoroutineScope()
    var apagar by remember { mutableStateOf<PerfilHorario?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            CartaoSecao("Como funcionam os horários") {
                Text(
                    "Um horário define as horas em que a criança PODE usar. Fora dessas horas, a app ou o site fica bloqueado " +
                        "automaticamente. Cria um horário (ex.: \"Escola\") e usa-o em quantas apps ou sites quiseres.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { aoEditarHorario("", "") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Criar novo horário")
                }
            }
        }
        if (d.perfis.isEmpty()) {
            item {
                EstadoVazio(Icons.Filled.Schedule, "Ainda não há horários", "Cria o primeiro e depois escolhe-o na lista de apps ou de sites.")
            }
        }
        items(d.perfis.sortedBy { it.nome.lowercase() }, key = { it.id }) { p ->
            val usos = d.apps.count { it.perfilHorarioId == p.id && it.regra == RegraApp.BLOQUEADO_POR_HORARIO } +
                d.regras.count { it.perfilHorarioId == p.id }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nome, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (usos == 0) "Ainda não está a ser usado" else "Usado em $usos item(ns)",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { aoEditarHorario(p.id, "") }) { Icon(Icons.Filled.Edit, "Editar") }
                        IconButton(onClick = { apagar = p }) { Icon(Icons.Filled.Delete, "Apagar") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(resumirPerfil(p), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    apagar?.let { p ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Apagar \"${p.nome}\"?") },
            text = { Text("As apps e sites que usam este horário voltam a ficar livres.") },
            confirmButton = {
                Button(onClick = {
                    apagar = null
                    escopo.launch { try { c.repo.removerPerfilHorario(c.fid, did, p.id) } catch (_: Exception) {} }
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------------ Escolher perfil

@Composable
fun DialogoEscolherPerfil(
    titulo: String,
    perfis: List<PerfilHorario>,
    atualId: String?,
    aoEscolher: (String) -> Unit,
    aoCriarNovo: () -> Unit,
    aoFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Fora das horas do horário escolhido fica bloqueado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                perfis.sortedBy { it.nome.lowercase() }.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = p.id == atualId, role = Role.RadioButton) { aoEscolher(p.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = p.id == atualId, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.nome, style = MaterialTheme.typography.titleMedium)
                            Text(MotorRegras.descreverHoje(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = aoCriarNovo) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Criar novo horário")
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------ Seletor de hora

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoHora(
    titulo: String,
    inicial: String,
    permitirFimDoDia: Boolean,
    aoConfirmar: (String) -> Unit,
    aoFechar: () -> Unit
) {
    val min = MotorRegras.minutos(inicial).coerceAtMost(23 * 60 + 59)
    val estado = rememberTimePickerState(initialHour = min / 60, initialMinute = min % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = estado)
                if (permitirFimDoDia) {
                    TextButton(onClick = { aoConfirmar("24:00") }) { Text("Usar fim do dia (24:00)") }
                }
            }
        },
        confirmButton = { Button(onClick = { aoConfirmar("%02d:%02d".format(estado.hour, estado.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------ Editor

private data class EstadoJanela(val dia: DiaSemana, val indice: Int, val inicio: String?, val fase: Int)

@Composable
private fun BarraDia(janelas: List<JanelaHorario>) {
    val cor = VerdeEsmeralda
    val fundo = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(fundo)) {
        janelas.forEach { j ->
            val a = MotorRegras.minutos(j.inicio) / 1440f
            val b = MotorRegras.minutos(j.fim) / 1440f
            if (b > a) drawRect(cor, topLeft = Offset(size.width * a, 0f), size = Size(size.width * (b - a), size.height))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEditorHorario(
    c: ContextoPai,
    did: String,
    perfilId: String,
    alvo: String,
    aoVoltar: () -> Unit
) {
    val escopo = rememberCoroutineScope()
    val perfis by remember(did) { c.repo.observarPerfisHorario(c.fid, did) }.collectAsState(initial = emptyList())
    val regras by remember(did) { c.repo.observarRegrasUrl(c.fid, did) }.collectAsState(initial = emptyList())

    var nome by remember { mutableStateOf("") }
    var janelas by remember { mutableStateOf<Map<DiaSemana, List<JanelaHorario>>>(emptyMap()) }
    var carregado by remember { mutableStateOf(perfilId.isBlank()) }
    LaunchedEffect(perfis, perfilId) {
        if (!carregado) {
            perfis.firstOrNull { it.id == perfilId }?.let {
                nome = it.nome
                janelas = it.janelasPorDia
                carregado = true
            }
        }
    }

    var estadoJanela by remember { mutableStateOf<EstadoJanela?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var aGuardar by remember { mutableStateOf(false) }

    fun definirDia(dia: DiaSemana, lista: List<JanelaHorario>) {
        janelas = janelas.toMutableMap().also { it[dia] = lista.sortedBy { j -> MotorRegras.minutos(j.inicio) } }
    }

    fun preset(dias: List<DiaSemana>, inicio: String, fim: String) {
        val m = DiaSemana.values().associateWith { emptyList<JanelaHorario>() }.toMutableMap()
        dias.forEach { m[it] = listOf(JanelaHorario(inicio, fim)) }
        janelas = m
    }

    val uteis = listOf(DiaSemana.SEGUNDA, DiaSemana.TERCA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SEXTA)

    fun guardar() {
        erro = null
        if (nome.isBlank()) { erro = "Dá um nome ao horário (por exemplo: Escola)."; return }
        if (janelas.values.none { it.isNotEmpty() }) {
            erro = "Adiciona pelo menos um intervalo em que a criança pode usar. Se queres bloquear sempre, escolhe \"Bloqueada\" em vez de horário."
            return
        }
        aGuardar = true
        escopo.launch {
            try {
                val id = c.repo.guardarPerfilHorario(c.fid, did, PerfilHorario(perfilId, nome.trim(), janelas))
                when {
                    alvo.startsWith("app:") ->
                        c.repo.definirRegraApp(c.fid, did, alvo.removePrefix("app:"), RegraApp.BLOQUEADO_POR_HORARIO, id)
                    alvo.startsWith("rede:") -> {
                        val rede = CatalogoRedes.porId(alvo.removePrefix("rede:"))
                        if (rede != null) {
                            c.repo.guardarRegraUrl(
                                c.fid, did,
                                RegraUrl("rede_${rede.id}", rede.nome, TipoRegraUrl.REDE_SOCIAL, rede.dominios, rede.pacotes, false, id)
                            )
                        }
                    }
                    alvo.startsWith("site:") -> {
                        regras.firstOrNull { it.id == alvo.removePrefix("site:") }?.let {
                            c.repo.guardarRegraUrl(c.fid, did, it.copy(ativo = false, perfilHorarioId = id))
                        }
                    }
                }
                aoVoltar()
            } catch (e: Exception) {
                erro = traduzirErro(e)
            }
            aGuardar = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (perfilId.isBlank()) "Novo horário" else "Editar horário") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    TextButton(enabled = !aGuardar, onClick = { guardar() }) { Text(if (aGuardar) "A guardar..." else "Guardar") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CartaoSecao("") {
                Text("Escolhe as horas em que a criança PODE usar. Fora delas fica bloqueado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it.take(30) },
                    label = { Text("Nome do horário") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Escola", "Estudo", "Noite").forEach { s ->
                        AssistChip(onClick = { nome = s }, label = { Text(s) })
                    }
                }
            }

            CartaoSecao("Modelos rápidos") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { preset(uteis, "07:00", "20:00") }, label = { Text("Seg–Sex 07–20h") })
                    AssistChip(onClick = { preset(DiaSemana.values().toList(), "08:00", "21:00") }, label = { Text("Todos os dias 08–21h") })
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { preset(listOf(DiaSemana.SABADO, DiaSemana.DOMINGO), "09:00", "21:00") }, label = { Text("Fim de semana") })
                    AssistChip(onClick = { janelas = emptyMap() }, label = { Text("Limpar tudo") })
                }
            }

            DiaSemana.ordemApresentacao.forEach { dia ->
                val lista = (janelas[dia] ?: emptyList()).sortedBy { MotorRegras.minutos(it.inicio) }
                var menuCopiar by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dia.nomeCompleto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (lista.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { menuCopiar = true }) { Icon(Icons.Filled.ContentCopy, "Copiar para outros dias") }
                                    DropdownMenu(expanded = menuCopiar, onDismissRequest = { menuCopiar = false }) {
                                        DropdownMenuItem(text = { Text("Copiar para dias úteis") }, onClick = {
                                            menuCopiar = false; uteis.forEach { definirDia(it, lista) }
                                        })
                                        DropdownMenuItem(text = { Text("Copiar para fim de semana") }, onClick = {
                                            menuCopiar = false
                                            definirDia(DiaSemana.SABADO, lista); definirDia(DiaSemana.DOMINGO, lista)
                                        })
                                        DropdownMenuItem(text = { Text("Copiar para todos os dias") }, onClick = {
                                            menuCopiar = false; DiaSemana.values().forEach { definirDia(it, lista) }
                                        })
                                    }
                                }
                            }
                        }
                        BarraDia(lista)
                        Spacer(Modifier.height(8.dp))
                        if (lista.isEmpty()) {
                            Text("Sem horas permitidas (bloqueado o dia todo)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        lista.forEachIndexed { i, j ->
                            InputChip(
                                selected = false,
                                onClick = { estadoJanela = EstadoJanela(dia, i, null, 0) },
                                label = { Text("${j.inicio} – ${j.fim}") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close, "Remover",
                                        modifier = Modifier.size(18.dp).clickable {
                                            definirDia(dia, lista.filterIndexed { k, _ -> k != i })
                                        }
                                    )
                                },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        TextButton(onClick = { estadoJanela = EstadoJanela(dia, -1, null, 0) }) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Adicionar intervalo")
                        }
                    }
                }
            }

            MensagemErro(erro)
            Button(enabled = !aGuardar, onClick = { guardar() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (aGuardar) "A guardar..." else "Guardar horário")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    estadoJanela?.let { ej ->
        val atual = (janelas[ej.dia] ?: emptyList()).sortedBy { MotorRegras.minutos(it.inicio) }
        val existente = atual.getOrNull(ej.indice)
        if (ej.fase == 0) {
            DialogoHora(
                titulo = "Começa às... (${ej.dia.nomeCompleto})",
                inicial = existente?.inicio ?: "08:00",
                permitirFimDoDia = false,
                aoConfirmar = { h -> estadoJanela = ej.copy(inicio = h, fase = 1) },
                aoFechar = { estadoJanela = null }
            )
        } else {
            DialogoHora(
                titulo = "Termina às...",
                inicial = existente?.fim ?: "20:00",
                permitirFimDoDia = true,
                aoConfirmar = { fim ->
                    val ini = ej.inicio ?: "08:00"
                    if (MotorRegras.minutos(fim) <= MotorRegras.minutos(ini)) {
                        erro = "A hora de fim tem de ser depois da hora de início."
                        estadoJanela = null
                    } else {
                        erro = null
                        val nova = JanelaHorario(ini, fim)
                        val lista = atual.toMutableList()
                        if (ej.indice >= 0 && ej.indice < lista.size) lista[ej.indice] = nova else lista.add(nova)
                        definirDia(ej.dia, lista)
                        estadoJanela = null
                    }
                },
                aoFechar = { estadoJanela = null }
            )
        }
    }
}
