package com.familia.controloparental.pai

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familia.controloparental.servico.AlertaSos
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import com.familia.controloparental.servico.ServicoMonitorPai
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private fun ecraInteiroPermitido(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 34)
        (ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).canUseFullScreenIntent()
    else true

@Composable
private fun LinhaDefinicao(icone: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, descricao: String, aoClicar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoClicar).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconeEmCirculo(icone, MaterialTheme.colorScheme.primary, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDefinicoes(c: ContextoPai, aoSair: () -> Unit, aoAbrirSobre: () -> Unit, aoAbrirAdmin: () -> Unit = {}, aoAbrirComunidade: () -> Unit = {}) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val utilizador = FirebaseAuth.getInstance().currentUser
    var monitorAtivo by remember { mutableStateOf(ServicoMonitorPai.ativo(ctx)) }
    var mostrarPin by remember { mutableStateOf(false) }
    var sobrepor by remember { mutableStateOf(AlertaSos.podeSobrepor(ctx)) }
    var ecraInteiro by remember { mutableStateOf(ecraInteiroPermitido(ctx)) }
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                sobrepor = AlertaSos.podeSobrepor(ctx); ecraInteiro = ecraInteiroPermitido(ctx)
            }
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    var mensagem by remember { mutableStateOf<String?>(null) }

    var superAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { superAdmin = com.familia.controloparental.admin.RetencaoRepositorio().ehSuperAdmin() }

    val lancadorNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(topBar = { TopAppBar(title = { Text("Definições") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CartaoSecao("Conta") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    AvatarCrianca(null, utilizador?.email.orEmpty().ifBlank { "Pai" }, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(utilizador?.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("Conta dos pais", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                LinhaDefinicao(Icons.Filled.LockReset, "Alterar senha da conta", "Enviamos um e-mail com um link seguro") {
                    val email = utilizador?.email
                    if (email != null) escopo.launch {
                        mensagem = try {
                            FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
                            "Enviámos um e-mail para $email."
                        } catch (e: Exception) { traduzirErro(e) }
                    }
                }
                LinhaDefinicao(Icons.Filled.Pin, "Alterar PIN de administrador", "Também serve se te esqueceste do PIN") { mostrarPin = true }
            }

            CartaoSecao("Notificações") {
                LinhaInterruptor(
                    Icons.Filled.NotificationsActive, "Avisar-me neste telemóvel",
                    "Notificações de pedidos, SOS e tentativas de desinstalação, mesmo com a app fechada.",
                    monitorAtivo, { v ->
                        monitorAtivo = v
                        if (v && Build.VERSION.SDK_INT >= 33) lancadorNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        ServicoMonitorPai.definirAtivo(ctx, v)
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                LinhaDefinicao(
                    Icons.Filled.Sos, "SOS por cima de outras apps",
                    if (sobrepor) "Ativo: o pedido de ajuda aparece no ecrã, mesmo a usar outra app."
                    else "Desligado. Toca para permitir que o SOS da criança apareça por cima de tudo."
                ) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                }
                if (!ecraInteiro) {
                    LinhaDefinicao(
                        Icons.Filled.ScreenLockPortrait, "Alertas em ecrã inteiro",
                        "Desligado. Toca para permitir que o SOS acenda o ecrã bloqueado."
                    ) {
                        try {
                            ctx.startActivity(
                                Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {}
                    }
                }
            }

            if (superAdmin) {
                CartaoSecao("Administração") {
                    LinhaDefinicao(Icons.Filled.AdminPanelSettings, "Painel do Super Administrador", "Política de retenção, limpeza e auditoria", aoAbrirAdmin)
                }
            }

            CartaoSecao("Comunidade") {
                LinhaDefinicao(Icons.Filled.Forum, "Conversas e publicações", "Mensagens com a família e partilhas, com eliminação automática", aoAbrirComunidade)
            }

            CartaoSecao("Aplicação") {
                LinhaDefinicao(Icons.Filled.Info, "Sobre o $NOME_APP", "Versão, autor e direitos de autor", aoAbrirSobre)
                LinhaDefinicao(Icons.AutoMirrored.Filled.Logout, "Terminar sessão", "Sair desta conta neste telemóvel") {
                    ServicoMonitorPai.parar(ctx)
                    FirebaseAuth.getInstance().signOut()
                    aoSair()
                }
            }
            if (mensagem != null) Text(mensagem!!, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (mostrarPin) DialogoAlterarPin(c, aoFechar = { mostrarPin = false }, aoSucesso = { mensagem = "PIN alterado com sucesso."; mostrarPin = false })
}

@Composable
private fun DialogoAlterarPin(c: ContextoPai, aoFechar: () -> Unit, aoSucesso: () -> Unit) {
    var senha by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!aCarregar) aoFechar() },
        title = { Text("Alterar PIN de administrador") },
        text = {
            Column {
                Text("Por segurança, confirma a senha da tua conta. Depois escolhe o novo PIN (4 a 8 números).")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = senha, onValueChange = { senha = it; erro = null }, label = { Text("Senha da conta") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8); erro = null }, label = { Text("Novo PIN") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmar, onValueChange = { confirmar = it.filter(Char::isDigit).take(8); erro = null },
                    label = { Text("Confirmar PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                MensagemErro(erro)
            }
        },
        confirmButton = {
            Button(enabled = !aCarregar, onClick = {
                erro = validarPinAdmin(pin) ?: if (confirmar != pin) "Os PINs não coincidem." else if (senha.isEmpty()) "Indica a senha da conta." else null
                if (erro != null) return@Button
                aCarregar = true
                escopo.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Sessão inválida.")
                        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), senha)).await()
                        c.repo.alterarPinAdmin(c.fid, pin)
                        aoSucesso()
                    } catch (e: Exception) {
                        erro = traduzirErro(e)
                    }
                    aCarregar = false
                }
            }) { Text(if (aCarregar) "Aguarda..." else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !aCarregar, onClick = aoFechar) { Text("Cancelar") } }
    )
}
