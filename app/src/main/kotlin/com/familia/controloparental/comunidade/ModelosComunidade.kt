package com.familia.controloparental.comunidade

data class Contacto(val uid: String, val nome: String)

data class Conversa(val id: String, val participantes: List<String>, val nomes: Map<String, String>, val criadoEm: Long)

data class MensagemChat(
    val id: String, val autorUid: String, val texto: String, val criadoEm: Long, val expiresAt: Long,
    val editadoEm: Long = 0L, val respostaAutorUid: String = "", val respostaTexto: String = ""
)

/** Presença de uma conta: [visivel] = false quando a pessoa escolheu aparecer offline. */
data class Presenca(val visivel: Boolean, val ultimoEm: Long)

data class Publicacao(
    val id: String, val autorUid: String, val autorNome: String, val texto: String,
    val imagemBase64: String?, val criadoEm: Long, val expiresAt: Long
)

data class ComentarioPub(val id: String, val autorUid: String, val autorNome: String, val texto: String, val criadoEm: Long)

/** Quem sou eu e com quem posso falar (preenchido pelo lado dos pais ou da criança). */
data class Identidade(
    val uid: String, val nome: String, val contactos: List<Contacto>, val podeAmizade: Boolean,
    /** Só para os pais: estado REAL de cada criança (uid -> texto), ignora o "aparecer offline". */
    val estadoReal: Map<String, String> = emptyMap(),
    val ehCrianca: Boolean = false
)
