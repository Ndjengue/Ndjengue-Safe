package com.familia.controloparental.pai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.*
import kotlinx.coroutines.launch

private sealed class AlvoEscolha {
    class Rede(val rede: RedeSocial) : AlvoEscolha()
    class Site(val regra: RegraUrl) : AlvoEscolha()
}

@Composable
fun SeparadorSites(
    c: ContextoPai,
    d: DadosFilho,
    aoEditarHorario: (perfilId: String, alvo: String) -> Unit
) {
    val did = d.dispositivo.id
    val escopo = rememberCoroutineScope()
    var novoDominio by remember { mutableStateOf("") }
    var erroDominio by remember { mutableStateOf<String?>(null) }
    var erroGeral by remember { mutableStateOf<String?>(null) }
    var escolhendo by remember { mutableStateOf<AlvoEscolha?>(null) }
    var apagar by remember { mutableStateOf<RegraUrl?>(null) }

    fun guardar(regra: RegraUrl) {
        escopo.launch {
            try {
                c.repo.guardarRegraUrl(c.fid, did, regra)
                erroGeral = null
            } catch (e: Exception) {
                erroGeral = traduzirErro(e)
            }
        }
    }

    fun regraDaRede(r: RedeSocial): RegraUrl? = d.regras.firstOrNull { it.id == "rede_${r.id}" }
    fun baseRede(r: RedeSocial): RegraUrl =
        regraDaRede(r) ?: RegraUrl("rede_${r.id}", r.nome, TipoRegraUrl.REDE_SOCIAL, r.dominios, r.pacotes, false, null)

    val sites = d.regras.filter { it.tipo == TipoRegraUrl.PERSONALIZADO }.sortedBy { it.rotulo.lowercase() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            CartaoSecao("Bloquear um site") {
                Text(
                    "Escreve o endereço (ex.: exemplo.com). Os subdomínios também ficam bloqueados.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = novoDominio, onValueChange = { novoDominio = it; erroDominio = null },
                        label = { Text("Site") }, singleLine = true,
                        isError = erroDominio != null, supportingText = { erroDominio?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = {
                        erroDominio = validarDominio(novoDominio)
                        if (erroDominio == null) {
                            val dom = normalizarDominio(novoDominio)
                            if (sites.any { dom in it.dominios }) {
                                erroDominio = "Este site já está na lista."
                            } else {
                                guardar(RegraUrl("", dom, TipoRegraUrl.PERSONALIZADO, listOf(dom), emptyList(), true, null))
                                novoDominio = ""
                            }
                        }
                    }, modifier = Modifier.padding(top = 6.dp).size(48.dp)) { Icon(Icons.Filled.Add, "Adicionar") }
                }
                MensagemErro(erroGeral)
            }
        }

        if (sites.isNotEmpty()) {
            item { Text("Sites bloqueados", style = MaterialTheme.typography.titleMedium) }
            items(sites, key = { it.id }) { r ->
                val perfil = d.perfis.firstOrNull { it.id == r.perfilHorarioId }
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(r.rotulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { apagar = r }) { Icon(Icons.Filled.Delete, "Apagar") }
                        }
                        SeletorModo(r.modo) { modo ->
                            when (modo) {
                                RegraApp.PERMITIDO -> guardar(r.copy(ativo = false, perfilHorarioId = null))
                                RegraApp.BLOQUEADO -> guardar(r.copy(ativo = true, perfilHorarioId = null))
                                RegraApp.BLOQUEADO_POR_HORARIO ->
                                    if (d.perfis.isEmpty()) aoEditarHorario("", "site:${r.id}") else escolhendo = AlvoEscolha.Site(r)
                            }
                        }
                        if (r.modo == RegraApp.BLOQUEADO_POR_HORARIO) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (perfil != null) "Horário: ${perfil.nome}" else "Sem horário escolhido",
                                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { escolhendo = AlvoEscolha.Site(r) }) { Text("Alterar") }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Redes sociais e plataformas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bloqueia o site e a app da rede de uma só vez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(CatalogoRedes.todas, key = { it.id }) { rede ->
            val regra = regraDaRede(rede)
            val modo = regra?.modo ?: RegraApp.PERMITIDO
            val perfil = d.perfis.firstOrNull { it.id == regra?.perfilHorarioId }
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp)) {
                    Text(rede.nome, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    SeletorModo(modo) { novo ->
                        when (novo) {
                            RegraApp.PERMITIDO -> if (regra != null) guardar(regra.copy(ativo = false, perfilHorarioId = null))
                            RegraApp.BLOQUEADO -> guardar(baseRede(rede).copy(ativo = true, perfilHorarioId = null))
                            RegraApp.BLOQUEADO_POR_HORARIO ->
                                if (d.perfis.isEmpty()) aoEditarHorario("", "rede:${rede.id}") else escolhendo = AlvoEscolha.Rede(rede)
                        }
                    }
                    if (modo == RegraApp.BLOQUEADO_POR_HORARIO) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (perfil != null) "Horário: ${perfil.nome}" else "Sem horário escolhido",
                                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { escolhendo = AlvoEscolha.Rede(rede) }) { Text("Alterar") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    escolhendo?.let { alvo ->
        val titulo: String
        val atual: String?
        when (alvo) {
            is AlvoEscolha.Rede -> { titulo = "Horário para ${alvo.rede.nome}"; atual = regraDaRede(alvo.rede)?.perfilHorarioId }
            is AlvoEscolha.Site -> { titulo = "Horário para ${alvo.regra.rotulo}"; atual = alvo.regra.perfilHorarioId }
        }
        DialogoEscolherPerfil(
            titulo = titulo, perfis = d.perfis, atualId = atual,
            aoEscolher = { id ->
                when (alvo) {
                    is AlvoEscolha.Rede -> guardar(baseRede(alvo.rede).copy(ativo = false, perfilHorarioId = id))
                    is AlvoEscolha.Site -> guardar(alvo.regra.copy(ativo = false, perfilHorarioId = id))
                }
                escolhendo = null
            },
            aoCriarNovo = {
                escolhendo = null
                when (alvo) {
                    is AlvoEscolha.Rede -> aoEditarHorario("", "rede:${alvo.rede.id}")
                    is AlvoEscolha.Site -> aoEditarHorario("", "site:${alvo.regra.id}")
                }
            },
            aoFechar = { escolhendo = null }
        )
    }

    apagar?.let { r ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Deixar de bloquear ${r.rotulo}?") },
            text = { Text("O site é removido da lista.") },
            confirmButton = {
                Button(onClick = {
                    apagar = null
                    escopo.launch { try { c.repo.removerRegraUrl(c.fid, did, r.id) } catch (e: Exception) { erroGeral = traduzirErro(e) } }
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } }
        )
    }
}
