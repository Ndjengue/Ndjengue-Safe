package com.familia.controloparental.comunidade

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.familia.controloparental.admin.ComunidadeRepositorio
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

/** Entrada da comunidade: pais e criança usam o mesmo ecrã com identidades diferentes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaComunidade(identidade: Identidade, diasRetencao: Int?, aoVoltar: () -> Unit) {
    var conversaAberta by remember { mutableStateOf<Conversa?>(null) }
    LaunchedEffect(identidade.nome) { com.familia.controloparental.chamadas.GestorChamadas.meuNome = identidade.nome }
    var aba by remember { mutableIntStateOf(0) }

    val c = conversaAberta
    if (c != null) {
        TelaChat(identidade, c, aoVoltar = { conversaAberta = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comunidade") },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = aba) {
                listOf("Conversas", "Publicações").forEachIndexed { i, t ->
                    Tab(selected = aba == i, onClick = { aba = i }, text = { Text(t) })
                }
            }
            Text(
                "As mensagens e publicações são eliminadas automaticamente" +
                    (if (diasRetencao != null) " ao fim de $diasRetencao dia(s)." else "."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when (aba) {
                0 -> AbaConversas(identidade, aoAbrir = { conversaAberta = it })
                else -> AbaPublicacoes(identidade)
            }
        }
    }
}

@Composable
private fun AbaConversas(identidade: Identidade, aoAbrir: (Conversa) -> Unit) {
    val leitura = remember { LeituraComunidade() }
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    val conversas by remember { leitura.observarConversas() }.collectAsState(initial = emptyList())
    var erro by remember { mutableStateOf<String?>(null) }
    var dialogoCodigo by remember { mutableStateOf<String?>(null) } // "gerar" ou "usar"
    var codigoGerado by remember { mutableStateOf<String?>(null) }
    var codigoTexto by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }

    fun outro(c: Conversa): Pair<String, String> {
        val uidOutro = c.participantes.firstOrNull { it != identidade.uid } ?: ""
        val nome = identidade.contactos.firstOrNull { it.uid == uidOutro }?.nome ?: c.nomes[uidOutro] ?: "Conversa"
        return uidOutro to nome
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MensagemErro(erro) }
        val comConversa = conversas.map { outro(it).first }.toSet()
        val semConversa = identidade.contactos.filter { it.uid !in comConversa }
        if (semConversa.isNotEmpty()) {
            item { Text("Iniciar conversa", style = MaterialTheme.typography.titleMedium) }
            items(semConversa, key = { it.uid }) { ct ->
                OutlinedButton(
                    enabled = !ocupado, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        ocupado = true
                        escopo.launch {
                            try {
                                repo.criarConversa(listOf(ct.uid), mapOf(identidade.uid to identidade.nome, ct.uid to ct.nome))
                            } catch (e: Exception) {
                                erro = traduzirErro(e)
                            }
                            ocupado = false
                        }
                    }
                ) { Text("Falar com ${ct.nome}") }
            }
        }
        if (identidade.podeAmizade) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        dialogoCodigo = "gerar"; codigoGerado = null
                        escopo.launch {
                            try {
                                codigoGerado = leitura.gerarCodigoAmizade(identidade.nome)
                            } catch (e: Exception) {
                                erro = traduzirErro(e); dialogoCodigo = null
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("O meu código") }
                    OutlinedButton(onClick = { dialogoCodigo = "usar"; codigoTexto = "" }, modifier = Modifier.weight(1f)) {
                        Text("Usar código")
                    }
                }
            }
        }
        item { Text("Conversas", style = MaterialTheme.typography.titleMedium) }
        if (conversas.isEmpty()) {
            item {
                EstadoVazio(Icons.Filled.ChatBubbleOutline, "Sem conversas", "Inicie uma conversa para começar.")
            }
        }
        items(conversas, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth().clickable { aoAbrir(c) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarCrianca(null, outro(c).second, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(outro(c).second, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    when (dialogoCodigo) {
        "gerar" -> AlertDialog(
            onDismissRequest = { dialogoCodigo = null },
            confirmButton = { TextButton(onClick = { dialogoCodigo = null }) { Text("Fechar") } },
            title = { Text("O meu código de amizade") },
            text = {
                if (codigoGerado == null) CircularProgressIndicator()
                else Column {
                    Text(codigoGerado!!, style = MaterialTheme.typography.headlineMedium)
                    Text("Válido durante 15 minutos. Dê-o à pessoa com quem quer falar.")
                }
            }
        )
        "usar" -> AlertDialog(
            onDismissRequest = { dialogoCodigo = null },
            confirmButton = {
                TextButton(enabled = codigoTexto.length == 6 && !ocupado, onClick = {
                    ocupado = true
                    escopo.launch {
                        try {
                            leitura.usarCodigoAmizade(codigoTexto, identidade.nome)
                            dialogoCodigo = null
                        } catch (e: Exception) {
                            erro = traduzirErro(e); dialogoCodigo = null
                        }
                        ocupado = false
                    }
                }) { Text("Ligar") }
            },
            dismissButton = { TextButton(onClick = { dialogoCodigo = null }) { Text("Cancelar") } },
            title = { Text("Código de amizade") },
            text = {
                OutlinedTextField(
                    value = codigoTexto, singleLine = true, label = { Text("6 dígitos") },
                    onValueChange = { codigoTexto = it.filter(Char::isDigit).take(6) }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelaChat(identidade: Identidade, conversa: Conversa, aoVoltar: () -> Unit) {
    val leitura = remember { LeituraComunidade() }
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    val mensagens by remember(conversa.id) { leitura.observarMensagens(conversa.id) }.collectAsState(initial = emptyList())
    var texto by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    val lista = rememberLazyListState()
    val uidOutro = conversa.participantes.firstOrNull { it != identidade.uid } ?: ""
    val titulo = identidade.contactos.firstOrNull { it.uid == uidOutro }?.nome ?: conversa.nomes[uidOutro] ?: "Conversa"

    LaunchedEffect(mensagens.size) { if (mensagens.isNotEmpty()) lista.animateScrollToItem(mensagens.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(uidOutro, titulo, false, identidade.nome) }) {
                        Icon(Icons.Filled.Call, "Chamada de voz")
                    }
                    IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(uidOutro, titulo, true, identidade.nome) }) {
                        Icon(Icons.Filled.Videocam, "Videochamada")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(mensagens, key = { it.id }) { m ->
                    val minha = m.autorUid == identidade.uid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (minha) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (minha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (minha) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(m.texto)
                                Text(formatarDataHora(m.criadoEm), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            MensagemErro(erro)
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it.take(1000) }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Mensagem") }, maxLines = 4
                )
                IconButton(enabled = texto.isNotBlank(), onClick = {
                    val t = texto.trim()
                    texto = ""
                    escopo.launch {
                        try {
                            repo.enviarMensagem(conversa.id, t)
                            erro = null
                        } catch (e: Exception) {
                            erro = traduzirErro(e); texto = t
                        }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
            }
        }
    }
}

@Composable
private fun AbaPublicacoes(identidade: Identidade) {
    val leitura = remember { LeituraComunidade() }
    val publicacoes by remember { leitura.observarPublicacoes() }.collectAsState(initial = emptyList())
    var nova by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { nova = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova publicação") } }
        if (publicacoes.isEmpty()) item { EstadoVazio(Icons.Filled.Image, "Sem publicações", "Partilhe algo educativo.") }
        items(publicacoes, key = { it.id }) { p -> CartaoPublicacao(p, identidade, leitura) }
    }
    if (nova) DialogoNovaPublicacao(identidade, aoFechar = { nova = false })
}

@Composable
private fun CartaoPublicacao(p: Publicacao, identidade: Identidade, leitura: LeituraComunidade) {
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    val reacoes by remember(p.id) { leitura.observarReacoes(p.id) }.collectAsState(initial = emptyList())
    val comentarios by remember(p.id) { leitura.observarComentarios(p.id) }.collectAsState(initial = emptyList())
    var abrir by remember { mutableStateOf(false) }
    var novo by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    val gostei = identidade.uid in reacoes
    val bmp = remember(p.imagemBase64) { Imagens.deBase64(p.imagemBase64) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.autorNome.ifBlank { "Utilizador" }, style = MaterialTheme.typography.titleSmall)
            Text(formatarDataHora(p.criadoEm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.texto.isNotBlank()) Text(p.texto)
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Crop)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !gostei, onClick = {
                    escopo.launch { try { repo.reagir(p.id) } catch (e: Exception) { erro = traduzirErro(e) } }
                }) {
                    Icon(if (gostei) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null)
                    Spacer(Modifier.width(6.dp)); Text("${reacoes.size}")
                }
                TextButton(onClick = { abrir = !abrir }) {
                    Icon(Icons.Filled.ChatBubbleOutline, null); Spacer(Modifier.width(6.dp)); Text("${comentarios.size}")
                }
            }
            MensagemErro(erro)
            if (abrir) {
                comentarios.forEach { c ->
                    Text("${c.autorNome.ifBlank { "Utilizador" }}: ${c.texto}", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = novo, onValueChange = { novo = it.take(500) }, modifier = Modifier.weight(1f), placeholder = { Text("Comentar") }, singleLine = true)
                    IconButton(enabled = novo.isNotBlank(), onClick = {
                        val t = novo.trim(); novo = ""
                        escopo.launch { try { repo.comentar(p.id, t, identidade.nome); erro = null } catch (e: Exception) { erro = traduzirErro(e) } }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
                }
            }
        }
    }
}

@Composable
private fun DialogoNovaPublicacao(identidade: Identidade, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var imagem by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val escolher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) imagem = Imagens.fotoDeUri(ctx, uri, lado = 480, qualidade = 70)
    }
    AlertDialog(
        onDismissRequest = { if (!ocupado) aoFechar() },
        title = { Text("Nova publicação") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = texto, onValueChange = { texto = it.take(1000) }, label = { Text("Texto") }, minLines = 3)
                OutlinedButton(onClick = { escolher.launch("image/*") }) { Text(if (imagem == null) "Juntar imagem" else "Imagem juntada ✓") }
                MensagemErro(erro)
            }
        },
        confirmButton = {
            TextButton(enabled = !ocupado && (texto.isNotBlank() || imagem != null), onClick = {
                ocupado = true
                escopo.launch {
                    try {
                        repo.criarPublicacao(texto.trim(), imagem, null, identidade.nome)
                        aoFechar()
                    } catch (e: Exception) {
                        erro = traduzirErro(e)
                    }
                    ocupado = false
                }
            }) { Text("Publicar") }
        },
        dismissButton = { TextButton(enabled = !ocupado, onClick = aoFechar) { Text("Cancelar") } }
    )
}
