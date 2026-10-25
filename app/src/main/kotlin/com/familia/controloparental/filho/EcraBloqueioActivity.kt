package com.familia.controloparental.filho

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Ecrã mostrado por cima de uma app bloqueada, com pedido de liberação aos pais. */
class EcraBloqueioActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHAVE = "chave"
        const val EXTRA_ROTULO = "rotulo"
        const val EXTRA_TIPO = "tipo"
        const val EXTRA_DETALHE = "detalhe"

        @Volatile var visivel: Boolean = false
    }

    private var chave by mutableStateOf("")
    private var rotulo by mutableStateOf("")
    private var tipo by mutableStateOf(TipoAlvo.APP)
    private var detalhe by mutableStateOf("")

    private fun lerIntent(i: Intent?) {
        chave = i?.getStringExtra(EXTRA_CHAVE) ?: ""
        rotulo = i?.getStringExtra(EXTRA_ROTULO) ?: ""
        tipo = try { TipoAlvo.valueOf(i?.getStringExtra(EXTRA_TIPO) ?: "APP") } catch (e: Exception) { TipoAlvo.APP }
        detalhe = i?.getStringExtra(EXTRA_DETALHE) ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lerIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = irParaInicio()
        })
        setContent {
            TemaControloParental {
                ConteudoBloqueio()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        visivel = true
    }

    override fun onStop() {
        visivel = false
        super.onStop()
    }

    private fun irParaInicio() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    @Composable
    private fun ConteudoBloqueio() {
        var duracao by remember { mutableIntStateOf(15) }
        var estado by remember { mutableStateOf("INICIAL") } // INICIAL, A_ENVIAR, ENVIADO
        val escopo = rememberCoroutineScope()
        val cache = remember { CacheLocal(applicationContext) }

        // Depois de enviado, fecha sozinho quando os pais aprovarem.
        LaunchedEffect(estado, chave) {
            if (estado == "ENVIADO") {
                while (true) {
                    delay(2_000)
                    val ate = cache.lerLiberacoes()[chave] ?: 0L
                    if (ate > System.currentTimeMillis()) {
                        finish()
                        break
                    }
                }
            }
        }

        Box(
            Modifier.fillMaxSize().fundoDeMarca().padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(84.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = AzulPrimario, modifier = Modifier.size(40.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (tipo == TipoAlvo.URL) "Site bloqueado" else "Bloqueado pelos teus pais",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(rotulo, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(detalhe, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))

                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (estado) {
                            "ENVIADO" -> {
                                Text("Pedido enviado ✔", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Esta janela fecha sozinha quando os teus pais aprovarem.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Text("Precisas de usar isto?", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("Pede aos teus pais por quanto tempo:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(15, 30, 60).forEach { m ->
                                        FilterChip(
                                            selected = duracao == m,
                                            onClick = { duracao = m },
                                            label = { Text(if (m == 60) "1 hora" else "$m min") }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    enabled = estado == "INICIAL",
                                    onClick = {
                                        estado = "A_ENVIAR"
                                        escopo.launch {
                                            val fid = cache.lerFamiliaId()
                                            val did = cache.lerDispositivoId()
                                            if (fid != null && did != null) {
                                                val nome = cache.lerTexto(Ambiente.CHAVE_NOME_CRIANCA) ?: ""
                                                // Se estiver offline o Firestore guarda o pedido e envia mais tarde.
                                                withTimeoutOrNull(4_000) {
                                                    try {
                                                        Ambiente.repo.criarPedido(
                                                            fid,
                                                            PedidoLiberacao(
                                                                dispositivoId = did, criancaNome = nome,
                                                                alvo = chave, rotulo = rotulo, tipoAlvo = tipo,
                                                                duracaoMinutos = duracao
                                                            )
                                                        )
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                            estado = "ENVIADO"
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (estado == "A_ENVIAR") "A enviar..." else "Pedir liberação") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { irParaInicio() }) {
                    Text("Voltar ao ecrã inicial", color = Color.White)
                }
            }
        }
    }
}
