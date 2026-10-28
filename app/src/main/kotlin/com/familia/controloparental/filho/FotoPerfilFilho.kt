package com.familia.controloparental.filho

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.familia.controloparental.comum.AvatarCrianca
import com.familia.controloparental.comum.Imagens
import com.familia.controloparental.comum.traduzirErro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Foto de perfil da criança: toca para tirar uma foto com a câmara, escolher da galeria ou remover. */
@Composable
fun AvatarEditavel(foto: String?, nome: String, fid: String?, did: String?, tamanho: Dp = 60.dp) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var fotoLocal by remember { mutableStateOf<String?>(null) }
    var removida by remember { mutableStateOf(false) }
    var aGuardar by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    fun guardar(b64: String?) {
        val f = fid; val d = did
        if (f == null || d == null) { mensagem = "Ainda a ligar. Tenta daqui a pouco."; return }
        aGuardar = true
        escopo.launch {
            try {
                kotlinx.coroutines.withTimeout(15_000L) { Ambiente.repo.atualizarFotoCrianca(f, d, b64) }
                fotoLocal = b64; removida = b64 == null
                mensagem = if (b64 == null) "Foto removida." else "Foto atualizada!"
            } catch (e: Exception) {
                mensagem = if (e is kotlinx.coroutines.TimeoutCancellationException) "Sem ligação à internet. Tenta de novo." else traduzirErro(e)
            }
            aGuardar = false
        }
    }

    fun processar(uri: Uri) {
        escopo.launch {
            val b64 = withContext(Dispatchers.IO) { Imagens.fotoDeUri(ctx, uri) }
            if (b64 == null) mensagem = "Não foi possível usar essa foto." else guardar(b64)
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) processar(uri) }
    var uriCamara by remember { mutableStateOf<Uri?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = uriCamara
        if (ok && u != null) processar(u)
    }
    fun abrirCamara() {
        try {
            val dir = File(ctx.cacheDir, "fotos").apply { mkdirs() }
            val ficheiro = File(dir, "perfil_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", ficheiro)
            uriCamara = u
            camara.launch(u)
        } catch (e: Exception) {
            mensagem = "Não foi possível abrir a câmara."
        }
    }
    val pedirCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) abrirCamara() else mensagem = "Sem permissão da câmara. Podes escolher uma foto da galeria."
    }

    val mostrar = if (removida) null else (fotoLocal ?: foto)
    Box(Modifier.size(tamanho).clip(CircleShape).clickable(enabled = !aGuardar) { menu = true }) {
        AvatarCrianca(mostrar, nome, tamanho)
        Surface(
            color = MaterialTheme.colorScheme.secondary, shape = CircleShape,
            modifier = Modifier.size(tamanho / 3).align(Alignment.BottomEnd)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (aGuardar) CircularProgressIndicator(Modifier.size(tamanho / 6), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Filled.PhotoCamera, "Mudar foto", tint = Color.White, modifier = Modifier.size(tamanho / 6))
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Foto de perfil") },
            text = {
                Column {
                    TextButton(onClick = {
                        menu = false
                        pedirCamara.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Tirar foto com a câmara") }
                    TextButton(onClick = {
                        menu = false
                        galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Escolher da galeria") }
                    if (mostrar != null) TextButton(onClick = { menu = false; guardar(null) }, modifier = Modifier.fillMaxWidth()) { Text("Remover foto") }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Cancelar") } }
        )
    }
    mensagem?.let { m ->
        AlertDialog(
            onDismissRequest = { mensagem = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { mensagem = null }) { Text("OK") } }
        )
    }
}
