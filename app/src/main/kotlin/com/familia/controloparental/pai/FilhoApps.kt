package com.familia.controloparental.pai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

@Composable
fun SeparadorApps(
    c: ContextoPai,
    d: DadosFilho,
    aoEditarHorario: (perfilId: String, alvo: String) -> Unit
) {
    val did = d.dispositivo.id
    val escopo = rememberCoroutineScope()
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableIntStateOf(0) } // 0 todas, 1 bloqueadas, 2 com horário
    var escolhendo by remember { mutableStateOf<AppInstalada?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    val lista = d.apps
        .filter { busca.isBlank() || it.nome.contains(busca, true) || it.nomePacote.contains(busca, true) }
        .filter {
            when (filtro) {
                1 -> it.regra == RegraApp.BLOQUEADO
                2 -> it.regra == RegraApp.BLOQUEADO_POR_HORARIO
                else -> true
            }
        }
        .sortedBy { it.nome.lowercase() }

    fun aplicar(app: AppInstalada, modo: RegraApp, perfilId: String?) {
        escopo.launch {
            try {
                c.repo.definirRegraApp(c.fid, did, app.nomePacote, modo, perfilId)
                erro = null
            } catch (e: Exception) {
                erro = traduzirErro(e)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = busca, onValueChange = { busca = it },
            placeholder = { Text("Procurar app") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filtro == 0, onClick = { filtro = 0 }, label = { Text("Todas (${d.apps.size})") })
            FilterChip(selected = filtro == 1, onClick = { filtro = 1 }, label = { Text("Bloqueadas (${d.apps.count { it.regra == RegraApp.BLOQUEADO }})") })
            FilterChip(selected = filtro == 2, onClick = { filtro = 2 }, label = { Text("Horário (${d.apps.count { it.regra == RegraApp.BLOQUEADO_POR_HORARIO }})") })
        }
        if (erro != null) Box(Modifier.padding(horizontal = 16.dp)) { MensagemErro(erro) }

        if (d.apps.isEmpty()) {
            EstadoVazio(
                Icons.Filled.Apps, "A lista de apps ainda não chegou",
                "Assim que o telemóvel da criança estiver ligado à internet, as apps aparecem aqui."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lista, key = { it.nomePacote }) { app ->
                    val perfil = d.perfis.firstOrNull { it.id == app.perfilHorarioId }
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconeApp(app.iconeBase64, app.nome)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.nome.ifBlank { app.nomePacote }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.nomePacote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            SeletorModo(app.regra) { modo ->
                                when (modo) {
                                    RegraApp.BLOQUEADO_POR_HORARIO -> {
                                        if (d.perfis.isEmpty()) aoEditarHorario("", "app:${app.nomePacote}")
                                        else escolhendo = app
                                    }
                                    else -> aplicar(app, modo, null)
                                }
                            }
                            if (app.regra == RegraApp.BLOQUEADO_POR_HORARIO) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (perfil != null) "Horário: ${perfil.nome}" else "Sem horário escolhido",
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(onClick = { escolhendo = app }) { Text("Alterar") }
                                }
                                if (perfil != null) {
                                    Text(
                                        MotorRegras.descreverHoje(perfil),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    escolhendo?.let { app ->
        DialogoEscolherPerfil(
            titulo = "Horário para ${app.nome.ifBlank { app.nomePacote }}",
            perfis = d.perfis,
            atualId = app.perfilHorarioId,
            aoEscolher = { id -> aplicar(app, RegraApp.BLOQUEADO_POR_HORARIO, id); escolhendo = null },
            aoCriarNovo = { escolhendo = null; aoEditarHorario("", "app:${app.nomePacote}") },
            aoFechar = { escolhendo = null }
        )
    }
}
