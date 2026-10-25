package com.familia.controloparental.comum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.filho.TelaEmparelhamento
import com.familia.controloparental.filho.TelaFilhoInicio
import com.familia.controloparental.pai.TelaLoginPai
import com.familia.controloparental.pai.TelaPai
import com.familia.controloparental.pai.TelaRecuperarSenha
import com.familia.controloparental.pai.TelaRegistoPai
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

/**
 * Ponto de entrada único do APK: depois do splash, vai direto para onde a pessoa estava
 * (painel dos pais, ecrã da criança) ou pergunta quem está a usar o telemóvel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaControloParental {
                val ctx = LocalContext.current
                val destino by produceState<String?>(null) {
                    val cache = CacheLocal(ctx)
                    val utilizador = FirebaseAuth.getInstance().currentUser
                    value = when {
                        cache.estaEmparelhado() -> "filho_inicio"
                        utilizador != null && !utilizador.isAnonymous -> "pai"
                        else -> "escolha_modo"
                    }
                }
                LaunchedEffect(Unit) { com.familia.controloparental.admin.AtividadeRegisto.registar() }
                var tempoMinimo by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1600)
                    tempoMinimo = true
                }
                val pronto = destino != null && tempoMinimo

                Box(Modifier.fillMaxSize()) {
                    if (destino != null) {
                        AnimatedVisibility(visible = pronto, enter = fadeIn(tween(450))) {
                            Navegacao(destino!!)
                        }
                    }
                    AnimatedVisibility(visible = !pronto, exit = fadeOut(tween(450))) {
                        EcraSplash()
                    }
                }
            }
        }
    }
}

@Composable
private fun Navegacao(inicio: String) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    NavHost(navController = nav, startDestination = inicio) {
        composable("escolha_modo") { TelaEscolhaModo(nav) }
        composable("pai_login") { TelaLoginPai(nav) }
        composable("pai_registo") { TelaRegistoPai(nav) }
        composable("pai_recuperar") { TelaRecuperarSenha(nav) }
        composable("pai") {
            TelaPai(aoSair = {
                nav.navigate("escolha_modo") { popUpTo(0) }
            })
        }
        composable("sobre") { TelaSobre(aoVoltar = { nav.popBackStack() }) }
        composable("filho_emparelhamento") {
            TelaEmparelhamento(
                aoVoltar = { nav.popBackStack() },
                aoConcluir = { nav.navigate("filho_inicio") { popUpTo("escolha_modo") { inclusive = true } } }
            )
        }
        composable("filho_comunidade") {
            com.familia.controloparental.comunidade.TelaComunidadeFilho(aoVoltar = { nav.popBackStack() })
        }
        composable("filho_inicio") {
            // Se o pai remover este dispositivo, o serviço apaga os dados locais e voltamos ao início.
            LaunchedEffect(Unit) {
                CacheLocal(ctx).observarEmparelhado().collect { emparelhado ->
                    if (!emparelhado) nav.navigate("escolha_modo") { popUpTo(0) }
                }
            }
            TelaFilhoInicio(
                aoDesemparelhar = { nav.navigate("escolha_modo") { popUpTo(0) } },
                aoAbrirComunidade = { nav.navigate("filho_comunidade") }
            )
        }
    }
}

@Composable
private fun EcraSplash() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoAnimado(280.dp)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(120.dp),
                color = VerdeEsmeralda,
                trackColor = Color(0xFFE3EEF8)
            )
        }
    }
}

@Composable
fun TelaEscolhaModo(navController: NavHostController) {
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visivel = true }

    Box(Modifier.fillMaxSize().fundoDeMarca()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(32.dp), color = Color.White, shadowElevation = 12.dp) {
                LogoAnimado(170.dp, Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Quem está a usar este telemóvel?",
                color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = visivel, enter = fadeIn(tween(500, 150)) + slideInVertically(tween(500, 150)) { it / 2 }) {
                CartaoEscolha(
                    icone = Icons.Filled.Shield,
                    titulo = "Sou pai ou mãe",
                    descricao = "Gerir regras, horários, mapa e pedidos",
                    aoClicar = { navController.navigate("pai_login") }
                )
            }
            Spacer(Modifier.height(14.dp))
            AnimatedVisibility(visible = visivel, enter = fadeIn(tween(500, 350)) + slideInVertically(tween(500, 350)) { it / 2 }) {
                CartaoEscolha(
                    icone = Icons.Filled.PhoneAndroid,
                    titulo = "Este é o telemóvel da criança",
                    descricao = "Emparelhar com o código dos pais",
                    aoClicar = { navController.navigate("filho_emparelhamento") }
                )
            }
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = { navController.navigate("sobre") }) {
                Text("Sobre o $NOME_APP", color = Color.White)
            }
            Text(
                "© 2026 $NOME_DESENVOLVEDOR",
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CartaoEscolha(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    descricao: String,
    aoClicar: () -> Unit
) {
    Surface(
        onClick = aoClicar,
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = AzulPrimario.copy(alpha = 0.1f), modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icone, contentDescription = null, tint = AzulPrimario)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = TextoPrimario)
                Spacer(Modifier.height(2.dp))
                Text(descricao, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextoSecundario, modifier = Modifier.size(16.dp))
        }
    }
}
