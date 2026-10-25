package com.familia.controloparental.filho

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.servico.ServicoSincronizacao
import com.familia.controloparental.vpn.FiltroVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private class PassoConfig(
    val titulo: String,
    val descricao: String,
    val feito: Boolean,
    val rotuloBotao: String,
    val acao: () -> Unit
)

/**
 * Página principal do telemóvel da criança: passos de ativação da proteção, botão SOS,
 * pedidos feitos e a opção (protegida por PIN) de desemparelhar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFilhoInicio(aoDesemparelhar: () -> Unit, aoAbrirComunidade: () -> Unit = {}) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val cache = remember { CacheLocal(ctx) }

    var estado by remember { mutableStateOf(LeitorProtecao.ler(ctx)) }
    var bateriaOk by remember { mutableStateOf(LeitorProtecao.semRestricaoBateria(ctx)) }
    var fid by remember { mutableStateOf<String?>(null) }
    var did by remember { mutableStateOf<String?>(null) }
    var mostrarSos by remember { mutableStateOf(false) }
    var mostrarPin by remember { mutableStateOf(false) }
    var sosEnviado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fid = cache.lerFamiliaId()
        did = cache.lerDispositivoId()
        while (true) {
            estado = LeitorProtecao.ler(ctx)
            bateriaOk = LeitorProtecao.semRestricaoBateria(ctx)
            delay(2_000)
        }
    }

    val dispositivo by remember(fid, did) {
        val f = fid
        val d = did
        if (f != null && d != null) Ambiente.repo.observarDispositivo(f, d) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val pedidos by remember(fid, did) {
        val f = fid
        val d = did
        if (f != null && d != null) Ambiente.repo.observarPedidosDoDispositivo(f, d)
        else kotlinx.coroutines.flow.flowOf(emptyList<PedidoLiberacao>())
    }.collectAsState(initial = emptyList())

    val lancadorNotificacoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        estado = LeitorProtecao.ler(ctx)
    }
    val lancadorLocalizacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        estado = LeitorProtecao.ler(ctx)
        ServicoSincronizacao.reiniciar(ctx)
    }
    val lancadorLocalizacaoSempre = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        estado = LeitorProtecao.ler(ctx)
        ServicoSincronizacao.reiniciar(ctx)
    }
    val lancadorVpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) FiltroVpnService.iniciar(ctx)
    }

    fun abrir(intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: Exception) {}
    }

    val ehXiaomi = remember {
        val f = Build.MANUFACTURER.lowercase() + " " + Build.BRAND.lowercase()
        f.contains("xiaomi") || f.contains("redmi") || f.contains("poco")
    }

    val passos = buildList {
        add(PassoConfig(
            "Localização sempre ligada",
            "Permite que os teus pais te encontrem se te perderes. Escolhe \"Permitir sempre\".",
            estado.localizacaoSempre,
            if (estado.localizacaoPermitida) "Permitir sempre" else "Permitir"
        ) {
            if (!estado.localizacaoPermitida) {
                lancadorLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else if (Build.VERSION.SDK_INT >= 30) {
                abrir(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            } else if (Build.VERSION.SDK_INT >= 29) {
                lancadorLocalizacaoSempre.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        })
        if (Build.VERSION.SDK_INT >= 33) {
            add(PassoConfig(
                "Notificações", "Para receberes avisos sobre pedidos e sites bloqueados.",
                estado.notificacoesPermitidas, "Permitir"
            ) { lancadorNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS) })
        }
        add(PassoConfig(
            "Tempo de ecrã", "Permite medir o tempo em cada app. Procura \"Ndjengue Safe\" e ativa.",
            estado.usoAppsPermitido, "Abrir definições"
        ) { abrir(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        add(PassoConfig(
            "Filtro de sites", "Cria uma ligação local que bloqueia sites escolhidos pelos pais. Não gasta dados.",
            estado.vpnAtiva, "Ativar"
        ) {
            val intent = VpnService.prepare(ctx)
            if (intent != null) lancadorVpn.launch(intent) else FiltroVpnService.iniciar(ctx)
        })
        add(PassoConfig(
            "Serviço de acessibilidade",
            "Necessário para bloquear apps. Se aparecer \"Definição restrita\": Informações da app → ⋮ → Permitir definições restritas.",
            estado.acessibilidadeAtiva, "Abrir definições"
        ) { abrir(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        add(PassoConfig(
            "Proteção contra desinstalação",
            "Ativa o administrador do dispositivo. Para desinstalar será pedido o PIN dos pais.",
            estado.adminAtivo, "Ativar"
        ) {
            abrir(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, LeitorProtecao.componenteAdmin(ctx))
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Impede que a app seja desinstalada sem o PIN dos pais."
                    )
            )
        })
        add(PassoConfig(
            "Funcionar sempre em segundo plano",
            "Evita que o telemóvel feche a proteção para poupar bateria.",
            bateriaOk, "Permitir"
        ) {
            abrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        })
    }

    val feitos = passos.count { it.feito }
    val nome = dispositivo?.crianca?.nome.orEmpty()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ndjengue Safe") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fundoDeMarca().padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCrianca(dispositivo?.crianca?.fotoBase64, nome.ifBlank { "Eu" }, 60.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                if (nome.isBlank()) "Olá!" else "Olá, $nome!",
                                color = Color.White, style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                if (feitos == passos.size) "A tua proteção está completa ✔"
                                else "Faltam ${passos.size - feitos} passo(s) para ficar tudo pronto",
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { mostrarSos = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CorErro),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.Sos, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (sosEnviado) "Pedido de ajuda enviado" else "Preciso de ajuda")
                }
            }

            if (feitos < passos.size) {
                item {
                    Text("Ativar a proteção", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(passos) { p -> CartaoPasso(p) }

            item {
                OutlinedButton(onClick = aoAbrirComunidade, modifier = Modifier.fillMaxWidth()) {
                    Text("Mensagens com os pais")
                }
            }

            if (ehXiaomi) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Telemóvel Xiaomi / Redmi / POCO", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "O MIUI/HyperOS fecha apps em segundo plano. Para o bloqueio funcionar sempre, faz estes 3 passos:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { abrirAutoArranqueXiaomi(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                Text("1. Ativar \"Arranque automático\"")
                            }
                            OutlinedButton(onClick = { abrirPermissoesXiaomi(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                Text("2. Permitir pop-ups e ecrã de bloqueio")
                            }
                            OutlinedButton(onClick = { abrirBateriaXiaomi(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                Text("3. Poupança de bateria: \"Sem restrições\"")
                            }
                            Text(
                                "Dica: abre os Recentes, toca no ícone da app Ndjengue Safe e escolhe \"Bloquear\" (cadeado) para não ser fechada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (pedidos.isNotEmpty()) {
                item { Text("Os meus pedidos", style = MaterialTheme.typography.titleMedium) }
                items(pedidos.take(8)) { p ->
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.rotulo.ifBlank { p.alvo }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${formatarDataHora(p.criadoEm)} · ${p.duracaoMinutos} min",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when (p.status) {
                                StatusPedidoLiberacao.APROVADO -> EtiquetaEstado("Aprovado", VerdeEsmeralda)
                                StatusPedidoLiberacao.RECUSADO -> EtiquetaEstado("Recusado", CorErro)
                                StatusPedidoLiberacao.EXPIRADO -> EtiquetaEstado("Expirado", TextoSecundario)
                                else -> EtiquetaEstado("A aguardar", CorAviso)
                            }
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { mostrarPin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desemparelhar este telemóvel (PIN dos pais)")
                }
            }
        }
    }

    if (mostrarSos) {
        AlertDialog(
            onDismissRequest = { mostrarSos = false },
            title = { Text("Pedir ajuda aos pais?") },
            text = { Text("Vamos avisar os teus pais e partilhar a tua localização em direto durante 15 minutos.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CorErro),
                    onClick = {
                        mostrarSos = false
                        sosEnviado = true
                        val f = fid
                        val d = did
                        if (f != null && d != null) {
                            escopo.launch {
                                val n = cache.lerTexto(Ambiente.CHAVE_NOME_CRIANCA) ?: ""
                                withTimeoutOrNull(5_000) {
                                    try {
                                        Ambiente.repo.definirRastreioAoVivo(f, d, System.currentTimeMillis() + 15 * 60_000L)
                                        Ambiente.repo.enviarAlerta(
                                            f, Alerta(
                                                dispositivoId = d, criancaNome = n, tipo = TipoAlerta.PEDIDO_AJUDA,
                                                mensagem = "${n.ifBlank { "A criança" }} pediu ajuda! Abre o mapa para ver onde está."
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    }
                ) { Text("Pedir ajuda") }
            },
            dismissButton = { TextButton(onClick = { mostrarSos = false }) { Text("Cancelar") } }
        )
    }

    if (mostrarPin) {
        var pin by remember { mutableStateOf("") }
        var erro by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { mostrarPin = false },
            title = { Text("PIN de administrador") },
            text = {
                Column {
                    Text("Só os pais podem desemparelhar este telemóvel.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        label = { Text("PIN") }
                    )
                    MensagemErro(erro)
                }
            },
            confirmButton = {
                Button(onClick = {
                    escopo.launch {
                        val r = cache.validarPin(pin)
                        if (r.correto) {
                            desemparelhar(ctx, cache)
                            mostrarPin = false
                            aoDesemparelhar()
                        } else {
                            pin = ""
                            erro = if (r.bloqueadoAteMs > System.currentTimeMillis())
                                "Demasiadas tentativas. Tenta mais tarde."
                            else "PIN incorreto. Restam ${r.tentativasRestantes} tentativa(s)."
                            AlertasFilho.enviar(ctx, TipoAlerta.SENHA_ADMIN_ERRADA, "PIN incorreto ao tentar desemparelhar.", 20_000L)
                        }
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { mostrarPin = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CartaoPasso(p: PassoConfig) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (p.feito) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (p.feito) VerdeEsmeralda else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.titulo, style = MaterialTheme.typography.titleMedium)
                Text(p.descricao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!p.feito) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = p.acao) { Text(p.rotuloBotao) }
            }
        }
    }
}

/** Remove o emparelhamento: pára serviços, retira o administrador e apaga os dados locais. */
private suspend fun desemparelhar(ctx: Context, cache: CacheLocal) {
    val f = cache.lerFamiliaId()
    val d = cache.lerDispositivoId()
    val nome = cache.lerTexto(Ambiente.CHAVE_NOME_CRIANCA) ?: ""
    if (f != null && d != null) {
        withTimeoutOrNull(4_000) {
            try {
                Ambiente.repo.enviarAlerta(
                    f, Alerta(dispositivoId = d, criancaNome = nome, tipo = TipoAlerta.INFO,
                        mensagem = "Este telemóvel foi desemparelhado com o PIN dos pais.")
                )
                Ambiente.repo.marcarOffline(f, d)
            } catch (_: Exception) {
            }
        }
    }
    try {
        ctx.startService(Intent(ctx, FiltroVpnService::class.java).setAction(FiltroVpnService.ACAO_PARAR))
    } catch (_: Exception) {
    }
    try {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(LeitorProtecao.componenteAdmin(ctx))
    } catch (_: Exception) {
    }
    ctx.stopService(Intent(ctx, ServicoSincronizacao::class.java))
    cache.limparTudo()
}


private fun tentar(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

private fun abrirAutoArranqueXiaomi(ctx: Context) = tentar(
    ctx,
    Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
)

private fun abrirPermissoesXiaomi(ctx: Context) = tentar(
    ctx,
    Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", ctx.packageName),
    Intent("miui.intent.action.APP_PERM_EDITOR")
        .setPackage("com.miui.securitycenter")
        .putExtra("extra_pkgname", ctx.packageName)
)

private fun abrirBateriaXiaomi(ctx: Context) = tentar(
    ctx,
    Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
        .setPackage("com.miui.powerkeeper")
        .putExtra("package_name", ctx.packageName)
        .putExtra("package_label", "Ndjengue Safe"),
    Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
        .putExtra("package_name", ctx.packageName)
        .putExtra("package_label", "Ndjengue Safe")
)
