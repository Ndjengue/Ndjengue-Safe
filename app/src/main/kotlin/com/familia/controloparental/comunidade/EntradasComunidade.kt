package com.familia.controloparental.comunidade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.pai.ContextoPai
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private suspend fun diasAtivos(): Int? = try {
    FirebaseFirestore.getInstance().collection("config").document("retencao").get().await().getLong("dias")?.toInt()
} catch (_: Exception) {
    null
}

@Composable
fun TelaComunidadePai(c: ContextoPai, aoVoltar: () -> Unit) {
    val utilizador = FirebaseAuth.getInstance().currentUser
    val dias by produceState<Int?>(null) { value = diasAtivos() }
    if (utilizador == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sessão inválida.") }
        return
    }
    val nome = utilizador.displayName?.takeIf { it.isNotBlank() } ?: utilizador.email?.substringBefore('@') ?: "Encarregado"
    val contactos = c.dispositivos
        .filter { it.emparelhado && it.uidAutenticacao.isNotBlank() }
        .map { Contacto(it.uidAutenticacao, it.nomeExibicao) }
    TelaComunidade(Identidade(utilizador.uid, nome, contactos, podeAmizade = true), dias, aoVoltar)
}

@Composable
fun TelaComunidadeFilho(aoVoltar: () -> Unit) {
    val ctx = LocalContext.current
    val leitura = remember { LeituraComunidade() }
    val dias by produceState<Int?>(null) { value = diasAtivos() }
    var identidade by remember { mutableStateOf<Identidade?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val cache = CacheLocal(ctx)
            identidade = leitura.identidadeCrianca(cache.lerFamiliaId().orEmpty(), cache.lerDispositivoId().orEmpty())
        } catch (e: Exception) {
            erro = e.message ?: "Erro ao abrir."
        }
    }
    val i = identidade
    if (i == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (erro != null) Text(erro!!) else CircularProgressIndicator()
        }
    } else {
        TelaComunidade(i, dias, aoVoltar)
    }
}
