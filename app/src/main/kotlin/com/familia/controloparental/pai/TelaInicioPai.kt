package com.familia.controloparental.pai

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaInicioPai(
    c: ContextoPai,
    aoAbrirFilho: (String) -> Unit,
    aoAdicionar: () -> Unit,
    aoAbrirAvisos: () -> Unit,
    aoAbrirMenu: () -> Unit
) {
    val agora by rememberAgora()
    val pendentes = c.pedidosPendentes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(NOME_APP) },
                navigationIcon = {
                    IconButton(onClick = aoAbrirMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = aoAdicionar,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Adicionar filho") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CabecalhoFamilia(
                    total = c.dispositivos.count { it.emparelhado },
                    online = c.dispositivos.count { it.emparelhado && it.estaOnline(agora) }
                )
            }

            if (pendentes.isNotEmpty()) {
                item {
                    Card(
                        onClick = aoAbrirAvisos,
                        colors = CardDefaults.cardColors(containerColor = CorAviso.copy(alpha = 0.16f)),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, tint = CorAviso)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${pendentes.size} pedido(s) de liberação", style = MaterialTheme.typography.titleMedium)
                                Text("Toca para aprovar ou recusar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            if (c.dispositivos.isEmpty()) {
                item {
                    EstadoVazio(
                        Icons.Filled.ChildCare,
                        "Ainda não tens filhos adicionados",
                        "Toca em \"Adicionar filho\", preenche os dados e instala a app no telemóvel dele para emparelhar."
                    )
                }
            }

            items(c.dispositivos.sortedBy { it.nomeExibicao.lowercase() }, key = { it.id }) { d ->
                CartaoFilho(d, agora, c.pedidosPendentes.count { it.dispositivoId == d.id }) { aoAbrirFilho(d.id) }
            }
        }
    }
}

@Composable
private fun CabecalhoFamilia(total: Int, online: Int) {
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visivel = true }
    AnimatedVisibility(visible = visivel, enter = fadeIn(tween(400)) + expandVertically(tween(400))) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fundoDeMarca().padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoAnimado(72.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("A tua família", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (total == 0) "Sem dispositivos emparelhados"
                        else "$online de $total dispositivo(s) online",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(LEMA_APP, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CartaoFilho(d: Dispositivo, agora: Long, pedidos: Int, aoClicar: () -> Unit) {
    val online = d.estaOnline(agora)
    val aviso = d.emparelhado && !d.protecao.completa
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoClicar),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AvatarCrianca(d.crianca.fotoBase64, d.nomeExibicao, 58.dp)
                    if (d.emparelhado) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (online) VerdeEsmeralda else MaterialTheme.colorScheme.outline,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier.size(16.dp).align(Alignment.BottomEnd)
                        ) {}
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.nomeExibicao, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        !d.emparelhado -> Text("A aguardar emparelhamento", color = CorAviso)
                        online -> Text("Online agora", color = VerdeEsmeralda)
                        else -> Text("Offline · visto ${tempoDecorrido(d.ultimoSinal, agora)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (d.nome.isNotBlank()) {
                        Text(d.nome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pedidos > 0) {
                    Badge { Text("$pedidos") }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
            if (d.emparelhado) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (d.bateria >= 0) {
                        EtiquetaEstado(
                            "${d.bateria}%" + if (d.aCarregar) " ⚡" else "",
                            if (d.bateria <= 15 && !d.aCarregar) CorErro else AzulPrimario
                        )
                    }
                    if (d.rede.isNotBlank() && online) EtiquetaEstado(d.rede, AzulPrimario)
                    if (aviso) EtiquetaEstado("Proteção incompleta", CorAviso)
                }
            }
        }
    }
}

@Suppress("unused")
private suspend fun _atraso() = delay(0)
