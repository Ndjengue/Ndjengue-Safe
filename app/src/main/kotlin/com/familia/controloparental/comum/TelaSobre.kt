package com.familia.controloparental.comum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familia.controloparental.R
import kotlinx.coroutines.delay

const val NOME_APP = "Ndjengue VPN"
const val LEMA_APP = "Navegação segura. Controlo parental."
const val NOME_DESENVOLVEDOR = "Esteves Ndjengue Mbuco Júnior"

/** Logótipo com animação de entrada e uma respiração suave. */
@Composable
fun LogoAnimado(tamanho: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    var entrou by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entrou = true }
    val escalaEntrada by animateFloatAsState(
        if (entrou) 1f else 0.6f, spring(dampingRatio = 0.55f, stiffness = 120f), label = "entrada"
    )
    val respiracao = rememberInfiniteTransition(label = "respiracao")
    val pulso by respiracao.animateFloat(
        1f, 1.035f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso"
    )
    Image(
        painter = painterResource(R.drawable.logo_app),
        contentDescription = NOME_APP,
        modifier = modifier.size(tamanho).scale(escalaEntrada * pulso)
    )
}

@Composable
private fun LinhaInfo(icone: ImageVector, titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaSobre(aoVoltar: () -> Unit) {
    val ctx = LocalContext.current
    val versao = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    }
    var mostrar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); mostrar = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sobre") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            LogoAnimado(220.dp)
            Text("Versão $versao", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(
                visible = mostrar,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CartaoSecao("A app") {
                        Text(
                            "O $NOME_APP ajuda as famílias a proteger os mais novos: bloqueia apps e sites, define horários, " +
                                "mostra o tempo de ecrã e deixa os pais ver, em tempo real, onde a criança está.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    CartaoSecao("Autoria") {
                        LinhaInfo(Icons.Filled.Person, "Desenvolvedor e criador", NOME_DESENVOLVEDOR)
                        LinhaInfo(Icons.Filled.CalendarMonth, "Criada em", "Outubro de 2026")
                        LinhaInfo(Icons.Filled.Code, "Tecnologia", "Android · Kotlin · Jetpack Compose · Firebase")
                    }
                    CartaoSecao("Direitos de autor") {
                        Text(
                            "© 2026 $NOME_DESENVOLVEDOR.\nTodos os direitos reservados.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "O nome, o logótipo e o código desta aplicação são propriedade do autor. " +
                                "A reprodução ou distribuição sem autorização é proibida.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CartaoSecao("Privacidade") {
                        Text(
                            "Os dados (localização, apps e horários) ficam na conta Firebase da tua família e só os pais " +
                                "ligados à conta os podem ver. A criança vê sempre que a proteção está ativa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(LEMA_APP, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Suppress("unused")
private val _cor = Color.Unspecified
