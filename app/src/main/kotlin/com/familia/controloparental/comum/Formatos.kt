package com.familia.controloparental.comum

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

fun formatarDuracao(ms: Long): String {
    if (ms <= 0) return "0 min"
    val minutosTotal = ms / 60_000
    val h = minutosTotal / 60
    val m = minutosTotal % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        minutosTotal == 0L -> "< 1 min"
        else -> "$m min"
    }
}

fun tempoDecorrido(desde: Long, agora: Long = System.currentTimeMillis()): String {
    if (desde <= 0) return "nunca"
    val seg = ((agora - desde) / 1000).coerceAtLeast(0)
    return when {
        seg < 20 -> "agora mesmo"
        seg < 60 -> "há ${seg} s"
        seg < 3600 -> "há ${seg / 60} min"
        seg < 86_400 -> "há ${seg / 3600} h"
        else -> "há ${seg / 86_400} d"
    }
}

fun formatarHora(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(ms))

fun formatarDataHora(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale("pt", "PT")).format(Date(ms))

fun formatarDataCurta(iso: String): String {
    return try {
        val d = LocalDate.parse(iso)
        "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    } catch (e: Exception) {
        iso
    }
}

fun hojeIso(): String = LocalDate.now().toString()

fun nomeDiaCurto(iso: String): String {
    return try {
        when (LocalDate.parse(iso).dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "Seg"
            java.time.DayOfWeek.TUESDAY -> "Ter"
            java.time.DayOfWeek.WEDNESDAY -> "Qua"
            java.time.DayOfWeek.THURSDAY -> "Qui"
            java.time.DayOfWeek.FRIDAY -> "Sex"
            java.time.DayOfWeek.SATURDAY -> "Sáb"
            java.time.DayOfWeek.SUNDAY -> "Dom"
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }
}

fun iniciais(nome: String): String {
    val partes = nome.trim().split(" ").filter { it.isNotBlank() }
    return when {
        partes.isEmpty() -> "?"
        partes.size == 1 -> partes[0].take(1).uppercase()
        else -> (partes.first().take(1) + partes.last().take(1)).uppercase()
    }
}
