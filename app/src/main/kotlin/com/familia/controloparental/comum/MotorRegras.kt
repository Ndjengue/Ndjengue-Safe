package com.familia.controloparental.comum

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Decide, neste preciso momento, se um app ou domínio deve estar bloqueado.
 * Combina as camadas do documento de requisitos:
 *   - toggle manual (secção 4): bloqueado/permitido, independente da hora
 *   - horário semanal por app/URL (secção 6): janelas em que o acesso é PERMITIDO
 *   - liberações pontuais aprovadas pelo pai (secção 7), sempre temporárias
 *
 * Funciona só com dados do cache local, por isso também aplica regras offline.
 */
object MotorRegras {

    enum class Motivo { MANUAL, HORARIO }

    data class Decisao(
        /** "app:<pacote>" ou "url:<regraId>" — usado nos pedidos de liberação. */
        val chave: String,
        val rotulo: String,
        val tipo: TipoAlvo,
        val motivo: Motivo,
        /** Texto para a criança, ex.: "Permitido hoje das 07:00 às 20:00". */
        val detalhe: String
    )

    // ---------- Tempo ----------

    fun diaDe(data: LocalDateTime): DiaSemana = when (data.dayOfWeek) {
        DayOfWeek.SUNDAY -> DiaSemana.DOMINGO
        DayOfWeek.MONDAY -> DiaSemana.SEGUNDA
        DayOfWeek.TUESDAY -> DiaSemana.TERCA
        DayOfWeek.WEDNESDAY -> DiaSemana.QUARTA
        DayOfWeek.THURSDAY -> DiaSemana.QUINTA
        DayOfWeek.FRIDAY -> DiaSemana.SEXTA
        DayOfWeek.SATURDAY -> DiaSemana.SABADO
        else -> DiaSemana.SEGUNDA
    }

    /** "07:30" -> 450; "24:00" -> 1440. */
    fun minutos(hhmm: String): Int {
        val partes = hhmm.split(":")
        val h = partes.getOrNull(0)?.toIntOrNull() ?: 0
        val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).coerceIn(0, 1440)
    }

    fun formatarMinutos(min: Int): String {
        val m = min.coerceIn(0, 1440)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun dentroDeAlgumaJanela(perfil: PerfilHorario, agora: LocalDateTime = LocalDateTime.now()): Boolean {
        val janelas = perfil.janelasPorDia[diaDe(agora)] ?: emptyList()
        val minutoAtual = agora.hour * 60 + agora.minute
        return janelas.any { j -> minutoAtual >= minutos(j.inicio) && minutoAtual < minutos(j.fim) }
    }

    fun descreverHoje(perfil: PerfilHorario, agora: LocalDateTime = LocalDateTime.now()): String {
        val janelas = (perfil.janelasPorDia[diaDe(agora)] ?: emptyList()).sortedBy { minutos(it.inicio) }
        if (janelas.isEmpty()) return "Hoje não há horas permitidas"
        return "Permitido hoje: " + janelas.joinToString(", ") { "${it.inicio}–${it.fim}" }
    }

    // ---------- Liberações ----------

    private fun liberado(chave: String, liberacoes: Map<String, Long>, agora: Long): Boolean {
        val ate = liberacoes[chave] ?: return false
        return agora < ate
    }

    // ---------- Avaliação genérica ----------

    private fun avaliar(
        manual: Boolean,
        perfil: PerfilHorario?,
        agora: LocalDateTime
    ): Pair<Motivo, String>? {
        if (manual) return Motivo.MANUAL to "Bloqueado pelos teus pais"
        if (perfil != null && !dentroDeAlgumaJanela(perfil, agora)) {
            return Motivo.HORARIO to "Fora do horário permitido (${perfil.nome}). ${descreverHoje(perfil, agora)}"
        }
        return null
    }

    fun appEstaBloqueada(app: AppInstalada, perfilHorario: PerfilHorario?): Boolean {
        return when (app.regra) {
            RegraApp.PERMITIDO -> false
            RegraApp.BLOQUEADO -> true
            RegraApp.BLOQUEADO_POR_HORARIO ->
                perfilHorario != null && !dentroDeAlgumaJanela(perfilHorario)
        }
    }

    /**
     * Devolve a decisão de bloqueio para o app em primeiro plano, ou null se
     * puder ser usado. Considera a regra do próprio app e as regras de redes
     * sociais que o incluem (o mesmo interruptor bloqueia domínio e app).
     */
    fun decidirApp(
        pacote: String,
        apps: List<AppInstalada>,
        regrasUrl: List<RegraUrl>,
        perfis: List<PerfilHorario>,
        liberacoes: Map<String, Long>,
        agoraMs: Long = System.currentTimeMillis()
    ): Decisao? {
        val agora = LocalDateTime.now()

        val app = apps.firstOrNull { it.nomePacote == pacote }
        if (app != null && app.regra != RegraApp.PERMITIDO) {
            val chave = "app:$pacote"
            if (!liberado(chave, liberacoes, agoraMs)) {
                val perfil = perfis.firstOrNull { it.id == app.perfilHorarioId }
                val resultado = avaliar(
                    manual = app.regra == RegraApp.BLOQUEADO,
                    perfil = if (app.regra == RegraApp.BLOQUEADO_POR_HORARIO) perfil else null,
                    agora = agora
                )
                if (resultado != null) {
                    return Decisao(chave, app.nome.ifBlank { pacote }, TipoAlvo.APP, resultado.first, resultado.second)
                }
            }
        }

        for (regra in regrasUrl) {
            if (pacote !in regra.pacotes) continue
            val chave = "url:${regra.id}"
            if (liberado(chave, liberacoes, agoraMs)) continue
            val perfil = perfis.firstOrNull { it.id == regra.perfilHorarioId }
            val resultado = avaliar(manual = regra.ativo, perfil = perfil, agora = agora)
            if (resultado != null) {
                return Decisao(chave, regra.rotulo, TipoAlvo.APP, resultado.first, resultado.second)
            }
        }
        return null
    }

    fun dominioCorresponde(consulta: String, dominioRegra: String): Boolean {
        val c = consulta.lowercase().trimEnd('.')
        val d = dominioRegra.lowercase().trimEnd('.')
        if (d.isBlank()) return false
        return c == d || c.endsWith(".$d")
    }

    /** Decisão para um domínio pedido via DNS (filtro VPN). */
    fun decidirDominio(
        dominio: String,
        regrasUrl: List<RegraUrl>,
        perfis: List<PerfilHorario>,
        liberacoes: Map<String, Long>,
        agoraMs: Long = System.currentTimeMillis()
    ): Decisao? {
        val agora = LocalDateTime.now()
        for (regra in regrasUrl) {
            if (regra.dominios.none { dominioCorresponde(dominio, it) }) continue
            val chave = "url:${regra.id}"
            if (liberado(chave, liberacoes, agoraMs)) continue
            val perfil = perfis.firstOrNull { it.id == regra.perfilHorarioId }
            val resultado = avaliar(manual = regra.ativo, perfil = perfil, agora = agora)
            if (resultado != null) {
                return Decisao(chave, regra.rotulo.ifBlank { dominio }, TipoAlvo.URL, resultado.first, resultado.second)
            }
        }
        return null
    }
}
