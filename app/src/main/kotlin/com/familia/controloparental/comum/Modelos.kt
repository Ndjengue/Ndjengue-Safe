package com.familia.controloparental.comum

/**
 * Modelos de dados partilhados entre o modo Pai e o modo Filho.
 * Espelham o esquema do Firestore definido no documento de requisitos (secção 12),
 * com as extensões descritas no README (perfil da criança, heartbeat, alertas,
 * pedidos ao nível da família, histórico de localização).
 */

enum class RegraApp {
    PERMITIDO,
    BLOQUEADO,
    BLOQUEADO_POR_HORARIO
}

data class Familia(
    val id: String = "",
    val donoUid: String = "",
    val emailRecuperacao: String = ""
)

/** Dados da criança, preenchidos pelo pai antes/depois do emparelhamento. */
data class PerfilCrianca(
    val nome: String = "",
    val telefone: String = "",
    val email: String = "",
    /** JPEG pequeno (≈256 px) em Base64. Guardado no Firestore porque o Storage exige plano pago. */
    val fotoBase64: String? = null
)

/** Estado das proteções no telemóvel da criança, enviado em cada heartbeat. */
data class EstadoProtecao(
    val vpnAtiva: Boolean = false,
    val acessibilidadeAtiva: Boolean = false,
    val adminAtivo: Boolean = false,
    val localizacaoPermitida: Boolean = false,
    val localizacaoSempre: Boolean = false,
    val usoAppsPermitido: Boolean = false,
    val notificacoesPermitidas: Boolean = false
) {
    /** Tudo o que é essencial para o controlo funcionar. */
    val completa: Boolean
        get() = vpnAtiva && acessibilidadeAtiva && adminAtivo && localizacaoSempre && usoAppsPermitido

    val pendentes: List<String>
        get() = buildList {
            if (!acessibilidadeAtiva) add("Acessibilidade")
            if (!adminAtivo) add("Proteção contra desinstalação")
            if (!vpnAtiva) add("Filtro de sites (VPN)")
            if (!localizacaoSempre) add("Localização")
            if (!usoAppsPermitido) add("Tempo de ecrã")
        }
}

data class LocalizacaoPonto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val precisaoMetros: Float = 0f,
    val velocidadeMs: Float = 0f,
    val timestamp: Long = 0L
)

/** Se não houver sinal há mais do que isto, o dispositivo é considerado offline. */
const val LIMITE_ONLINE_MS = 2 * 60 * 1000L

data class Dispositivo(
    val id: String = "",
    val nome: String = "", // modelo do aparelho, ex.: "samsung SM-A135F"
    val crianca: PerfilCrianca = PerfilCrianca(),
    val emparelhado: Boolean = false,
    val onlineDeclarado: Boolean = false,
    /** Servidor Firestore (epoch ms) do último heartbeat. */
    val ultimoSinal: Long = 0L,
    val bateria: Int = -1,
    val aCarregar: Boolean = false,
    val rede: String = "",
    val localizacaoAtual: LocalizacaoPonto? = null,
    val rastreioAoVivoAte: Long = 0L,
    val protecao: EstadoProtecao = EstadoProtecao(),
    val uidAutenticacao: String = "",
    /** Controlado pelos pais: pede o PIN ao tentar desinstalar / desativar a proteção. */
    val pinProtecaoAtiva: Boolean = true,
    /** Controlado pelos pais: mostra o ecrã de bloqueio (apps, sites, horários). */
    val bloqueioEcraAtivo: Boolean = true,
    /** Controlado pelos pais: bloqueia já o ecrã inteiro até eles desligarem. */
    val ecraBloqueadoAgora: Boolean = false
) {
    fun estaOnline(agora: Long = System.currentTimeMillis()): Boolean =
        emparelhado && onlineDeclarado && ultimoSinal > 0 && (agora - ultimoSinal) < LIMITE_ONLINE_MS

    val nomeExibicao: String
        get() = crianca.nome.ifBlank { nome.ifBlank { "Dispositivo" } }
}

data class AppInstalada(
    val nomePacote: String = "",
    val nome: String = "",
    val iconeBase64: String? = null,
    val instaladoEm: Long = 0L,
    val regra: RegraApp = RegraApp.PERMITIDO,
    val perfilHorarioId: String? = null
)

enum class TipoRegraUrl { PERSONALIZADO, REDE_SOCIAL }

/**
 * Regra de bloqueio de sites. Para redes sociais, [dominios] e [pacotes] vêm do
 * catálogo e o mesmo interruptor bloqueia o domínio E o(s) app(s) correspondente(s).
 */
data class RegraUrl(
    val id: String = "",
    val rotulo: String = "",
    val tipo: TipoRegraUrl = TipoRegraUrl.PERSONALIZADO,
    val dominios: List<String> = emptyList(),
    val pacotes: List<String> = emptyList(),
    val ativo: Boolean = false,
    val perfilHorarioId: String? = null
) {
    val modo: RegraApp
        get() = when {
            ativo -> RegraApp.BLOQUEADO
            perfilHorarioId != null -> RegraApp.BLOQUEADO_POR_HORARIO
            else -> RegraApp.PERMITIDO
        }
}

data class JanelaHorario(
    val inicio: String = "07:00", // HH:mm, "24:00" = fim do dia
    val fim: String = "20:00"
)

enum class DiaSemana {
    DOMINGO, SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO;

    val abreviatura: String
        get() = when (this) {
            DOMINGO -> "Dom"
            SEGUNDA -> "Seg"
            TERCA -> "Ter"
            QUARTA -> "Qua"
            QUINTA -> "Qui"
            SEXTA -> "Sex"
            SABADO -> "Sáb"
        }

    val nomeCompleto: String
        get() = when (this) {
            DOMINGO -> "Domingo"
            SEGUNDA -> "Segunda-feira"
            TERCA -> "Terça-feira"
            QUARTA -> "Quarta-feira"
            QUINTA -> "Quinta-feira"
            SEXTA -> "Sexta-feira"
            SABADO -> "Sábado"
        }

    companion object {
        /** Ordem de apresentação em Portugal: a semana começa à segunda. */
        val ordemApresentacao = listOf(SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO)
    }
}

/**
 * Perfil de horário reutilizável (ex.: "Escola"). As janelas definem quando o
 * acesso é PERMITIDO; fora delas a regra bloqueada aplica-se automaticamente.
 */
data class PerfilHorario(
    val id: String = "",
    val nome: String = "",
    val janelasPorDia: Map<DiaSemana, List<JanelaHorario>> = emptyMap()
)

enum class StatusPedidoLiberacao { PENDENTE, APROVADO, RECUSADO, EXPIRADO }

enum class TipoAlvo { APP, URL }

data class PedidoLiberacao(
    val id: String = "",
    val dispositivoId: String = "",
    val criancaNome: String = "",
    /** Chave técnica do alvo: "app:<pacote>" ou "url:<regraId>". */
    val alvo: String = "",
    val rotulo: String = "",
    val tipoAlvo: TipoAlvo = TipoAlvo.APP,
    val status: StatusPedidoLiberacao = StatusPedidoLiberacao.PENDENTE,
    val criadoEm: Long = 0L,
    val duracaoMinutos: Int = 15,
    val decididoEm: Long = 0L,
    val duracaoAprovadaMinutos: Int = 0
) {
    val liberadoAte: Long
        get() = if (status == StatusPedidoLiberacao.APROVADO && decididoEm > 0)
            decididoEm + duracaoAprovadaMinutos * 60_000L else 0L
}

enum class TipoAlerta {
    DESINSTALACAO_TENTADA,
    ADMIN_DESATIVADO,
    ACESSIBILIDADE_DESATIVADA,
    VPN_DESATIVADA,
    SENHA_ADMIN_ERRADA,
    SENHA_ADMIN_USADA,
    PEDIDO_AJUDA,
    LOCALIZACAO_DESATIVADA,
    INFO
}

data class Alerta(
    val id: String = "",
    val dispositivoId: String = "",
    val criancaNome: String = "",
    val tipo: TipoAlerta = TipoAlerta.INFO,
    val mensagem: String = "",
    val criadoEm: Long = 0L,
    val lido: Boolean = false
)

data class RegistoBloqueio(
    val id: String = "",
    val tipo: TipoAlvo = TipoAlvo.APP,
    val alvo: String = "",
    val rotulo: String = "",
    val em: Long = 0L
)

data class RelatorioDiario(
    val data: String = "", // YYYY-MM-DD
    val tempoEcraPorApp: Map<String, Long> = emptyMap(), // pacote -> ms
    val totalMs: Long = 0L
)

data class Convite(
    val codigo: String = "",
    val familiaId: String = "",
    val dispositivoId: String = "",
    val expiraEm: Long = 0L
)

/** Pedido de captura de ecrã (secção 10): sempre visível para a criança. */
data class PedidoCaptura(
    val id: String = "",
    val dispositivoId: String = "",
    val estado: String = "PENDENTE", // PENDENTE, CONCLUIDA, RECUSADA, FALHOU
    val criadoEm: Long = 0L,
    val imagemBase64: String? = null
)
