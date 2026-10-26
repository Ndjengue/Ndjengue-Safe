package com.familia.controloparental.filho

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.servico.ServicoSincronizacao
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Telemóvel da criança: introduz o código de 6 dígitos gerado pelos pais.
 * A criança autentica-se de forma anónima; o convite dá-lhe acesso apenas à família certa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEmparelhamento(aoVoltar: () -> Unit, aoConcluir: () -> Unit) {
    val contexto = LocalContext.current
    var codigo by remember { mutableStateOf("") }
    var aEmparelhar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    fun emparelhar() {
        erro = null
        aEmparelhar = true
        escopo.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid
                    ?: auth.signInAnonymously().await().user?.uid
                    ?: throw IllegalStateException("Não foi possível identificar este telemóvel.")

                val repo = Ambiente.repo
                val convite = repo.lerConvite(codigo)
                if (convite == null) {
                    erro = "Código inválido ou expirado. Pede um novo código aos teus pais."
                    aEmparelhar = false
                    return@launch
                }
                repo.concluirEmparelhamento(convite, uid, "${Build.MANUFACTURER} ${Build.MODEL}")

                val cache = CacheLocal(contexto)
                cache.guardarIdentidade(convite.familiaId, convite.dispositivoId)
                repo.obterSegredoAdmin(convite.familiaId)?.let { cache.guardarSegredoAdmin(it.first, it.second) }

                ServicoSincronizacao.iniciar(contexto)
                aoConcluir()
            } catch (e: Exception) {
                erro = traduzirErro(e)
                aEmparelhar = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emparelhar telemóvel") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar, enabled = !aEmparelhar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.familia.controloparental.comum.IconeEmCirculo(androidx.compose.material.icons.Icons.Filled.Link, MaterialTheme.colorScheme.primary, 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Introduz o código dos teus pais", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "No telemóvel dos pais abre \"Adicionar filho\" e escolhe \"Gerar código\". O código dura 10 minutos.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = codigo,
                onValueChange = { novo -> codigo = novo.filter { it.isDigit() }.take(6) },
                label = { Text("Código de 6 dígitos") },
                singleLine = true,
                enabled = !aEmparelhar,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 10.sp),
                modifier = Modifier.fillMaxWidth()
            )
            erro?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { emparelhar() },
                enabled = codigo.length == 6 && !aEmparelhar,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (aEmparelhar) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("A emparelhar...")
                } else Text("Emparelhar")
            }
        }
    }
}
