package com.familia.controloparental.pai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.familia.controloparental.comum.*
import com.familia.controloparental.filho.Ambiente
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Mapa em tempo real (OpenStreetMap: gratuito, sem chave nem faturação). Enquanto este ecrã está aberto,
 * o telemóvel da criança envia a posição a cada poucos segundos. Os botões abrem o Google Maps do telemóvel do pai.
 */
@Composable
fun TelaMapa(c: ContextoPai, didInicial: String) {
    val ctx = LocalContext.current
    val agora by rememberAgora()
    val escopo = rememberCoroutineScope()
    val emparelhados = c.dispositivos.filter { it.emparelhado }

    var selecionado by remember { mutableStateOf(didInicial) }
    LaunchedEffect(emparelhados.map { it.id }) {
        if (selecionado.isBlank() || emparelhados.none { it.id == selecionado }) {
            selecionado = emparelhados.firstOrNull()?.id ?: ""
        }
    }
    var aoVivo by remember { mutableStateOf(true) }
    var seguir by remember { mutableStateOf(true) }

    val disp = c.dispositivo(selecionado)
    val loc = disp?.localizacaoAtual

    val historico by remember(selecionado) {
        if (selecionado.isBlank()) flowOf(emptyList<LocalizacaoPonto>())
        else c.repo.observarHistoricoLocalizacao(c.fid, selecionado, 80)
    }.collectAsState(initial = emptyList())

    // Pede ao telemóvel da criança para enviar a posição com frequência enquanto o mapa está aberto.
    LaunchedEffect(selecionado, aoVivo) {
        if (aoVivo && selecionado.isNotBlank()) {
            while (true) {
                try { c.repo.definirRastreioAoVivo(c.fid, selecionado, System.currentTimeMillis() + 10 * 60_000L) } catch (_: Exception) {}
                delay(4 * 60_000L)
            }
        }
    }
    DisposableEffect(selecionado, aoVivo) {
        val did = selecionado
        onDispose {
            if (aoVivo && did.isNotBlank()) {
                Ambiente.escopo.launch { try { c.repo.definirRastreioAoVivo(c.fid, did, 0L) } catch (_: Exception) {} }
            }
        }
    }

    val mapa = remember {
        val cfg = Configuration.getInstance()
        cfg.load(ctx, ctx.getSharedPreferences("osmdroid", 0))
        cfg.userAgentValue = ctx.packageName
        val base = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(38.7223, -9.1393))
        }
    }
    DisposableEffect(Unit) {
        mapa.onResume()
        onDispose {
            mapa.onPause()
            mapa.onDetach()
        }
    }

    var primeiraVez by remember(selecionado) { mutableStateOf(true) }
    LaunchedEffect(loc?.timestamp, loc?.latitude, loc?.longitude, historico.size, selecionado) {
        mapa.overlays.clear()
        if (historico.size > 1) {
            mapa.overlays.add(Polyline(mapa).apply {
                setPoints(historico.reversed().map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = 0xAA1565C0.toInt()
                outlinePaint.strokeWidth = 7f
            })
        }
        if (loc != null) {
            val ponto = GeoPoint(loc.latitude, loc.longitude)
            mapa.overlays.add(Polygon(mapa).apply {
                points = Polygon.pointsAsCircle(ponto, loc.precisaoMetros.toDouble().coerceIn(8.0, 500.0))
                fillPaint.color = 0x221565C0
                outlinePaint.color = 0x881565C0.toInt()
                outlinePaint.strokeWidth = 2f
            })
            mapa.overlays.add(Marker(mapa).apply {
                position = ponto
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = disp?.nomeExibicao ?: ""
                snippet = "Atualizado ${tempoDecorrido(loc.timestamp)}"
            })
            if (seguir || primeiraVez) {
                if (primeiraVez) mapa.controller.setZoom(17.0)
                mapa.controller.animateTo(ponto)
                primeiraVez = false
            }
        }
        mapa.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapa }, modifier = Modifier.fillMaxSize())

        // Seleção de filho
        if (emparelhados.size > 1) {
            LazyRow(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(emparelhados, key = { it.id }) { d ->
                    FilterChip(
                        selected = d.id == selecionado,
                        onClick = { selecionado = d.id },
                        label = { Text(d.nomeExibicao) },
                        leadingIcon = { AvatarCrianca(d.crianca.fotoBase64, d.nomeExibicao, 24.dp) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }

        // Botões flutuantes
        Column(
            Modifier.align(Alignment.CenterEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallFloatingActionButton(onClick = { mapa.controller.zoomIn() }) { Icon(Icons.Filled.Add, "Aproximar") }
            SmallFloatingActionButton(onClick = { mapa.controller.zoomOut() }) { Icon(Icons.Filled.Remove, "Afastar") }
            SmallFloatingActionButton(onClick = {
                seguir = true
                loc?.let { mapa.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
            }) { Icon(Icons.Filled.MyLocation, "Centrar na criança") }
        }

        if (emparelhados.isEmpty()) {
            Surface(
                Modifier.align(Alignment.Center).padding(24.dp), shape = MaterialTheme.shapes.large,
                shadowElevation = 6.dp
            ) {
                EstadoVazio(Icons.Filled.Map, "Sem dispositivos", "Adiciona e emparelha o telemóvel de um filho para o ver no mapa.")
            }
        } else {
            // Cartão inferior com o estado
            AnimatedVisibility(
                visible = disp != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                if (disp != null) CartaoLocalizacao(disp, agora, aoVivo, { aoVivo = it }, seguir, { seguir = it })
            }
        }
    }
}

@Composable
private fun PontoAoVivo(ativo: Boolean) {
    val t = rememberInfiniteTransition(label = "ponto")
    val alfa by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alfa")
    Box(
        Modifier.size(10.dp).clip(CircleShape)
            .background(if (ativo) CorErro else MaterialTheme.colorScheme.outline)
            .alpha(if (ativo) alfa else 1f)
    )
}

@Composable
private fun CartaoLocalizacao(
    d: Dispositivo,
    agora: Long,
    aoVivo: Boolean,
    aoMudarAoVivo: (Boolean) -> Unit,
    seguir: Boolean,
    aoMudarSeguir: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val loc = d.localizacaoAtual
    val online = d.estaOnline(agora)
    Surface(
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCrianca(d.crianca.fotoBase64, d.nomeExibicao, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.nomeExibicao, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PontoAoVivo(aoVivo && online)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                !online -> "Offline · ${tempoDecorrido(d.ultimoSinal, agora)}"
                                aoVivo -> "Ao vivo"
                                else -> "Online"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (d.bateria >= 0) EtiquetaEstado("${d.bateria}%", if (d.bateria <= 15) CorErro else AzulPrimario)
            }
            Spacer(Modifier.height(8.dp))
            if (loc == null) {
                Text(
                    "Ainda sem localização. A criança tem de ativar a localização \"sempre\" na app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start
                )
            } else {
                Text(
                    "Posição de ${tempoDecorrido(loc.timestamp, agora)} · precisão ≈ ${loc.precisaoMetros.toInt()} m" +
                        if (loc.velocidadeMs > 1f) " · ${(loc.velocidadeMs * 3.6f).toInt()} km/h" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!online) {
                    Text("Última posição conhecida (o telemóvel está offline).", color = CorAviso)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Tempo real", modifier = Modifier.weight(1f))
                Switch(checked = aoVivo, onCheckedChange = aoMudarAoVivo)
                Spacer(Modifier.width(14.dp))
                Text("Seguir")
                Spacer(Modifier.width(6.dp))
                Switch(checked = seguir, onCheckedChange = aoMudarSeguir)
            }
            if (loc != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    OutlinedButton(onClick = { abrirNoGoogleMaps(ctx, loc.latitude, loc.longitude, d.nomeExibicao) }, modifier = Modifier.weight(1f)) {
                        Text("Google Maps")
                    }
                    Button(onClick = { navegarAteCrianca(ctx, loc.latitude, loc.longitude) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Directions, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ir até lá")
                    }
                    if (d.crianca.telefone.isNotBlank()) {
                        FilledTonalIconButton(onClick = { ligarPara(ctx, d.crianca.telefone) }) { Icon(Icons.Filled.Call, "Ligar") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
