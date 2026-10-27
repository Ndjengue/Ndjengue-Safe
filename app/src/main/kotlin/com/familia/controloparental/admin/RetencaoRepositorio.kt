package com.familia.controloparental.admin

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Política de retenção, painel e limpeza do Super Administrador (plano gratuito Spark).
 *
 * Sem Cloud Functions: a eliminação automática diária é feita pelo TTL do Firestore sobre o campo
 * `expiresAt` (ver README). Esta classe cobre o que o TTL não faz: recálculo das datas quando a
 * política muda, eliminação em cascata imediata e registo operacional.
 */
class RetencaoRepositorio(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val colecoesComExpiracao = listOf("mensagens", "publicacoes", "comentarios", "reacoes")
    private val milisPorDia = 86_400_000L

    suspend fun ehSuperAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }

    fun observarPolitica(): Flow<PoliticaRetencao> = callbackFlow {
        val reg = db.collection("config").document("retencao").addSnapshotListener { snap, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }
            val d = snap
            if (d == null || !d.exists()) {
                trySend(PoliticaRetencao())
            } else {
                trySend(
                    PoliticaRetencao(
                        dias = (d.getLong("dias") ?: LimitesRetencao.PADRAO_DIAS.toLong()).toInt(),
                        alteradoPorEmail = d.getString("alteradoPorEmail") ?: "",
                        alteradoEm = d.getTimestamp("alteradoEm")?.toDate()?.time ?: 0L,
                        definida = true
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    /** Valida e grava a política e o registo de auditoria no mesmo lote. */
    suspend fun guardarPolitica(novoDias: Int, anterior: PoliticaRetencao) {
        require(novoDias in LimitesRetencao.MIN_DIAS..LimitesRetencao.MAX_DIAS) {
            "O período tem de estar entre ${LimitesRetencao.MIN_DIAS} e ${LimitesRetencao.MAX_DIAS} dias."
        }
        val user = auth.currentUser ?: throw IllegalStateException("Sessão inválida.")
        val lote = db.batch()
        lote.set(
            db.collection("config").document("retencao"),
            mapOf(
                "dias" to novoDias,
                "alteradoPor" to user.uid,
                "alteradoPorEmail" to (user.email ?: ""),
                "alteradoEm" to FieldValue.serverTimestamp()
            )
        )
        lote.set(
            db.collection("auditoria").document(),
            mapOf(
                "adminUid" to user.uid,
                "adminEmail" to (user.email ?: ""),
                "acao" to "ALTERAR_RETENCAO",
                "valorAnterior" to anterior.dias,
                "valorNovo" to novoDias,
                "resultado" to "OK",
                "criadoEm" to FieldValue.serverTimestamp()
            )
        )
        lote.commit().await()
    }

    /**
     * Recalcula expiresAt = criadoEm + dias para tudo o que já existe, sem reiniciar a idade.
     * Devolve o número de documentos atualizados. Retomável: repetir é idempotente.
     */
    suspend fun recalcularExpiracoes(dias: Int, progresso: (String) -> Unit = {}): Long {
        var total = 0L
        for (colecao in colecoesComExpiracao) {
            progresso("A recalcular $colecao…")
            var ultimo: DocumentSnapshot? = null
            while (true) {
                var q: Query = db.collection(colecao).orderBy("criadoEm").limit(400)
                if (ultimo != null) q = q.startAfter(ultimo)
                val pagina = q.get().await()
                if (pagina.isEmpty) break
                val lote = db.batch()
                var n = 0
                for (doc in pagina.documents) {
                    val criado = doc.getTimestamp("criadoEm")?.toDate()?.time ?: continue
                    lote.update(doc.reference, "expiresAt", Timestamp(Date(criado + dias * milisPorDia)))
                    n++
                }
                if (n > 0) lote.commit().await()
                total += n
                ultimo = pagina.documents.last()
                if (pagina.size() < 400) break
            }
        }
        return total
    }

    private suspend fun contar(q: Query): Long =
        q.count().get(AggregateSource.SERVER).await().count

    /** Soma do campo anexoBytes (Base64 guardado no documento). Inclui expirados ainda por eliminar. */
    private suspend fun somarAnexos(colecao: String): Long {
        val campo = AggregateField.sum("anexoBytes")
        val r = db.collection(colecao).aggregate(campo).get(AggregateSource.SERVER).await()
        return (r.get(campo) as? Number)?.toLong() ?: 0L
    }

    /** Contagens reais; o conteúdo privado nunca é descarregado. */
    suspend fun resumo(): ResumoPainel {
        val agora = Timestamp.now()
        fun ativos(c: String) = db.collection(c).whereGreaterThan("expiresAt", agora)
        var pendentes = 0L
        for (c in colecoesComExpiracao + "chamadas") {
            pendentes += contar(db.collection(c).whereLessThanOrEqualTo("expiresAt", agora))
        }
        return ResumoPainel(
            encarregados = contar(db.collection("familias")),
            criancas = contar(db.collectionGroup("dispositivos")),
            mensagens = contar(ativos("mensagens")),
            publicacoes = contar(ativos("publicacoes")),
            comentarios = contar(ativos("comentarios")),
            reacoes = contar(ativos("reacoes")),
            chamadas = contar(ativos("chamadas")),
            expiradosPendentes = pendentes,
            contasAtivas = contar(
                db.collection("atividade").whereGreaterThan("ultimaAtividade", Timestamp(Date(System.currentTimeMillis() - 7 * milisPorDia)))
            ),
            bytesAnexos = somarAnexos("mensagens") + somarAnexos("publicacoes")
        )
    }

    fun observarLimpezas(): Flow<List<ExecucaoLimpeza>> = callbackFlow {
        val reg = db.collection("limpezas").orderBy("inicio", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { d ->
                    ExecucaoLimpeza(
                        id = d.id,
                        inicio = d.getTimestamp("inicio")?.toDate()?.time ?: 0L,
                        fim = d.getTimestamp("fim")?.toDate()?.time ?: 0L,
                        mensagens = d.getLong("mensagens") ?: 0,
                        publicacoes = d.getLong("publicacoes") ?: 0,
                        comentarios = d.getLong("comentarios") ?: 0,
                        reacoes = d.getLong("reacoes") ?: 0,
                        chamadas = d.getLong("chamadas") ?: 0,
                        falhas = d.getLong("falhas") ?: 0,
                        estado = d.getString("estado") ?: ""
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun observarAuditoria(): Flow<List<RegistoAuditoria>> = callbackFlow {
        val reg = db.collection("auditoria").orderBy("criadoEm", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, erro ->
                if (erro != null) {
                    close(erro)
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.map { d ->
                    RegistoAuditoria(
                        id = d.id,
                        adminEmail = d.getString("adminEmail") ?: "",
                        acao = d.getString("acao") ?: "",
                        valorAnterior = d.getLong("valorAnterior") ?: 0,
                        valorNovo = d.getLong("valorNovo") ?: 0,
                        criadoEm = d.getTimestamp("criadoEm")?.toDate()?.time ?: 0L,
                        resultado = d.getString("resultado") ?: ""
                    )
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Elimina já o que expirou, em lotes de 400 e em cascata:
     * publicações expiradas → os seus comentários e reações → restantes expirados.
     * Cada falha de lote tem até 3 tentativas; depois é contada e o resto continua.
     */
    /** Há pelo menos um documento expirado? (consulta barata, 1 leitura por coleção) */
    suspend fun haExpirados(): Boolean {
        val agora = Timestamp.now()
        for (c in listOf("mensagens", "publicacoes", "comentarios", "reacoes", "sinais")) {
            val n = try {
                db.collection(c).whereLessThanOrEqualTo("expiresAt", agora).limit(1).get().await().size()
            } catch (_: Exception) { 0 }
            if (n > 0) return true
        }
        return false
    }

    suspend fun executarLimpeza(): ExecucaoLimpeza {
        val inicio = Timestamp.now()
        val ref = db.collection("limpezas").document(UUID.randomUUID().toString())
        ref.set(mapOf("inicio" to FieldValue.serverTimestamp(), "estado" to "EM_CURSO", "falhas" to 0)).await()
        // Hora do SERVIDOR (não a do telemóvel): evita apagar cedo/tarde se o relógio estiver errado.
        val agoraServidor: Timestamp = try {
            ref.get().await().getTimestamp("inicio") ?: inicio
        } catch (_: Exception) {
            inicio
        }

        var falhas = 0L
        val contagem = HashMap<String, Long>()

        suspend fun apagarLote(docs: List<DocumentSnapshot>, chave: String): Boolean {
            if (docs.isEmpty()) return true
            repeat(3) {
                try {
                    val lote = db.batch()
                    docs.forEach { lote.delete(it.reference) }
                    lote.commit().await()
                    contagem[chave] = (contagem[chave] ?: 0) + docs.size
                    return true
                } catch (_: Exception) {
                }
            }
            falhas++
            return false
        }

        suspend fun apagarExpirados(colecao: String, apagados: MutableList<String>? = null) {
            while (true) {
                val docs = try {
                    db.collection(colecao).whereLessThanOrEqualTo("expiresAt", agoraServidor).limit(400).get().await().documents
                } catch (_: Exception) {
                    falhas++
                    return
                }
                if (docs.isEmpty()) return
                apagados?.addAll(docs.map { it.id })
                if (!apagarLote(docs, colecao)) return
                if (docs.size < 400) return
            }
        }

        val publicacoesApagadas = mutableListOf<String>()
        apagarExpirados("publicacoes", publicacoesApagadas)
        for (dependente in listOf("comentarios", "reacoes")) {
            for (grupo in publicacoesApagadas.chunked(30)) {
                try {
                    val docs = db.collection(dependente).whereIn("publicacaoId", grupo).get().await().documents
                    docs.chunked(400).forEach { apagarLote(it, dependente) }
                } catch (_: Exception) {
                    falhas++
                }
            }
            apagarExpirados(dependente)
        }
        apagarExpirados("mensagens")
        apagarExpirados("chamadas")
        apagarExpirados("sinais")

        val total = contagem.values.sum()
        val estado = when {
            falhas == 0L -> "CONCLUIDA"
            total > 0 -> "CONCLUIDA_COM_ERROS"
            else -> "FALHADA"
        }
        val fim = Timestamp.now()
        ref.update(
            mapOf(
                "fim" to fim, "estado" to estado, "falhas" to falhas,
                "mensagens" to (contagem["mensagens"] ?: 0), "publicacoes" to (contagem["publicacoes"] ?: 0),
                "comentarios" to (contagem["comentarios"] ?: 0), "reacoes" to (contagem["reacoes"] ?: 0),
                "chamadas" to (contagem["chamadas"] ?: 0)
            )
        ).await()
        return ExecucaoLimpeza(
            id = ref.id, inicio = inicio.toDate().time, fim = fim.toDate().time,
            mensagens = contagem["mensagens"] ?: 0, publicacoes = contagem["publicacoes"] ?: 0,
            comentarios = contagem["comentarios"] ?: 0, reacoes = contagem["reacoes"] ?: 0,
            chamadas = contagem["chamadas"] ?: 0, falhas = falhas, estado = estado
        )
    }

    /**
     * "Limpar base de dados": apaga TODO o conteúdo temporário (mensagens, publicações, comentários,
     * reações, registo de chamadas, sinais de chamada e presença), expirado ou não, para libertar espaço.
     * Mantém contas, famílias, dispositivos, regras e conversas (as amizades não se perdem).
     * Devolve o total de documentos apagados por coleção.
     */
    suspend fun limparBaseDeDados(progresso: (String) -> Unit = {}): Map<String, Long> {
        if (!ehSuperAdmin()) throw IllegalStateException("Só o super administrador pode limpar a base de dados.")
        val ref = db.collection("limpezas").document(UUID.randomUUID().toString())
        ref.set(mapOf("inicio" to FieldValue.serverTimestamp(), "estado" to "EM_CURSO", "falhas" to 0, "tipo" to "TOTAL")).await()
        val total = LinkedHashMap<String, Long>()
        var falhas = 0L
        for (colecao in listOf("comentarios", "reacoes", "publicacoes", "mensagens", "chamadas", "sinais", "presenca")) {
            progresso("A limpar $colecao…")
            var n = 0L
            while (true) {
                val docs = try {
                    db.collection(colecao).limit(400).get().await().documents
                } catch (_: Exception) {
                    falhas++; break
                }
                if (docs.isEmpty()) break
                var ok = false
                repeat(3) {
                    if (!ok) try {
                        val lote = db.batch()
                        docs.forEach { lote.delete(it.reference) }
                        lote.commit().await()
                        ok = true
                    } catch (_: Exception) {
                    }
                }
                if (!ok) { falhas++; break }
                n += docs.size
                progresso("A limpar $colecao… $n")
            }
            total[colecao] = n
        }
        ref.update(
            mapOf(
                "fim" to FieldValue.serverTimestamp(), "estado" to if (falhas == 0L) "CONCLUIDA" else "CONCLUIDA_COM_ERROS",
                "falhas" to falhas, "mensagens" to (total["mensagens"] ?: 0), "publicacoes" to (total["publicacoes"] ?: 0),
                "comentarios" to (total["comentarios"] ?: 0), "reacoes" to (total["reacoes"] ?: 0), "chamadas" to (total["chamadas"] ?: 0)
            )
        ).await()
        return total
    }
}
