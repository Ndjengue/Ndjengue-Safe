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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Done
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
            Row(
                Modifier.fillMaxWidth().clickable { aoAbrir(c) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCrianca(null, outro(c).second, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(outro(c).second, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("Toque para conversar ou ligar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(outro(c).first, outro(c).second, false, identidade.nome) }) {
                    Icon(Icons.Filled.Call, "Ligar", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(outro(c).first, outro(c).second, true, identidade.nome) }) {
                    Icon(Icons.Filled.Videocam, "Videochamada", tint = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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

private fun rotuloDia(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val hoje = java.util.Calendar.getInstance()
    fun mesmoDia(x: java.util.Calendar, y: java.util.Calendar) =
        x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR) && x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR)
    if (mesmoDia(c, hoje)) return "Hoje"
    hoje.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (mesmoDia(c, hoje)) return "Ontem"
    return java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "PT")).format(java.util.Date(ms))
}

private fun diaDe(ms: Long): Int {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
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

    LaunchedEffect(mensagens.size) { if (mensagens.isNotEmpty()) lista.animateScrollToItem(lista.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    val corPapel = MaterialTheme.colorScheme.background
    val corPonto = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCrianca(null, titulo, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("chamadas cifradas · não gravadas", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(uidOutro, titulo, true, identidade.nome) }) {
                        Icon(Icons.Filled.Videocam, "Videochamada")
                    }
                    IconButton(onClick = { com.familia.controloparental.chamadas.GestorChamadas.ligar(uidOutro, titulo, false, identidade.nome) }) {
                        Icon(Icons.Filled.Call, "Chamada de voz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding()
                .background(corPapel)
                .drawBehind {
                    // padrão discreto de pontos, como o papel de parede do WhatsApp, nas cores da marca
                    val passo = 36.dp.toPx()
                    var y = passo / 2
                    var linha = 0
                    while (y < size.height) {
                        var x = if (linha % 2 == 0) passo / 2 else passo
                        while (x < size.width) {
                            drawCircle(corPonto, radius = 2.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            x += passo
                        }
                        y += passo; linha++
                    }
                }
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)) {
                            Text(
                                "🔒 As mensagens são eliminadas automaticamente. Ninguém grava as chamadas.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                itemsIndexed(mensagens, key = { _, m -> m.id }) { i, m ->
                    val anterior = mensagens.getOrNull(i - 1)
                    val novoDia = anterior == null || diaDe(anterior.criadoEm) != diaDe(m.criadoEm)
                    val primeiraDoGrupo = novoDia || anterior?.autorUid != m.autorUid
                    if (novoDia) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                                Text(
                                    rotuloDia(m.criadoEm), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    BalaoMensagem(m, minha = m.autorUid == identidade.uid, primeiraDoGrupo = primeiraDoGrupo)
                }
            }
            MensagemErro(erro)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Surface(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp
                ) {
                    TextField(
                        value = texto, onValueChange = { texto = it.take(1000) },
                        placeholder = { Text("Mensagem") }, maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    enabled = texto.isNotBlank(), modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.outline, disabledContentColor = Color.White
                    ),
                    onClick = {
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
                    }
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
            }
        }
    }
}

@Composable
private fun BalaoMensagem(m: MensagemChat, minha: Boolean, primeiraDoGrupo: Boolean) {
    val forma = RoundedCornerShape(
        topStart = if (!minha && primeiraDoGrupo) 4.dp else 16.dp,
        topEnd = if (minha && primeiraDoGrupo) 4.dp else 16.dp,
        bottomStart = 16.dp, bottomEnd = 16.dp
    )
    Row(
        Modifier.fillMaxWidth().padding(top = if (primeiraDoGrupo) 6.dp else 2.dp),
        horizontalArrangement = if (minha) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = forma, shadowElevation = 1.dp,
            color = if (minha) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (minha) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 5.dp)) {
                Text(m.texto, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatarHora(m.criadoEm), style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                    if (minha) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.Done, "Enviada", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
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
