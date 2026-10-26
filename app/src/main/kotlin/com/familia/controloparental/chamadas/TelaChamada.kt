package com.familia.controloparental.chamadas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import com.familia.controloparental.comum.AvatarCrianca
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Estado global: o chat pede chamadas aqui e a camada por cima de toda a app mostra-as. */
object GestorChamadas {
    var ativa by mutableStateOf<DadosChamada?>(null)
        private set
    var meuNome by mutableStateOf("")
    /** Preenchido quando o utilizador carrega em "Atender" na notificação. */
    var pedidoAtender by mutableStateOf<String?>(null)

    fun ligar(outroUid: String, outroNome: String, video: Boolean, meuNomeAtual: String) {
        if (ativa != null) return
        meuNome = meuNomeAtual
        ativa = DadosChamada(id = "", chamador = true, outroUid = outroUid, outroNome = outroNome, video = video)
    }

    fun atender(c: ChamadaEntrada, meuNomeAtual: String) {
        if (ativa != null) return
        meuNome = meuNomeAtual
        ativa = DadosChamada(id = c.id, chamador = false, outroUid = c.deUid, outroNome = c.deNome, video = c.video)
    }

    fun fechar() {
        ativa = null
    }
}

private val FundoChamada = Brush.verticalGradient(listOf(Color(0xFF0A2540), Color(0xFF0D47A1), Color(0xFF0B3B33)))

/** Colocada por cima da navegação: chamadas a tocar e chamada em curso. */
@Composable
fun CamadaChamadas() {
    val ctx = LocalContext.current
    val sinal = remember { Sinalizacao() }
    val entrada by remember { sinal.observarEntrada() }.collectAsState(initial = null)
    val ativa = GestorChamadas.ativa
    var ignorada by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    if (ativa != null) {
        TelaChamada(ativa)
        return
    }
    val e = entrada
    if (e != null && e.id != ignorada) {
        // "Atender" carregado na notificação
        LaunchedEffect(e.id, GestorChamadas.pedidoAtender) {
            if (GestorChamadas.pedidoAtender == e.id) {
                GestorChamadas.pedidoAtender = null
                GestorChamadas.atender(e, GestorChamadas.meuNome)
            }
        }
        DisposableEffect(e.id) {
            val toque = ToqueEntrada(ctx)
            toque.iniciar()
            onDispose { toque.parar() }
        }
        LaunchedEffect(e.id) {
            delay((e.tocaAte - System.currentTimeMillis()).coerceAtLeast(0L))
            ignorada = e.id
        }
        Box(Modifier.fillMaxSize().background(FundoChamada)) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                Text("Ndjengue Safe · " + if (e.video) "videochamada" else "chamada de voz", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(40.dp))
                AvatarGrande(e.deNome, pulsar = true)
                Spacer(Modifier.height(24.dp))
                Text(e.deNome, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text("a chamar…", color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BotaoRotulado(Icons.Filled.CallEnd, "Recusar", Color(0xFFE53935)) {
                        ignorada = e.id
                        escopo.launch { sinal.recusar(e.id) }
                    }
                    BotaoRotulado(if (e.video) Icons.Filled.Videocam else Icons.Filled.Call, "Atender", Color(0xFF17A673)) {
                        GestorChamadas.atender(e, GestorChamadas.meuNome)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AvatarGrande(nome: String, pulsar: Boolean = false) {
    val anim = rememberInfiniteTransition(label = "pulso")
    val escala by anim.animateFloat(
        initialValue = 1f, targetValue = if (pulsar) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "escala"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).scale(escala).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
        AvatarCrianca(null, nome, 116.dp)
    }
}

@Composable
private fun BotaoRotulado(icone: androidx.compose.ui.graphics.vector.ImageVector, rotulo: String, cor: Color, aoClicar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = aoClicar, modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = cor, contentColor = Color.White)
        ) { Icon(icone, rotulo, Modifier.size(34.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(rotulo, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BotaoControlo(
    icone: androidx.compose.ui.graphics.vector.ImageVector, descricao: String, ativo: Boolean, aoClicar: () -> Unit
) {
    FilledIconButton(
        onClick = aoClicar, modifier = Modifier.size(58.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (ativo) Color.White else Color.White.copy(alpha = 0.16f),
            contentColor = if (ativo) Color(0xFF0D47A1) else Color.White
        )
    ) { Icon(icone, descricao, Modifier.size(26.dp)) }
}

@Composable
private fun formatarTempo(desde: Long): String {
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(desde) { while (true) { agora = System.currentTimeMillis(); delay(1000) } }
    val s = ((agora - desde) / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
fun TelaChamada(dados: DadosChamada) {
    val ctx = LocalContext.current
    var permitido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!dados.video || ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        )
    }
    var pedido by remember { mutableStateOf(false) }
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permitido = r[Manifest.permission.RECORD_AUDIO] == true && (!dados.video || r[Manifest.permission.CAMERA] == true)
        pedido = true
    }
    LaunchedEffect(Unit) {
        if (!permitido) lancador.launch(
            if (dados.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
        )
    }

    if (!permitido) {
        Box(Modifier.fillMaxSize().background(FundoChamada)) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    if (pedido) "Sem permissão de microfone${if (dados.video) "/câmara" else ""}. Ative-a nas definições do Android."
                    else "A pedir permissões…",
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    GestorChamadas.fechar()
                    if (!dados.chamador) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { Sinalizacao().recusar(dados.id) }
                    }
                }) { Text("Fechar") }
            }
        }
        return
    }

    val sessao = remember(dados) {
        SessaoChamada(ctx, dados, GestorChamadas.meuNome) { GestorChamadas.fechar() }
    }
    LaunchedEffect(sessao) { sessao.iniciar() }
    DisposableEffect(sessao) {
        onDispose {
            sessao.terminar(apagar = true)
            sessao.liberarEgl()
        }
    }

    Box(Modifier.fillMaxSize().background(FundoChamada)) {
        val comVideoRemoto = dados.video && sessao.videoRemoto != null
        if (dados.video) {
            VistaVideo(sessao.videoRemoto, sessao.egl, espelhar = false, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 72.dp, end = 12.dp)
                    .size(width = 108.dp, height = 148.dp).clip(RoundedCornerShape(14.dp))
            ) { VistaVideo(sessao.videoLocal, sessao.egl, espelhar = true, Modifier.fillMaxSize(), sobrepor = true) }
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(top = 12.dp)
                .then(if (comVideoRemoto) Modifier.background(Color.Black.copy(alpha = 0.25f)).padding(vertical = 8.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!comVideoRemoto) Spacer(Modifier.height(if (dados.video) 0.dp else 56.dp))
            if (!dados.video || !comVideoRemoto) {
                if (!dados.video) {
                    AvatarGrande(dados.outroNome, pulsar = !sessao.ligada)
                    Spacer(Modifier.height(20.dp))
                }
            }
            Text(dados.outroNome, color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                if (sessao.ligada && sessao.ligadaDesde > 0) formatarTempo(sessao.ligadaDesde) else sessao.estado,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                "Cifrada · nada é gravado" + if (sessao.tipoLigacao.isNotEmpty()) " · ${sessao.tipoLigacao}" else "",
                color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 28.dp)
                .clip(RoundedCornerShape(40.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            BotaoControlo(if (sessao.altifalante) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown, "Altifalante", sessao.altifalante) { sessao.alternarAltifalante() }
            if (dados.video) {
                BotaoControlo(if (sessao.cameraAtiva) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Câmara", !sessao.cameraAtiva) { sessao.alternarCamera() }
                BotaoControlo(Icons.Filled.FlipCameraAndroid, "Virar câmara", false) { sessao.mudarCamera() }
            }
            BotaoControlo(if (sessao.microfoneAtivo) Icons.Filled.Mic else Icons.Filled.MicOff, "Microfone", !sessao.microfoneAtivo) { sessao.alternarMicrofone() }
            FilledIconButton(
                onClick = { sessao.terminar(apagar = true) }, modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
            ) { Icon(Icons.Filled.CallEnd, "Desligar", Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun VistaVideo(track: VideoTrack?, egl: EglBase, espelhar: Boolean, modifier: Modifier, sobrepor: Boolean = false) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    DisposableEffect(Unit) {
        onDispose { try { renderer?.release() } catch (_: Exception) {} }
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(espelhar)
                setEnableHardwareScaler(true)
                if (sobrepor) setZOrderMediaOverlay(true)
                renderer = this
            }
        }
    )
    DisposableEffect(track, renderer) {
        val r = renderer
        val t = track
        if (r != null && t != null) {
            try { t.addSink(r) } catch (_: Exception) {}
        }
        onDispose {
            if (r != null && t != null) {
                try { t.removeSink(r) } catch (_: Exception) {}
            }
        }
    }
}
