package com.familia.controloparental.pai

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAvisos(c: ContextoPai, aoAbrirMapa: (String) -> Unit) {
    var separador by remember { mutableIntStateOf(0) }
    val pendentes = c.pedidosPendentes.size
    val naoLidos = c.alertas.count { !it.lido }

    Scaffold(topBar = { TopAppBar(title = { Text("Pedidos e alertas") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = separador) {
                Tab(selected = separador == 0, onClick = { separador = 0 }, text = {
                    BadgedBox(badge = { if (pendentes > 0) Badge { Text("$pendentes") } }) { Text("Pedidos") }
                })
                Tab(selected = separador == 1, onClick = { separador = 1 }, text = {
                    BadgedBox(badge = { if (naoLidos > 0) Badge { Text("$naoLidos") } }) { Text("Alertas") }
                })
            }
            if (separador == 0) ListaPedidos(c) else ListaAlertas(c, aoAbrirMapa)
        }
    }
}

@Composable
private fun ListaPedidos(c: ContextoPai) {
    val escopo = rememberCoroutineScope()
    var erro by remember { mutableStateOf<String?>(null) }
    val pendentes = c.pedidosPendentes
    val historico = c.pedidos.filter { it.status != StatusPedidoLiberacao.PENDENTE }.take(20)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (erro != null) item { MensagemErro(erro) }
        if (pendentes.isEmpty()) {
            item {
                EstadoVazio(Icons.Filled.LockOpen, "Sem pedidos pendentes", "Quando uma criança pedir para usar algo bloqueado, aparece aqui.")
            }
        }
        items(pendentes, key = { it.id }) { p ->
            var minutos by remember(p.id) { mutableIntStateOf(p.duracaoMinutos) }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeEmCirculo(if (p.tipoAlvo == TipoAlvo.URL) Icons.Filled.Language else Icons.Filled.Apps, CorAviso, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        EtiquetaEstado("Pedido de liberação", CorAviso)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("${p.criancaNome.ifBlank { "Criança" }} quer usar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.rotulo.ifBlank { p.alvo }, style = MaterialTheme.typography.titleLarge)
                    Text("Pedido ${tempoDecorrido(p.criadoEm)} · ${if (p.tipoAlvo == TipoAlvo.URL) "site" else "app"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Permitir durante:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { m ->
                            FilterChip(
                                selected = minutos == m, onClick = { minutos = m },
                                label = { Text(if (m >= 60) "${m / 60} h" else "$m min") }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            escopo.launch {
                                try { c.repo.responderPedido(c.fid, p.id, false, 0) } catch (e: Exception) { erro = traduzirErro(e) }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Recusar") }
                        Button(onClick = {
                            escopo.launch {
                                try { c.repo.responderPedido(c.fid, p.id, true, minutos) } catch (e: Exception) { erro = traduzirErro(e) }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Aprovar") }
                    }
                }
            }
        }
        if (historico.isNotEmpty()) {
            item { Text("Histórico", style = MaterialTheme.typography.titleMedium) }
            items(historico, key = { "h" + it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.criancaNome.ifBlank { "Criança" }} · ${p.rotulo.ifBlank { p.alvo }}")
                        Text(formatarDataHora(p.criadoEm), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    when (p.status) {
                        StatusPedidoLiberacao.APROVADO -> EtiquetaEstado("Aprovado ${p.duracaoAprovadaMinutos} min", VerdeEsmeralda)
                        StatusPedidoLiberacao.RECUSADO -> EtiquetaEstado("Recusado", CorErro)
                        else -> EtiquetaEstado("Expirado", TextoSecundario)
                    }
                }
            }
        }
    }
}

private fun iconeDoAlerta(t: TipoAlerta): ImageVector = when (t) {
    TipoAlerta.PEDIDO_AJUDA -> Icons.Filled.Sos
    TipoAlerta.SENHA_ADMIN_USADA -> Icons.Filled.Key
    TipoAlerta.INFO -> Icons.Filled.Info
    TipoAlerta.LOCALIZACAO_DESATIVADA -> Icons.Filled.LocationOff
    TipoAlerta.VPN_DESATIVADA -> Icons.Filled.VpnLock
    else -> Icons.Filled.Warning
}

@Composable
private fun ListaAlertas(c: ContextoPai, aoAbrirMapa: (String) -> Unit) {
    val escopo = rememberCoroutineScope()
    val naoLidos = c.alertas.filter { !it.lido }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (c.alertas.isEmpty()) {
            item { EstadoVazio(Icons.Filled.NotificationsNone, "Sem alertas", "Vais ser avisado de pedidos de ajuda, tentativas de desinstalar e proteções desligadas.") }
        }
        if (naoLidos.isNotEmpty()) {
            item {
                TextButton(onClick = {
                    escopo.launch { try { c.repo.marcarTodosAlertasLidos(c.fid, naoLidos.map { it.id }) } catch (_: Exception) {} }
                }) { Text("Marcar todos como lidos") }
            }
        }
        items(c.alertas, key = { it.id }) { a ->
            val grave = a.tipo == TipoAlerta.PEDIDO_AJUDA || a.tipo == TipoAlerta.DESINSTALACAO_TENTADA || a.tipo == TipoAlerta.ADMIN_DESATIVADO
            val cor = if (grave) CorErro else CorAviso
            Card(
                Modifier.fillMaxWidth().clickable {
                    if (!a.lido) escopo.launch { try { c.repo.marcarAlertaLido(c.fid, a.id) } catch (_: Exception) {} }
                },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (a.lido) MaterialTheme.colorScheme.surface else cor.copy(alpha = 0.10f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconeEmCirculo(iconeDoAlerta(a.tipo), cor, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.criancaNome.ifBlank { "Criança" }, style = MaterialTheme.typography.titleMedium)
                            Text(a.mensagem)
                            Text(formatarDataHora(a.criadoEm), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!a.lido) Box(Modifier.padding(top = 6.dp)) { EtiquetaEstado("Novo", cor) }
                    }
                    if (a.tipo == TipoAlerta.PEDIDO_AJUDA) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { aoAbrirMapa(a.dispositivoId) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Map, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ver onde está")
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val _cor = Color.Unspecified
