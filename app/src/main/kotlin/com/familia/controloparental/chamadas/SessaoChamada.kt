package com.familia.controloparental.chamadas

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Uma chamada WebRTC ponto a ponto. O áudio/vídeo segue em UDP (SRTP/DTLS), cifrado, diretamente
 * entre os dois telemóveis; nada passa pelo Firestore nem é gravado. Tenta primeiro a ligação direta
 * (STUN); se a rede a bloquear, usa o relé TURN gratuito (ver ConfigIce) sem que o áudio deixe de estar cifrado.
 */
class SessaoChamada(
    private val contexto: Context,
    private val dados: DadosChamada,
    private val meuNome: String,
    private val aoTerminar: () -> Unit
) {
    var estado by mutableStateOf("A preparar…")
        private set
    var ligada by mutableStateOf(false)
        private set
    var videoRemoto by mutableStateOf<VideoTrack?>(null)
        private set
    var videoLocal by mutableStateOf<VideoTrack?>(null)
        private set
    var microfoneAtivo by mutableStateOf(true)
        private set
    var cameraAtiva by mutableStateOf(true)
        private set
    var altifalante by mutableStateOf(false)
        private set
    /** Instante (ms) em que a chamada ficou ligada; 0 enquanto não liga. */
    var ligadaDesde by mutableStateOf(0L)
        private set
    var tipoLigacao by mutableStateOf("")
        private set

    val egl: EglBase = EglBase.create()

    private val sinal = Sinalizacao()
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callId: String = dados.id
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoTrackLocal: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var observador: com.google.firebase.firestore.ListenerRegistration? = null
    private var terminada = false
    private var remotaDefinida = false
    private val candidatosPendentes = mutableListOf<IceCandidate>()
    private val candidatosVistos = HashSet<String>()
    private var tempoLimite: Job? = null
    private val toqueSaida = ToqueSaida()

    private val audioManager = contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        @Volatile
        private var iniciado = false

        private fun iniciarBiblioteca(ctx: Context) {
            if (iniciado) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions()
            )
            iniciado = true
        }
    }

    fun iniciar() {
        escopo.launch {
            try {
                com.familia.controloparental.servico.ServicoChamadaAtiva.iniciar(contexto, dados.video)
                preparar()
                if (dados.chamador) chamar() else atender()
            } catch (e: Exception) {
                estado = "Erro: ${e.message ?: "falha na chamada"}"
                delay(1500)
                terminar(apagar = true)
            }
        }
    }

    private suspend fun preparar() {
        iniciarBiblioteca(contexto)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        altifalante = dados.video
        audioManager.isSpeakerphoneOn = altifalante

        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f

        val servidores = ConfigIce.obter()
        val config = PeerConnection.RTCConfiguration(servidores).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // UDP é sempre preferido; o TCP/TLS só entra como último recurso (relé em redes muito restritas).
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }
        pc = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                escopo.launch {
                    when (p0) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> {
                            if (!ligada) ligadaDesde = System.currentTimeMillis()
                            ligada = true
                            toqueSaida.parar()
                            tempoLimite?.cancel()
                            estado = "Em chamada"
                            descobrirTipoLigacao()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            toqueSaida.parar()
                            estado = "Não foi possível ligar (rede)"
                            delay(1500)
                            terminar(apagar = true)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            estado = "Ligação instável…"
                            // Se não recuperar em 20 s (o outro lado fechou a app ou perdeu a rede), termina.
                            delay(20_000)
                            if (!terminada && estado == "Ligação instável…") {
                                estado = "Chamada terminada"
                                delay(800)
                                terminar(apagar = true)
                            }
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {}
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                if (c == null) return
                escopo.launch {
                    try {
                        sinal.publicarCandidato(callId, dados.chamador, c.sdpMid, c.sdpMLineIndex, c.sdp)
                    } catch (_: Exception) {
                    }
                }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(t: RtpTransceiver?) {
                val track = t?.receiver?.track()
                if (track is VideoTrack) escopo.launch { videoRemoto = track }
            }
        }) ?: throw IllegalStateException("Não foi possível criar a ligação.")

        val fonteAudio = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("a0", fonteAudio)
        pc!!.addTrack(audioTrack, listOf("s0"))

        if (dados.video) {
            val enumerador = Camera2Enumerator(contexto)
            val nome = enumerador.deviceNames.firstOrNull { enumerador.isFrontFacing(it) } ?: enumerador.deviceNames.firstOrNull()
            val cap = nome?.let { enumerador.createCapturer(it, null) }
            if (cap != null) {
                capturer = cap
                helper = SurfaceTextureHelper.create("captura", egl.eglBaseContext)
                videoSource = f.createVideoSource(cap.isScreencast)
                cap.initialize(helper, contexto, videoSource!!.capturerObserver)
                cap.startCapture(640, 480, 24)
                videoTrackLocal = f.createVideoTrack("v0", videoSource)
                pc!!.addTrack(videoTrackLocal, listOf("s0"))
                videoLocal = videoTrackLocal
            }
        }
    }

    /** Diz se a chamada vai direta ou pelo relé (só informativo). */
    private fun descobrirTipoLigacao() {
        pc?.getStats { relatorio ->
            try {
                val par = relatorio.statsMap.values.firstOrNull { it.type == "candidate-pair" && it.members["state"] == "succeeded" && it.members["nominated"] == true }
                val idLocal = par?.members?.get("localCandidateId") as? String
                val local = if (idLocal != null) relatorio.statsMap[idLocal] else null
                val tipo = local?.members?.get("candidateType")?.toString()
                escopo.launch { tipoLigacao = if (tipo == "relay") "via relé" else "direta" }
            } catch (_: Exception) {
            }
        }
    }

    private fun restricoes() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (dados.video) "true" else "false"))
    }

    private suspend fun chamar() {
        estado = "A chamar…"
        toqueSaida.iniciar()
        callId = sinal.criar(dados.outroUid, meuNome, dados.video)
        val oferta = criarSdp(true)
        definirLocal(oferta)
        sinal.publicarOferta(callId, oferta.description)
        observar()
        tempoLimite = escopo.launch {
            delay(Sinalizacao.TOCA_MS + 10_000L)
            if (!ligada) {
                estado = "Sem resposta"
                delay(1200)
                terminar(apagar = true)
            }
        }
    }

    private suspend fun atender() {
        estado = "A ligar…"
        val doc = sinal.ref(callId).get().await()
        val sdp = doc.getString("oferta") ?: throw IllegalStateException("A chamada já terminou.")
        definirRemota(SessionDescription(SessionDescription.Type.OFFER, sdp))
        remotaDefinida = true
        val resposta = criarSdp(false)
        definirLocal(resposta)
        sinal.publicarResposta(callId, resposta.description)
        observar()
        tempoLimite = escopo.launch {
            delay(25_000L)
            if (!ligada) {
                estado = "Falha na ligação"
                delay(1200)
                terminar(apagar = true)
            }
        }
    }

    private fun observar() {
        observador = sinal.ref(callId).addSnapshotListener { snap, erro ->
            if (erro != null || snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                escopo.launch { terminar(apagar = false) }
                return@addSnapshotListener
            }
            val est = snap.getString("estado")
            if (est == Sinalizacao.ESTADO_TERMINADA) {
                // O outro lado desligou: termina por completo aqui também.
                escopo.launch {
                    if (ligada) estado = "Chamada terminada"
                    terminar(apagar = false)
                }
                return@addSnapshotListener
            }
            if (est == Sinalizacao.ESTADO_RECUSADA) {
                escopo.launch {
                    estado = "Chamada recusada"
                    delay(1200)
                    terminar(apagar = dados.chamador)
                }
                return@addSnapshotListener
            }
            escopo.launch {
                if (dados.chamador && !remotaDefinida) {
                    snap.getString("resposta")?.let {
                        try {
                            definirRemota(SessionDescription(SessionDescription.Type.ANSWER, it))
                            remotaDefinida = true
                            estado = "A ligar…"
                            esvaziarPendentes()
                        } catch (_: Exception) {
                        }
                    }
                }
                val campo = if (dados.chamador) "candidatosPara" else "candidatosDe"
                (snap.get(campo) as? List<*>)?.forEach { item ->
                    val s = item?.toString() ?: return@forEach
                    if (!candidatosVistos.add(s)) return@forEach
                    val p = s.split("|", limit = 3)
                    if (p.size == 3) {
                        val c = IceCandidate(p[0], p[1].toIntOrNull() ?: 0, p[2])
                        if (remotaDefinida) pc?.addIceCandidate(c) else candidatosPendentes.add(c)
                    }
                }
            }
        }
    }

    private fun esvaziarPendentes() {
        candidatosPendentes.forEach { pc?.addIceCandidate(it) }
        candidatosPendentes.clear()
    }

    private suspend fun criarSdp(oferta: Boolean): SessionDescription = suspendCancellableCoroutine { c ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription?) { if (d != null) c.resume(d) else c.resumeWithException(Exception("SDP vazio")) }
            override fun onCreateFailure(e: String?) { c.resumeWithException(Exception(e ?: "falha ao criar SDP")) }
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }
        if (oferta) pc!!.createOffer(obs, restricoes()) else pc!!.createAnswer(obs, restricoes())
    }

    private suspend fun definirLocal(d: SessionDescription) = suspendCancellableCoroutine<Unit> { c ->
        pc!!.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { c.resume(Unit) }
            override fun onSetFailure(e: String?) { c.resumeWithException(Exception(e ?: "falha local")) }
        }, d)
    }

    private suspend fun definirRemota(d: SessionDescription) = suspendCancellableCoroutine<Unit> { c ->
        pc!!.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { c.resume(Unit) }
            override fun onSetFailure(e: String?) { c.resumeWithException(Exception(e ?: "falha remota")) }
        }, d)
    }

    fun alternarMicrofone() {
        microfoneAtivo = !microfoneAtivo
        audioTrack?.setEnabled(microfoneAtivo)
    }

    fun alternarAltifalante() {
        altifalante = !altifalante
        audioManager.isSpeakerphoneOn = altifalante
    }

    fun alternarCamera() {
        cameraAtiva = !cameraAtiva
        videoTrackLocal?.setEnabled(cameraAtiva)
    }

    fun mudarCamera() {
        capturer?.switchCamera(null)
    }

    /** Chamado pelo ecrã depois de largar as vistas de vídeo. */
    fun liberarEgl() {
        try { egl.release() } catch (_: Exception) {}
    }

    /** Termina e liberta tudo. apagar=true apaga também o documento de sinalização. */
    fun terminar(apagar: Boolean = true) {
        if (terminada) return
        terminada = true
        toqueSaida.parar()
        com.familia.controloparental.servico.ServicoChamadaAtiva.parar(contexto)
        tempoLimite?.cancel()
        observador?.remove()
        val id = callId
        videoRemoto = null
        videoLocal = null
        try { pc?.close() } catch (_: Exception) {}
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        try { videoSource?.dispose() } catch (_: Exception) {}
        try { helper?.dispose() } catch (_: Exception) {}
        try { videoTrackLocal?.dispose() } catch (_: Exception) {}
        try { audioTrack?.dispose() } catch (_: Exception) {}
        try { pc?.dispose() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        CoroutineScope(Dispatchers.IO).launch {
            if (apagar) sinal.terminar(id)
        }
        escopo.cancel()
        aoTerminar()
    }
}
