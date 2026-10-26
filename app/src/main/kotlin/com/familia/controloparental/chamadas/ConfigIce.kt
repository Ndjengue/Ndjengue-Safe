package com.familia.controloparental.chamadas

import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.PeerConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Servidores de ligação (ICE) da chamada.
 *
 *  - STUN (Google, grátis): descobre o endereço público de cada telemóvel → chamada direta em UDP.
 *  - TURN (Open Relay da Metered, grátis, 20 GB/mês, sem registo): se a rede bloquear a ligação direta
 *    (4G com CGNAT, Wi-Fi de empresa…), o áudio/vídeo passa por este relé. Continua cifrado
 *    (DTLS-SRTP) ponta a ponta: o relé não consegue ouvir nem ver nada e nada é gravado.
 *  - Opcional: o super administrador pode escrever em Firestore `config/ice` os seus próprios servidores
 *    (campos `urls` = lista, `username`, `credential`) para trocar de fornecedor sem recompilar a app.
 */
object ConfigIce {
    private const val ANFITRIAO = "staticauth.openrelay.metered.ca"
    private const val SEGREDO = "openrelayprojectsecret"

    @Volatile
    private var personalizado: List<PeerConnection.IceServer>? = null
    @Volatile
    private var carregadoEm = 0L

    private fun credenciaisTemporarias(): Pair<String, String> {
        val expira = System.currentTimeMillis() / 1000 + 24 * 3600
        val utilizador = expira.toString()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(SEGREDO.toByteArray(), "HmacSHA1"))
        val credencial = Base64.encodeToString(mac.doFinal(utilizador.toByteArray()), Base64.NO_WRAP)
        return utilizador to credencial
    }

    private fun porOmissao(): List<PeerConnection.IceServer> {
        val (u, c) = credenciaisTemporarias()
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:$ANFITRIAO:80").createIceServer(),
            PeerConnection.IceServer.builder(
                listOf(
                    "turn:$ANFITRIAO:80?transport=udp",
                    "turn:$ANFITRIAO:443?transport=udp",
                    "turn:$ANFITRIAO:80?transport=tcp",
                    "turn:$ANFITRIAO:443?transport=tcp",
                    "turns:$ANFITRIAO:443?transport=tcp"
                )
            ).setUsername(u).setPassword(c).createIceServer()
        )
    }

    /** Lê `config/ice` (no máximo uma vez por 10 min, com limite de 3 s). Nunca falha. */
    suspend fun obter(): List<PeerConnection.IceServer> {
        val agora = System.currentTimeMillis()
        if (agora - carregadoEm > 10 * 60_000L) {
            carregadoEm = agora
            personalizado = try {
                withTimeoutOrNull(3_000L) {
                    val d = FirebaseFirestore.getInstance().collection("config").document("ice").get().await()
                    val urls = (d.get("urls") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                    if (urls.isEmpty()) null
                    else listOf(
                        PeerConnection.IceServer.builder(urls)
                            .setUsername(d.getString("username").orEmpty())
                            .setPassword(d.getString("credential").orEmpty())
                            .createIceServer()
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
        val base = porOmissao()
        return (personalizado ?: emptyList()) + base
    }
}
