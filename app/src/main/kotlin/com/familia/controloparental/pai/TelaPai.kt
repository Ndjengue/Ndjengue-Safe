package com.familia.controloparental.pai

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familia.controloparental.comum.*
import com.familia.controloparental.data.FirestoreRepositorio
import com.familia.controloparental.filho.Ambiente
import com.familia.controloparental.servico.ServicoMonitorPai
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dados partilhados por todos os ecrãs do pai (uma única subscrição ao Firestore). */
class ContextoPai(
    val fid: String,
    val repo: FirestoreRepositorio,
    val dispositivos: List<Dispositivo>,
    val pedidos: List<PedidoLiberacao>,
    val alertas: List<Alerta>
) {
    fun dispositivo(did: String): Dispositivo? = dispositivos.firstOrNull { it.id == did }
    val pedidosPendentes: List<PedidoLiberacao> get() = pedidos.filter { it.status == StatusPedidoLiberacao.PENDENTE }
}

/** Relógio que "bate" a cada 15 s, para que o estado online/offline se atualize sozinho. */
@Composable
fun rememberAgora(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(15_000)
        value = System.currentTimeMillis()
    }
}

@Composable
fun TelaPai(aoSair: () -> Unit) {
    val repo = remember { Ambiente.repo }
    var fid by remember { mutableStateOf<String?>(null) }
    var estado by remember { mutableStateOf("A_CARREGAR") } // A_CARREGAR, CONFIGURAR, PRONTO, ERRO
    var erro by remember { mutableStateOf<String?>(null) }
    var tentativa by remember { mutableIntStateOf(0) }

    LaunchedEffect(tentativa) {
        estado = "A_CARREGAR"
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            aoSair()
            return@LaunchedEffect
        }
        try {
            val f = repo.buscarFamiliaDoPai(uid)
            if (f == null) estado = "CONFIGURAR" else { fid = f; estado = "PRONTO" }
        } catch (e: Exception) {
            erro = traduzirErro(e)
            estado = "ERRO"
        }
    }

    when (estado) {
        "A_CARREGAR" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        "ERRO" -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(erro ?: "Erro", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { tentativa++ }) { Text("Tentar novamente") }
            TextButton(onClick = { FirebaseAuth.getInstance().signOut(); aoSair() }) { Text("Sair") }
        }
        "CONFIGURAR" -> TelaConfigurarFamilia(
            aoConcluir = { novo -> fid = novo; estado = "PRONTO" },
            aoSair = { FirebaseAuth.getInstance().signOut(); aoSair() }
        )
        else -> fid?.let { ShellPai(it, aoSair) }
    }
}

/** Para contas criadas sem família (ex.: o registo foi interrompido): só falta definir o PIN. */
@Composable
private fun TelaConfigurarFamilia(aoConcluir: (String) -> Unit, aoSair: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quase pronto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Define o PIN de administrador (4 a 8 números). É pedido no telemóvel da criança para desinstalar a app.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmar, onValueChange = { confirmar = it.filter(Char::isDigit).take(8) }, label = { Text("Confirmar PIN") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
        )
        MensagemErro(erro)
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !aCarregar,
            onClick = {
                erro = validarPinAdmin(pin) ?: if (confirmar != pin) "Os PINs não coincidem." else null
                if (erro != null) return@Button
                aCarregar = true
                escopo.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser!!
                        val novo = Ambiente.repo.criarFamilia(user.uid, user.email ?: "", pin)
                        aoConcluir(novo)
                    } catch (e: Exception) {
                        erro = traduzirErro(e)
                    }
                    aCarregar = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Concluir") }
        TextButton(onClick = aoSair, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
    }
}

private data class Separador(val rota: String, val rotulo: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellPai(fid: String, aoSair: () -> Unit) {
    val repo = remember { Ambiente.repo }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dispositivos by remember(fid) { repo.observarDispositivos(fid) }.collectAsState(initial = emptyList())
    val pedidos by remember(fid) { repo.observarPedidos(fid) }.collectAsState(initial = emptyList())
    val alertas by remember(fid) { repo.observarAlertas(fid) }.collectAsState(initial = emptyList())
    val contexto = ContextoPai(fid, repo, dispositivos, pedidos, alertas)

    LaunchedEffect(Unit) { ServicoMonitorPai.iniciarSeAtivo(ctx) }

    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rota = entrada?.destination?.route ?: "inicio"
    val mostrarBarra = rota == "inicio" || rota.startsWith("mapa") || rota == "alertas" || rota == "definicoes"

    val pendentes = contexto.pedidosPendentes.size
    val naoLidos = alertas.count { !it.lido }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val escopoMenu = rememberCoroutineScope()
    fun irPara(destino: String) {
        escopoMenu.launch { drawerState.close() }
        if (destino == "sobre") nav.navigate("sobre") else navegarTab(nav, destino)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = mostrarBarra,
        drawerContent = {
            ModalDrawerSheet {
                Box(Modifier.fillMaxWidth().fundoDeMarca().statusBarsPadding().padding(20.dp)) {
                    Column {
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(64.dp)) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(com.familia.controloparental.R.drawable.logo_app),
                                contentDescription = null, modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(NOME_APP, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple("inicio", "Início", Icons.Filled.Home),
                    Triple("mapa", "Mapa em direto", Icons.Filled.Map),
                    Triple("alertas", "Pedidos e alertas", Icons.Filled.Notifications),
                    Triple("definicoes", "Definições", Icons.Filled.Settings),
                    Triple("sobre", "Sobre", Icons.Filled.Info)
                ).forEach { (rotaItem, nome, icone) ->
                    NavigationDrawerItem(
                        label = { Text(nome) },
                        icon = { Icon(icone, null) },
                        selected = rota == rotaItem || (rotaItem == "mapa" && rota.startsWith("mapa")),
                        onClick = { irPara(rotaItem) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                NavigationDrawerItem(
                    label = { Text("Terminar sessão") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    selected = false,
                    onClick = {
                        ServicoMonitorPai.parar(ctx)
                        FirebaseAuth.getInstance().signOut()
                        aoSair()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    ) {
    Scaffold(
        bottomBar = {
            if (mostrarBarra) {
                NavigationBar {
                    NavigationBarItem(
                        selected = rota == "inicio", onClick = { navegarTab(nav, "inicio") },
                        icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Início") }
                    )
                    NavigationBarItem(
                        selected = rota.startsWith("mapa"), onClick = { navegarTab(nav, "mapa") },
                        icon = { Icon(Icons.Filled.Map, null) }, label = { Text("Mapa") }
                    )
                    NavigationBarItem(
                        selected = rota == "alertas", onClick = { navegarTab(nav, "alertas") },
                        icon = {
                            BadgedBox(badge = { if (pendentes + naoLidos > 0) Badge { Text("${pendentes + naoLidos}") } }) {
                                Icon(Icons.Filled.Notifications, null)
                            }
                        },
                        label = { Text("Avisos") }
                    )
                    NavigationBarItem(
                        selected = rota == "definicoes", onClick = { navegarTab(nav, "definicoes") },
                        icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Definições") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "inicio",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable("inicio") {
                TelaInicioPai(
                    c = contexto,
                    aoAbrirFilho = { nav.navigate("filho/$it") },
                    aoAdicionar = { nav.navigate("adicionar") },
                    aoAbrirAvisos = { navegarTab(nav, "alertas") },
                    aoAbrirMenu = { escopoMenu.launch { drawerState.open() } }
                )
            }
            composable(
                "mapa?did={did}",
                arguments = listOf(navArgument("did") { type = NavType.StringType; defaultValue = "" })
            ) { e ->
                TelaMapa(contexto, e.arguments?.getString("did").orEmpty())
            }
            composable("alertas") {
                TelaAvisos(contexto, aoAbrirMapa = { nav.navigate("mapa?did=$it") })
            }
            composable("definicoes") { TelaDefinicoes(contexto, aoSair, aoAbrirSobre = { nav.navigate("sobre") }, aoAbrirAdmin = { nav.navigate("admin") }) }
            composable("admin") { com.familia.controloparental.admin.TelaSuperAdmin(aoVoltar = { nav.popBackStack() }) }
            composable("sobre") { TelaSobre(aoVoltar = { nav.popBackStack() }) }
            composable("adicionar") {
                TelaFormularioFilho(contexto, didExistente = null, aoVoltar = { nav.popBackStack() }, aoConcluir = { nav.popBackStack() })
            }
            composable("editar/{did}") { e ->
                TelaFormularioFilho(
                    contexto, didExistente = e.arguments?.getString("did"),
                    aoVoltar = { nav.popBackStack() }, aoConcluir = { nav.popBackStack() }
                )
            }
            composable("filho/{did}") { e ->
                val did = e.arguments?.getString("did").orEmpty()
                TelaFilho(
                    c = contexto,
                    did = did,
                    aoVoltar = { nav.popBackStack() },
                    aoEditar = { nav.navigate("editar/$did") },
                    aoAbrirMapa = { nav.navigate("mapa?did=$did") },
                    aoEditarHorario = { perfilId, alvo ->
                        nav.navigate("horario/$did?perfil=${Uri.encode(perfilId)}&alvo=${Uri.encode(alvo)}")
                    },
                    aoRemovido = { nav.popBackStack("inicio", false) }
                )
            }
            composable(
                "horario/{did}?perfil={perfil}&alvo={alvo}",
                arguments = listOf(
                    navArgument("did") { type = NavType.StringType },
                    navArgument("perfil") { type = NavType.StringType; defaultValue = "" },
                    navArgument("alvo") { type = NavType.StringType; defaultValue = "" }
                )
            ) { e ->
                TelaEditorHorario(
                    c = contexto,
                    did = e.arguments?.getString("did").orEmpty(),
                    perfilId = e.arguments?.getString("perfil").orEmpty(),
                    alvo = e.arguments?.getString("alvo").orEmpty(),
                    aoVoltar = { nav.popBackStack() }
                )
            }
        }
    }
    }
}

private fun navegarTab(nav: androidx.navigation.NavHostController, destino: String) {
    nav.navigate(destino) {
        popUpTo("inicio") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
