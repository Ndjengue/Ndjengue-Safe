package com.familia.controloparental.pai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.RegraApp

private fun lancar(ctx: Context, intent: Intent): Boolean {
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/** Abre a posição no Google Maps do telemóvel do pai (sem chave de API nem custos). */
fun abrirNoGoogleMaps(ctx: Context, lat: Double, lng: Double, nome: String) {
    val rotulo = Uri.encode(nome)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($rotulo)"))
    geo.setPackage("com.google.android.apps.maps")
    if (!lancar(ctx, geo)) {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
        lancar(ctx, web)
    }
}

/** Inicia a navegação passo a passo até à criança. */
fun navegarAteCrianca(ctx: Context, lat: Double, lng: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
    nav.setPackage("com.google.android.apps.maps")
    if (!lancar(ctx, nav)) {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
        lancar(ctx, web)
    }
}

fun ligarPara(ctx: Context, telefone: String) {
    lancar(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(telefone)}")))
}

fun enviarSms(ctx: Context, telefone: String) {
    lancar(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(telefone)}")))
}

fun enviarEmail(ctx: Context, email: String) {
    lancar(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
}

/** Três opções: Livre / Bloqueada / Por horário (usado em apps e sites). */
@Composable
fun SeletorModo(modo: RegraApp, aoEscolher: (RegraApp) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier) {
        FilterChip(selected = modo == RegraApp.PERMITIDO, onClick = { aoEscolher(RegraApp.PERMITIDO) }, label = { Text("Livre") })
        FilterChip(selected = modo == RegraApp.BLOQUEADO, onClick = { aoEscolher(RegraApp.BLOQUEADO) }, label = { Text("Bloqueada") })
        FilterChip(
            selected = modo == RegraApp.BLOQUEADO_POR_HORARIO,
            onClick = { aoEscolher(RegraApp.BLOQUEADO_POR_HORARIO) },
            label = { Text("Horário") }
        )
    }
}
