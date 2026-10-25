package com.familia.controloparental.admin

/** Limites da retenção (documentados no ecrã e validados também nas regras do Firestore). */
object LimitesRetencao {
    const val MIN_DIAS = 1
    const val MAX_DIAS = 365
    const val PADRAO_DIAS = 7
    /** Retenção própria dos metadados de chamadas (nunca há gravação de áudio/vídeo). */
    const val CHAMADAS_DIAS = 30
}

data class PoliticaRetencao(
    val dias: Int = LimitesRetencao.PADRAO_DIAS,
    val alteradoPorEmail: String = "",
    val alteradoEm: Long = 0L,
    val definida: Boolean = false
)

data class ResumoPainel(
    val encarregados: Long = 0,
    val criancas: Long = 0,
    val mensagens: Long = 0,
    val publicacoes: Long = 0,
    val comentarios: Long = 0,
    val reacoes: Long = 0,
    val chamadas: Long = 0,
    val expiradosPendentes: Long = 0,
    val contasAtivas: Long = 0,
    val bytesAnexos: Long = 0
)

data class ExecucaoLimpeza(
    val id: String = "",
    val inicio: Long = 0L,
    val fim: Long = 0L,
    val mensagens: Long = 0,
    val publicacoes: Long = 0,
    val comentarios: Long = 0,
    val reacoes: Long = 0,
    val chamadas: Long = 0,
    val falhas: Long = 0,
    val estado: String = "" // EM_CURSO, CONCLUIDA, CONCLUIDA_COM_ERROS, FALHADA
)

data class RegistoAuditoria(
    val id: String = "",
    val adminEmail: String = "",
    val acao: String = "",
    val valorAnterior: Long = 0,
    val valorNovo: Long = 0,
    val criadoEm: Long = 0L,
    val resultado: String = ""
)
