package com.familia.controloparental.pai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import java.time.LocalDate

@Composable
fun SeparadorRelatorios(d: DadosFilho) {
    val hojeStr = hojeIso()
    val hoje = d.relatorios.firstOrNull { it.data == hojeStr }
    val dias = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).toString() }
    val porDia = d.relatorios.associateBy { it.data }
    val maxDia = (dias.maxOfOrNull { porDia[it]?.totalMs ?: 0L } ?: 0L).coerceAtLeast(1L)
    val nomes = d.apps.associate { it.nomePacote to it.nome }
    val icones = d.apps.associate { it.nomePacote to it.iconeBase64 }
    val topo = (hoje?.tempoEcraPorApp ?: emptyMap()).entries.sortedByDescending { it.value }.take(10)
    val maxApp = (topo.firstOrNull()?.value ?: 1L).coerceAtLeast(1L)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CartaoSecao("Tempo de ecrã hoje") {
            Text(formatarDuracao(hoje?.totalMs ?: 0L), style = MaterialTheme.typography.headlineMedium)
            if (!d.dispositivo.protecao.usoAppsPermitido && d.dispositivo.emparelhado) {
                Text(
                    "Falta a criança ativar o acesso ao tempo de ecrã no telemóvel dela.",
                    color = CorAviso, style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        CartaoSecao("Últimos 7 dias") {
            Row(
                Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dias.forEach { dia ->
                    val ms = porDia[dia]?.totalMs ?: 0L
                    val fracao = ms.toFloat() / maxDia.toFloat()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (ms > 0) formatarDuracao(ms).replace(" min", "m").replace(" h ", "h") else "",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(22.dp).height((100 * fracao).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (dia == hojeStr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(nomeDiaCurto(dia), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        CartaoSecao("Apps mais usadas hoje") {
            if (topo.isEmpty()) Text("Sem dados ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            topo.forEach { (pacote, ms) ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconeApp(icones[pacote], nomes[pacote] ?: pacote, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                nomes[pacote]?.ifBlank { pacote } ?: pacote, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(formatarDuracao(ms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { ms.toFloat() / maxApp.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        CartaoSecao("Tentativas bloqueadas") {
            if (d.bloqueios.isEmpty()) {
                Text("Nenhuma tentativa registada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            d.bloqueios.take(30).forEach { b ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (b.tipo == TipoAlvo.URL) Icons.Filled.Language else Icons.Filled.Block, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(b.rotulo.ifBlank { b.alvo }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatarDataHora(b.em), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Suppress("unused")
private val _icone = Icons.Filled.Apps
