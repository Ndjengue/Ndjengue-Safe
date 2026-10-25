package com.familia.controloparental.pai

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.familia.controloparental.R
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.FirestoreRepositorio
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
private fun EstruturaAutenticacao(
    titulo: String,
    subtitulo: String,
    aoVoltar: () -> Unit,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().fundoDeMarca()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(onClick = aoVoltar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 10.dp, modifier = Modifier.size(78.dp)) {
                Image(painterResource(R.drawable.logo_app), contentDescription = null, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(titulo, color = Color.White, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitulo, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp), content = conteudo)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    erro: String? = null,
    soNumeros: Boolean = false
) {
    var visivel by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        isError = erro != null,
        supportingText = { if (erro != null) Text(erro) },
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (soNumeros) KeyboardType.NumberPassword else KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visivel = !visivel }) {
                Icon(
                    if (visivel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visivel) "Esconder" else "Mostrar"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CampoEmail(valor: String, aoMudar: (String) -> Unit, erro: String?) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text("E-mail") },
        singleLine = true,
        isError = erro != null,
        supportingText = { if (erro != null) Text(erro) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BotaoPrincipal(texto: String, aCarregar: Boolean, aoClicar: () -> Unit) {
    Button(onClick = aoClicar, enabled = !aCarregar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (aCarregar) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Aguarda...")
        } else Text(texto)
    }
}

// ---------------------------------------------------------------- Login

@Composable
fun TelaLoginPai(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erroEmail by remember { mutableStateOf<String?>(null) }
    var erroSenha by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    fun entrar() {
        erroEmail = validarEmail(email)
        erroSenha = if (senha.isEmpty()) "Indica a senha." else null
        erroGeral = null
        if (erroEmail != null || erroSenha != null) return
        aCarregar = true
        escopo.launch {
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), senha).await()
                escopo.launch { com.familia.controloparental.admin.LimpezaNoLogin.executar() }
                navController.navigate("pai") { popUpTo("escolha_modo") { inclusive = false } }
            } catch (e: Exception) {
                erroGeral = traduzirErro(e)
            }
            aCarregar = false
        }
    }

    EstruturaAutenticacao(
        titulo = "Bem-vindo de volta",
        subtitulo = "Entra para acompanhar a tua família",
        aoVoltar = { navController.popBackStack() }
    ) {
        CampoEmail(email, { email = it; erroEmail = null }, erroEmail)
        Spacer(Modifier.height(6.dp))
        CampoSenha(senha, { senha = it; erroSenha = null }, "Senha", erroSenha)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { navController.navigate("pai_recuperar") }) { Text("Esqueci a senha") }
        }
        MensagemErro(erroGeral)
        Spacer(Modifier.height(10.dp))
        BotaoPrincipal("Entrar", aCarregar) { entrar() }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navController.navigate("pai_registo") }, modifier = Modifier.fillMaxWidth()) {
            Text("Ainda não tenho conta. Criar conta")
        }
    }
}

// ---------------------------------------------------------------- Registo

@Composable
fun TelaRegistoPai(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmarPin by remember { mutableStateOf("") }
    var erroEmail by remember { mutableStateOf<String?>(null) }
    var erroSenha by remember { mutableStateOf<String?>(null) }
    var erroConfirmar by remember { mutableStateOf<String?>(null) }
    var erroPin by remember { mutableStateOf<String?>(null) }
    var erroConfirmarPin by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    fun criar() {
        erroEmail = validarEmail(email)
        erroSenha = validarSenhaConta(senha)
        erroConfirmar = if (confirmar != senha) "As senhas não coincidem." else null
        erroPin = validarPinAdmin(pin)
        erroConfirmarPin = if (confirmarPin != pin) "Os PINs não coincidem." else null
        erroGeral = null
        if (listOf(erroEmail, erroSenha, erroConfirmar, erroPin, erroConfirmarPin).any { it != null }) return
        aCarregar = true
        escopo.launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val uid = auth.createUserWithEmailAndPassword(email.trim(), senha).await().user?.uid
                    ?: throw IllegalStateException("Não foi possível criar a conta.")
                FirestoreRepositorio().criarFamilia(uid, email.trim(), pin)
                navController.navigate("pai") { popUpTo("escolha_modo") { inclusive = false } }
            } catch (e: Exception) {
                erroGeral = traduzirErro(e)
            }
            aCarregar = false
        }
    }

    EstruturaAutenticacao(
        titulo = "Criar conta dos pais",
        subtitulo = "Uma conta para toda a família",
        aoVoltar = { navController.popBackStack() }
    ) {
        CampoEmail(email, { email = it; erroEmail = null }, erroEmail)
        Spacer(Modifier.height(6.dp))
        CampoSenha(senha, { senha = it; erroSenha = null }, "Senha (mínimo 6 caracteres)", erroSenha)
        Spacer(Modifier.height(6.dp))
        CampoSenha(confirmar, { confirmar = it; erroConfirmar = null }, "Confirmar senha", erroConfirmar)
        Spacer(Modifier.height(14.dp))
        Text("PIN de administrador", style = MaterialTheme.typography.titleMedium)
        Text(
            "Este PIN (4 a 8 números) é pedido no telemóvel da criança para desinstalar a app ou mudar a proteção. Não o partilhes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        CampoSenha(pin, { pin = it.filter(Char::isDigit).take(8); erroPin = null }, "PIN de administrador", erroPin, soNumeros = true)
        Spacer(Modifier.height(6.dp))
        CampoSenha(confirmarPin, { confirmarPin = it.filter(Char::isDigit).take(8); erroConfirmarPin = null }, "Confirmar PIN", erroConfirmarPin, soNumeros = true)
        MensagemErro(erroGeral)
        Spacer(Modifier.height(16.dp))
        BotaoPrincipal("Criar conta", aCarregar) { criar() }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Já tenho conta. Entrar")
        }
    }
}

// ---------------------------------------------------------------- Recuperar senha

@Composable
fun TelaRecuperarSenha(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var erroEmail by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var enviado by remember { mutableStateOf(false) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    fun enviar() {
        erroEmail = validarEmail(email)
        erroGeral = null
        if (erroEmail != null) return
        aCarregar = true
        escopo.launch {
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
                enviado = true
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                // Não revelamos se o e-mail existe ou não.
                enviado = true
            } catch (e: Exception) {
                erroGeral = traduzirErro(e)
            }
            aCarregar = false
        }
    }

    EstruturaAutenticacao(
        titulo = "Recuperar senha",
        subtitulo = "Enviamos-te um link para criares uma nova senha",
        aoVoltar = { navController.popBackStack() }
    ) {
        if (enviado) {
            Text("Verifica o teu e-mail ✔", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Se existir uma conta com ${email.trim()}, vais receber um e-mail com as instruções. " +
                    "Confirma também a pasta de spam. O link serve para escolher uma nova senha.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Voltar ao início de sessão")
            }
        } else {
            CampoEmail(email, { email = it; erroEmail = null }, erroEmail)
            MensagemErro(erroGeral)
            Spacer(Modifier.height(14.dp))
            BotaoPrincipal("Enviar link de recuperação", aCarregar) { enviar() }
        }
    }
}
