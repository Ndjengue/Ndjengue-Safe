package com.familia.controloparental.comum

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Foto da criança (ou iniciais sobre o gradiente da marca). */
@Composable
fun AvatarCrianca(foto: String?, nome: String, tamanho: Dp = 48.dp, modifier: Modifier = Modifier) {
    val bmp = remember(foto) { Imagens.deBase64(foto) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = nome,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(tamanho).clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier.size(tamanho).clip(CircleShape).background(GradienteMarca),
            contentAlignment = Alignment.Center
        ) {
            Text(iniciais(nome), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (tamanho.value * 0.38f).sp)
        }
    }
}

/** Ícone de uma app (guardado em Base64) ou uma letra como alternativa. */
@Composable
fun IconeApp(iconeBase64: String?, nome: String, tamanho: Dp = 44.dp) {
    val bmp = remember(iconeBase64) { Imagens.deBase64(iconeBase64) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = nome,
            modifier = Modifier.size(tamanho).clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            Modifier.size(tamanho).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(nome.take(1).uppercase().ifBlank { "?" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Pequeno rótulo colorido (online, bloqueado...). */
@Composable
fun EtiquetaEstado(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = cor.copy(alpha = 0.14f), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
            Spacer(Modifier.width(6.dp))
            Text(texto, color = cor, style = MaterialTheme.typography.labelLarge, fontSize = 12.sp)
        }
    }
}

@Composable
fun EstadoVazio(icone: ImageVector, titulo: String, descricao: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            descricao, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun CartaoSecao(
    titulo: String,
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (titulo.isNotBlank()) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
            }
            conteudo()
        }
    }
}

/** Mostra uma mensagem de erro/aviso por baixo de um formulário. */
@Composable
fun MensagemErro(texto: String?) {
    if (texto.isNullOrBlank()) return
    Spacer(Modifier.height(8.dp))
    Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Suppress("unused")
private val iconeReserva = Icons.Filled.Android
