package com.familia.controloparental.pai

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PassoForm { FORMULARIO, CODIGO, SUCESSO }

/**
 * Adicionar um filho (nome, telemóvel, e-mail, foto) e emparelhar com o telemóvel dele,
 * ou editar os dados de um filho já existente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFormularioFilho(
    c: ContextoPai,
    didExistente: String?,
    aoVoltar: () -> Unit,
    aoConcluir: () -> Unit
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val existente = didExistente?.let { c.dispositivo(it) }

    var nome by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var foto by remember { mutableStateOf<String?>(null) }
    var carregado by remember { mutableStateOf(false) }
    LaunchedEffect(existente?.id) {
        if (existente != null && !carregado) {
            nome = existente.crianca.nome
            telefone = existente.crianca.telefone
            email = existente.crianca.email
            foto = existente.crianca.fotoBase64
            carregado = true
        }
    }

    var erroNome by remember { mutableStateOf<String?>(null) }
    var erroTelefone by remember { mutableStateOf<String?>(null) }
    var erroEmail by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }

    var passo by remember { mutableStateOf(PassoForm.FORMULARIO) }
    var codigo by remember { mutableStateOf("") }
    var expiraEm by remember { mutableLongStateOf(0L) }
    var didNovo by remember { mutableStateOf<String?>(null) }

    val seletorFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            escopo.launch {
                val b64 = withContext(Dispatchers.IO) { Imagens.fotoDeUri(ctx, uri) }
                if (b64 != null) foto = b64 else erroGeral = "Não foi possível usar essa foto. Escolhe outra."
            }
        }
    }

    fun validar(): Boolean {
        erroNome = if (nome.isBlank()) "Indica o nome da criança." else null
        erroTelefone = validarTelefone(telefone)
        erroEmail = if (email.isBlank()) null else validarEmail(email)
        return erroNome == null && erroTelefone == null && erroEmail == null
    }

    fun perfil() = PerfilCrianca(nome.trim(), telefone.trim(), email.trim(), foto)

    fun gerarCodigo() {
        aCarregar = true
        erroGeral = null
        escopo.launch {
            try {
                val did = didNovo ?: c.repo.criarDispositivoPendente(c.fid, perfil()).also { didNovo = it }
                val (cod, exp) = c.repo.gerarConvite(c.fid, did)
                codigo = cod
                expiraEm = exp
                passo = PassoForm.CODIGO
            } catch (e: Exception) {
                erroGeral = traduzirErro(e)
            }
            aCarregar = false
        }
    }

    val cAtual by rememberUpdatedState(c)
    // Quando a criança concluir o emparelhamento, mostra o sucesso.
    LaunchedEffect(passo, didNovo) {
        if (passo == PassoForm.CODIGO) {
            while (true) {
                val d = didNovo?.let { cAtual.dispositivo(it) }
                if (d != null && d.emparelhado) {
                    passo = PassoForm.SUCESSO
                    break
                }
                delay(1_000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            didExistente != null -> "Editar perfil"
                            passo == PassoForm.CODIGO -> "Emparelhar"
                            else -> "Adicionar filho"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = passo,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200)))
            },
            modifier = Modifier.padding(padding),
            label = "passo"
        ) { estadoPasso ->
            when (estadoPasso) {
                PassoForm.FORMULARIO -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.clickable {
                        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        AvatarCrianca(foto, nome.ifBlank { "?" }, 108.dp)
                        Surface(
                            shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp).align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = "Escolher foto", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    TextButton(onClick = {
                        seletorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (foto == null) "Adicionar foto" else "Mudar foto") }
                    if (foto != null) TextButton(onClick = { foto = null }) { Text("Remover foto") }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nome, onValueChange = { nome = it.take(40); erroNome = null },
                        label = { Text("Nome da criança *") }, singleLine = true,
                        isError = erroNome != null, supportingText = { erroNome?.let { Text(it) } },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = telefone, onValueChange = { telefone = it.take(20); erroTelefone = null },
                        label = { Text("Nº de telemóvel") }, singleLine = true,
                        isError = erroTelefone != null, supportingText = { erroTelefone?.let { Text(it) } },
                        leadingIcon = { Icon(Icons.Filled.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.take(80); erroEmail = null },
                        label = { Text("E-mail (opcional)") }, singleLine = true,
                        isError = erroEmail != null, supportingText = { erroEmail?.let { Text(it) } },
                        leadingIcon = { Icon(Icons.Filled.Email, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    MensagemErro(erroGeral)
                    Spacer(Modifier.height(18.dp))

                    if (didExistente != null) {
                        Button(
                            enabled = !aCarregar,
                            onClick = {
                                if (!validar()) return@Button
                                aCarregar = true
                                escopo.launch {
                                    try {
                                        c.repo.atualizarPerfilCrianca(c.fid, didExistente, perfil())
                                        aoConcluir()
                                    } catch (e: Exception) {
                                        erroGeral = traduzirErro(e)
                                    }
                                    aCarregar = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text(if (aCarregar) "A guardar..." else "Guardar") }
                    } else {
                        Button(
                            enabled = !aCarregar,
                            onClick = { if (validar()) gerarCodigo() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.QrCode2, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (aCarregar) "A gerar..." else "Gerar código de emparelhamento")
                        }
                    }
                }

                PassoForm.CODIGO -> PassoCodigo(
                    nome = nome, codigo = codigo, expiraEm = expiraEm,
                    aoNovoCodigo = { gerarCodigo() },
                    aoCancelar = {
                        escopo.launch {
                            try {
                                if (codigo.isNotBlank()) c.repo.cancelarConvite(codigo)
                                didNovo?.let { c.repo.removerDispositivo(c.fid, it) }
                            } catch (_: Exception) {
                            }
                            aoVoltar()
                        }
                    }
                )

                PassoForm.SUCESSO -> Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = VerdeEsmeralda, modifier = Modifier.size(96.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("${nome.trim()} está ligado!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No telemóvel dele, segue os passos para ativar a proteção. Quando estiverem todos feitos, verás tudo no painel.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = aoConcluir, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Concluir") }
                }
            }
        }
    }
}

@Composable
private fun PassoCodigo(
    nome: String,
    codigo: String,
    expiraEm: Long,
    aoNovoCodigo: () -> Unit,
    aoCancelar: () -> Unit
) {
    val ctx = LocalContext.current
    var restanteMs by remember(expiraEm) { mutableLongStateOf(expiraEm - System.currentTimeMillis()) }
    LaunchedEffect(expiraEm) {
        while (restanteMs > 0) {
            delay(1_000)
            restanteMs = expiraEm - System.currentTimeMillis()
        }
    }
    val expirado = restanteMs <= 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Código para ${nome.trim()}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    codigo.chunked(3).joinToString("  "),
                    fontSize = 44.sp, style = MaterialTheme.typography.headlineLarge,
                    color = if (expirado) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                if (expirado) Text("Código expirado", color = MaterialTheme.colorScheme.error)
                else {
                    val s = (restanteMs / 1000).coerceAtLeast(0)
                    Text("Válido por %d:%02d".format(s / 60, s % 60), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(enabled = !expirado, onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Código de emparelhamento do $NOME_APP: $codigo")
                }
                ctx.startActivity(Intent.createChooser(i, "Partilhar código"))
            }) {
                Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Partilhar")
            }
            if (expirado) Button(onClick = aoNovoCodigo) { Text("Gerar novo código") }
        }

        Spacer(Modifier.height(22.dp))
        CartaoSecao("No telemóvel da criança") {
            listOf(
                "Instala a app $NOME_APP.",
                "Abre-a e escolhe \"Sou o dispositivo da criança\".",
                "Escreve o código acima e segue os passos de ativação."
            ).forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.White, fontSize = 13.sp) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("À espera que a criança introduza o código...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = aoCancelar) { Text("Cancelar e apagar") }
    }
}
