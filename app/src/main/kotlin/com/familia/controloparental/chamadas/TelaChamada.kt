package com.familia.controloparental.chamadas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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

/** Colocada por cima da navegação: chamadas a tocar e chamada em curso. */
@Composable
fun CamadaChamadas() {
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
        LaunchedEffect(e.id) {
            delay((e.tocaAte - System.currentTimeMillis()).coerceAtLeast(0L))
            ignorada = e.id
        }
        Surface(Modifier.fillMaxSize(), color = Color(0xFF10202E)) {
            Column(
                Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(if (e.video) "Videochamada" else "Chamada de voz", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Text(e.deNome, color = Color.White, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(64.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    BotaoRedondo(Icons.Filled.CallEnd, "Recusar", Color(0xFFD32F2F)) {
                        ignorada = e.id
                        escopo.launch { sinal.recusar(e.id) }
                    }
                    BotaoRedondo(if (e.video) Icons.Filled.Videocam else Icons.Filled.Call, "Atender", Color(0xFF2E7D32)) {
                        GestorChamadas.atender(e, GestorChamadas.meuNome)
                    }
                }
            }
        }
    }
}

@Composable
private fun BotaoRedondo(icone: androidx.compose.ui.graphics.vector.ImageVector, descricao: String, cor: Color, aoClicar: () -> Unit) {
    FilledIconButton(
        onClick = aoClicar, modifier = Modifier.size(68.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = cor, contentColor = Color.White)
    ) { Icon(icone, descricao, Modifier.size(32.dp)) }
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
        Surface(Modifier.fillMaxSize(), color = Color(0xFF10202E)) {
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
                        // recusa para o outro lado não ficar à espera
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

    Surface(Modifier.fillMaxSize(), color = Color(0xFF10202E)) {
        Box(Modifier.fillMaxSize()) {
            if (dados.video) {
                VistaVideo(sessao.videoRemoto, sessao.egl, espelhar = false, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp).size(width = 110.dp, height = 150.dp).clip(MaterialTheme.shapes.medium)) {
                    VistaVideo(sessao.videoLocal, sessao.egl, espelhar = true, Modifier.fillMaxSize(), sobrepor = true)
                }
            }
            Column(
                Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(dados.outroNome, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(sessao.estado, color = Color.White.copy(alpha = 0.8f))
                Text("Cifrada ponta a ponta · nada é gravado", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                BotaoRedondo(if (sessao.microfoneAtivo) Icons.Filled.Mic else Icons.Filled.MicOff, "Microfone", Color(0xFF455A64)) { sessao.alternarMicrofone() }
                if (dados.video) {
                    BotaoRedondo(if (sessao.cameraAtiva) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Câmara", Color(0xFF455A64)) { sessao.alternarCamera() }
                    BotaoRedondo(Icons.Filled.FlipCameraAndroid, "Virar câmara", Color(0xFF455A64)) { sessao.mudarCamera() }
                }
                BotaoRedondo(Icons.Filled.CallEnd, "Desligar", Color(0xFFD32F2F)) { sessao.terminar(apagar = true) }
            }
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
