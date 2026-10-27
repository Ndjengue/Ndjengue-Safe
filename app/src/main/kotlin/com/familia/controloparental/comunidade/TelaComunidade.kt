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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    if (GestorChat.abrirConversaId != null && aba != 0) aba = 0

    // Presença: "aparecer offline" é só da criança; guardado neste telemóvel e enviado em pulsos de 30 s.
    val ctxP = LocalContext.current
    val prefsP = remember { ctxP.getSharedPreferences("presenca", android.content.Context.MODE_PRIVATE) }
    var aparecerOffline by remember { mutableStateOf(prefsP.getBoolean("offline", false) && identidade.ehCrianca) }
    LaunchedEffect(aparecerOffline) {
        val repoP = ComunidadeRepositorio()
        while (true) {
            try { repoP.atualizarPresenca(!aparecerOffline) } catch (_: Exception) {}
            delay(30_000)
        }
    }
    val alterarOffline: (Boolean) -> Unit = { v ->
        aparecerOffline = v
        prefsP.edit().putBoolean("offline", v).apply()
    }

    val c = conversaAberta
    if (c != null) {
        TelaChat(identidade, c, aparecerOffline, alterarOffline, aoVoltar = { conversaAberta = null })
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
            if (identidade.ehCrianca) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Aparecer offline", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Os teus amigos deixam de ver quando estás online. Os teus pais continuam a ver o estado real.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = aparecerOffline, onCheckedChange = alterarOffline)
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
        return uidOutro to nomeOutro(identidade, c)
    }
    val grupos = remember(conversas, identidade) { agrupar(identidade, conversas) }
    // Toque numa notificação de mensagem: abre a conversa certa.
    val pedido = GestorChat.abrirConversaId
    LaunchedEffect(pedido, grupos) {
        if (pedido != null) {
            grupos.firstOrNull { pedido in it.todosIds }?.let { aoAbrir(it); GestorChat.abrirConversaId = null }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MensagemErro(erro) }
        val comConversa = grupos.map { nomeOutro(identidade, it).trim().lowercase() }.toSet()
        val semConversa = identidade.contactos.filter { it.nome.trim().lowercase() !in comConversa }
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
        if (grupos.isEmpty()) {
            item {
                EstadoVazio(Icons.Filled.ChatBubbleOutline, "Sem conversas", "Inicie uma conversa para começar.")
            }
        }
        items(grupos, key = { it.id }) { c ->
            val nome = outro(c).second
            Row(
                Modifier.fillMaxWidth().clickable { aoAbrir(c) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCrianca(null, nome, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, fontWeight = if (c.naoLida) androidx.compose.ui.text.font.FontWeight.Bold else null)
                    Text(
                        when {
                            c.ultimaTexto.isBlank() -> "Toque para conversar"
                            c.ultimaAutor == identidade.uid -> "Tu: ${c.ultimaTexto}"
                            else -> c.ultimaTexto
                        },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (c.naoLida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        rotuloHora(c.ultimaEm), style = MaterialTheme.typography.labelSmall,
                        color = if (c.naoLida) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (c.naoLida) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.secondary))
                    }
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


/** Nome da outra pessoa de uma conversa (contacto conhecido ou nome guardado na conversa). */
private fun nomeOutro(identidade: Identidade, c: Conversa): String {
    val uidOutro = c.participantes.firstOrNull { it != identidade.uid } ?: ""
    return identidade.contactos.firstOrNull { it.uid == uidOutro }?.nome ?: c.nomes[uidOutro] ?: "Conversa"
}

/**
 * Como no WhatsApp: uma só linha por pessoa. Conversas com a mesma pessoa (ex.: telemóvel da criança
 * reinstalado, com outro identificador) juntam-se; as mensagens de todas aparecem no mesmo chat.
 */
private fun agrupar(identidade: Identidade, conversas: List<Conversa>): List<Conversa> {
    val uidsContacto = identidade.contactos.map { it.uid }.toSet()
    return conversas.groupBy { nomeOutro(identidade, it).trim().lowercase() }.values.map { g ->
        val preferida = g.firstOrNull { c -> c.participantes.any { it in uidsContacto } }
            ?: g.maxByOrNull { maxOf(it.ultimaEm, it.criadoEm) }!!
        val ultima = g.maxByOrNull { it.ultimaEm }!!
        val naoLida = g.any { c ->
            c.ultimaEm > 0 && c.ultimaAutor.isNotBlank() && c.ultimaAutor != identidade.uid &&
                c.ultimaEm > (c.lidoEm[identidade.uid] ?: 0L)
        }
        preferida.copy(
            ultimaTexto = ultima.ultimaTexto, ultimaEm = ultima.ultimaEm, ultimaAutor = ultima.ultimaAutor,
            todosIds = g.map { it.id }, naoLida = naoLida
        )
    }.sortedByDescending { maxOf(it.ultimaEm, it.criadoEm) }
}

private fun rotuloHora(ms: Long): String {
    if (ms <= 0) return ""
    return when (val r = rotuloDia(ms)) {
        "Hoje" -> formatarHora(ms)
        else -> r
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
private fun TelaChat(identidade: Identidade, conversa: Conversa, aparecerOffline: Boolean, aoAlterarOffline: (Boolean) -> Unit, aoVoltar: () -> Unit) {
    val leitura = remember { LeituraComunidade() }
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    val idsConversa = remember(conversa) { conversa.todosIds.ifEmpty { listOf(conversa.id) } }
    val mensagens by remember(idsConversa) { leitura.observarMensagens(idsConversa) }.collectAsState(initial = emptyList())
    val todasConversas by remember { leitura.observarConversas() }.collectAsState(initial = emptyList())
    val destinos = remember(todasConversas, identidade) { agrupar(identidade, todasConversas) }
    var reencaminhar by remember { mutableStateOf<MensagemChat?>(null) }
    var imagemPendente by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mensagens.size) { repo.marcarLidas(idsConversa) }
    var texto by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var respondendo by remember { mutableStateOf<MensagemChat?>(null) }
    var editando by remember { mutableStateOf<MensagemChat?>(null) }
    var menuTopo by remember { mutableStateOf(false) }
    val ctxChat = LocalContext.current
    val escolherImagem = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val b64 = Imagens.fotoDeUri(ctxChat, uri, lado = 800, qualidade = 70)
            if (b64 == null || b64.length > ComunidadeRepositorio.MAX_ANEXO_BASE64) erro = "A imagem é demasiado grande." else imagemPendente = b64
        }
    }
    val lista = rememberLazyListState()
    val uidOutro = conversa.participantes.firstOrNull { it != identidade.uid } ?: ""
    val titulo = identidade.contactos.firstOrNull { it.uid == uidOutro }?.nome ?: conversa.nomes[uidOutro] ?: "Conversa"
    val presenca by remember(uidOutro) { leitura.observarPresenca(uidOutro) }.collectAsState(initial = null)
    val agoraP by produceState(System.currentTimeMillis()) { while (true) { delay(15_000); value = System.currentTimeMillis() } }
    val subtitulo: String = run {
        val real = identidade.estadoReal[uidOutro]
        val p = presenca
        if (real != null) real + if (p != null && !p.visivel) " · offline para os amigos" else ""
        else if (p == null || !p.visivel || p.ultimoEm <= 0) "chamadas cifradas · não gravadas"
        else if (agoraP - p.ultimoEm < 75_000) "online"
        else "visto ${tempoDecorrido(p.ultimoEm, agoraP)}"
    }

    LaunchedEffect(mensagens.size) { if (mensagens.isNotEmpty()) lista.animateScrollToItem(lista.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    val corPapel = MaterialTheme.colorScheme.background
    val corPonto = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    reencaminhar?.let { m ->
        DialogoReencaminhar(m, destinos, identidade, aoFechar = { reencaminhar = null }, aoEnviado = { reencaminhar = null })
    }

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
                            Text(subtitulo, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
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
                    if (identidade.ehCrianca) {
                        IconButton(onClick = { menuTopo = true }) { Icon(Icons.Filled.MoreVert, "Mais") }
                        DropdownMenu(expanded = menuTopo, onDismissRequest = { menuTopo = false }) {
                            DropdownMenuItem(
                                text = { Text(if (aparecerOffline) "Voltar a aparecer online" else "Aparecer offline") },
                                onClick = { menuTopo = false; aoAlterarOffline(!aparecerOffline) }
                            )
                        }
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
                    BalaoMensagem(
                        m, minha = m.autorUid == identidade.uid, primeiraDoGrupo = primeiraDoGrupo,
                        nomeDe = { uid -> if (uid == identidade.uid) "Tu" else titulo },
                        aoResponder = { respondendo = m; editando = null },
                        aoEditar = { editando = m; respondendo = null; texto = m.texto },
                        aoReencaminhar = { reencaminhar = m }
                    )
                }
            }
            MensagemErro(erro)
            val alvoBanner = editando ?: respondendo
            if (alvoBanner != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 4.dp),
                    shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editando != null) "Editar mensagem" else if (alvoBanner.autorUid == identidade.uid) "Tu" else titulo,
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary
                            )
                            Text(alvoBanner.texto, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (editando != null) texto = ""; editando = null; respondendo = null }) { Icon(Icons.Filled.Close, "Cancelar") }
                    }
                }
            }
            imagemPendente?.let { b64 ->
                val bmpP = remember(b64) { Imagens.deBase64(b64) }
                if (bmpP != null) {
                    Row(Modifier.padding(horizontal = 8.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(bmpP.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Text("Imagem anexada", Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { imagemPendente = null }) { Icon(Icons.Filled.Close, "Remover anexo") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Surface(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp
                ) {
                    TextField(
                        value = texto, onValueChange = { texto = it.take(1000) },
                        placeholder = { Text("Mensagem") }, maxLines = 5,
                        trailingIcon = {
                            IconButton(onClick = { escolherImagem.launch("image/*") }) {
                                Icon(Icons.Filled.AttachFile, "Anexar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    enabled = texto.isNotBlank() || (imagemPendente != null && editando == null), modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.outline, disabledContentColor = Color.White
                    ),
                    onClick = {
                        val t = texto.trim()
                        val emEdicao = editando
                        val emResposta = respondendo
                        val imagemEnviar = imagemPendente
                        texto = ""; editando = null; respondendo = null; imagemPendente = null
                        escopo.launch {
                            try {
                                if (emEdicao != null) {
                                    if (t != emEdicao.texto) repo.editarMensagem(emEdicao.id, t)
                                } else {
                                    repo.enviarMensagem(conversa.id, t, null, emResposta?.autorUid, emResposta?.texto, imagemEnviar)
                                }
                                erro = null
                            } catch (e: Exception) {
                                erro = if (emEdicao != null) "Não foi possível editar (só até 15 minutos depois de enviar)." else traduzirErro(e)
                                texto = t; imagemPendente = imagemEnviar
                            }
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
            }
        }
    }
}

@Composable
private fun DialogoReencaminhar(
    mensagem: MensagemChat, destinos: List<Conversa>, identidade: Identidade,
    aoFechar: () -> Unit, aoEnviado: () -> Unit
) {
    val repo = remember { ComunidadeRepositorio() }
    val escopo = rememberCoroutineScope()
    var escolhidos by remember { mutableStateOf(setOf<String>()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!ocupado) aoFechar() },
        title = { Text("Reencaminhar para…") },
        text = {
            Column {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (mensagem.texto.isNotBlank()) mensagem.texto else "📷 Imagem", maxLines = 2,
                        modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (destinos.isEmpty()) Text("Ainda não tens outras conversas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(destinos, key = { it.id }) { d ->
                        val marcado = d.id in escolhidos
                        Row(
                            Modifier.fillMaxWidth().clickable { escolhidos = if (marcado) escolhidos - d.id else escolhidos + d.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = marcado, onCheckedChange = { escolhidos = if (marcado) escolhidos - d.id else escolhidos + d.id })
                            AvatarCrianca(null, nomeOutro(identidade, d), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(nomeOutro(identidade, d), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                MensagemErro(erro)
            }
        },
        confirmButton = {
            TextButton(enabled = escolhidos.isNotEmpty() && !ocupado, onClick = {
                ocupado = true
                escopo.launch {
                    try {
                        escolhidos.forEach { id ->
                            repo.enviarMensagem(
                                id, mensagem.texto, null, null, null,
                                mensagem.imagemBase64.ifBlank { null }, reencaminhada = true
                            )
                        }
                        aoEnviado()
                    } catch (e: Exception) {
                        erro = traduzirErro(e)
                    }
                    ocupado = false
                }
            }) { Text(if (ocupado) "A enviar…" else "Enviar") }
        },
        dismissButton = { TextButton(enabled = !ocupado, onClick = aoFechar) { Text("Cancelar") } }
    )
}

private const val JANELA_EDICAO_MS = 15 * 60_000L

@Composable
private fun BalaoMensagem(
    m: MensagemChat, minha: Boolean, primeiraDoGrupo: Boolean,
    nomeDe: (String) -> String, aoResponder: () -> Unit, aoEditar: () -> Unit, aoReencaminhar: () -> Unit
) {
    val forma = RoundedCornerShape(
        topStart = if (!minha && primeiraDoGrupo) 4.dp else 16.dp,
        topEnd = if (minha && primeiraDoGrupo) 4.dp else 16.dp,
        bottomStart = 16.dp, bottomEnd = 16.dp
    )
    var menu by remember { mutableStateOf(false) }
    var deslocamento by remember { mutableFloatStateOf(0f) }
    val anim by animateFloatAsState(deslocamento, label = "deslize")
    val limite = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val area = LocalClipboardManager.current
    val podeEditar = minha && m.criadoEm > 0 && System.currentTimeMillis() - m.criadoEm < JANELA_EDICAO_MS

    Box(Modifier.fillMaxWidth().padding(top = if (primeiraDoGrupo) 6.dp else 2.dp)) {
        if (anim > 12f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(22.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().offset { IntOffset(anim.roundToInt(), 0) }
                .pointerInput(m.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (deslocamento >= limite) aoResponder()
                            deslocamento = 0f
                        },
                        onDragCancel = { deslocamento = 0f },
                        onHorizontalDrag = { _, d -> deslocamento = (deslocamento + d).coerceIn(0f, limite * 1.3f) }
                    )
                }
                .pointerInput(m.id) { detectTapGestures(onLongPress = { menu = true }) },
            horizontalArrangement = if (minha) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = forma, shadowElevation = 1.dp,
                color = if (minha) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (minha) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 5.dp)) {
                    if (m.respostaAutorUid.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp), color = LocalContentColor.current.copy(alpha = 0.08f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                                Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(nomeDe(m.respostaAutorUid), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Text(m.respostaTexto, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
                                }
                            }
                        }
                    }
                    if (m.reencaminhada) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp), tint = LocalContentColor.current.copy(alpha = 0.6f))
                            Spacer(Modifier.width(4.dp))
                            Text("Reencaminhada", style = MaterialTheme.typography.labelSmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = LocalContentColor.current.copy(alpha = 0.6f))
                        }
                    }
                    if (m.imagemBase64.isNotBlank()) {
                        val bmpM = remember(m.imagemBase64) { Imagens.deBase64(m.imagemBase64) }
                        if (bmpM != null) {
                            Image(bmpM.asImageBitmap(), null, Modifier.padding(bottom = 4.dp).widthIn(max = 260.dp).heightIn(max = 300.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                        }
                    }
                    if (m.texto.isNotBlank()) Text(m.texto, style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        if (m.editadoEm > 0) {
                            Text("editada  ", style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                        }
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
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Responder") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                onClick = { menu = false; aoResponder() }
            )
            DropdownMenuItem(
                text = { Text("Copiar") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menu = false; if (m.texto.isNotBlank()) area.setText(AnnotatedString(m.texto)) }
            )
            DropdownMenuItem(
                text = { Text("Reencaminhar") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                onClick = { menu = false; aoReencaminhar() }
            )
            if (podeEditar) {
                DropdownMenuItem(
                    text = { Text("Editar (até 15 min)") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; aoEditar() }
                )
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
                OutlinedButton(onClick = { escolher.launch("image/*") }) { Text(if (imagem == null) "Anexar imagem" else "Imagem anexada ✓") }
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
