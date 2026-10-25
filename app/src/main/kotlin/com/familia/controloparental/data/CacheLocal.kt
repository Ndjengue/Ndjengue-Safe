package com.familia.controloparental.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.familia.controloparental.comum.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "controlo_parental_cache")

data class ResultadoPin(
    val correto: Boolean,
    val tentativasRestantes: Int,
    val bloqueadoAteMs: Long
)

/**
 * Cache local das regras e do estado do dispositivo da criança. É o que
 * permite continuar a aplicar bloqueios mesmo sem internet (secção 8).
 * As regras são escritas aqui sempre que chega uma atualização do Firestore.
 */
class CacheLocal(private val context: Context) {

    private object Chaves {
        val APPS = stringPreferencesKey("apps_json")
        val REGRAS_URL = stringPreferencesKey("regras_url_v2_json")
        val PERFIS_HORARIO = stringPreferencesKey("perfis_horario_json")
        val FAMILIA_ID = stringPreferencesKey("familia_id")
        val DISPOSITIVO_ID = stringPreferencesKey("dispositivo_id")
        val SENHA_HASH = stringPreferencesKey("senha_admin_hash")
        val SENHA_SAL = stringPreferencesKey("senha_admin_sal")
        val LIBERACOES = stringPreferencesKey("liberacoes_json")
        val JANELA_ADMIN_ATE = longPreferencesKey("janela_admin_ate")
        val PIN_TENTATIVAS = intPreferencesKey("pin_tentativas")
        val PIN_BLOQUEADO_ATE = longPreferencesKey("pin_bloqueado_ate")
    }

    // ---------- Genéricos ----------

    suspend fun lerLong(nome: String, padrao: Long = 0L): Long =
        context.dataStore.data.first()[longPreferencesKey(nome)] ?: padrao

    suspend fun guardarLong(nome: String, valor: Long) {
        context.dataStore.edit { it[longPreferencesKey(nome)] = valor }
    }

    suspend fun lerBool(nome: String, padrao: Boolean = false): Boolean =
        context.dataStore.data.first()[booleanPreferencesKey(nome)] ?: padrao

    suspend fun guardarBool(nome: String, valor: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(nome)] = valor }
    }

    suspend fun lerTexto(nome: String): String? =
        context.dataStore.data.first()[stringPreferencesKey(nome)]

    suspend fun guardarTexto(nome: String, valor: String) {
        context.dataStore.edit { it[stringPreferencesKey(nome)] = valor }
    }

    // ---------- Identidade do dispositivo emparelhado ----------

    suspend fun guardarIdentidade(familiaId: String, dispositivoId: String) {
        context.dataStore.edit {
            it[Chaves.FAMILIA_ID] = familiaId
            it[Chaves.DISPOSITIVO_ID] = dispositivoId
        }
    }

    suspend fun lerFamiliaId(): String? = context.dataStore.data.first()[Chaves.FAMILIA_ID]
    suspend fun lerDispositivoId(): String? = context.dataStore.data.first()[Chaves.DISPOSITIVO_ID]

    suspend fun estaEmparelhado(): Boolean {
        val prefs = context.dataStore.data.first()
        return !prefs[Chaves.FAMILIA_ID].isNullOrBlank() && !prefs[Chaves.DISPOSITIVO_ID].isNullOrBlank()
    }

    fun observarEmparelhado(): Flow<Boolean> = context.dataStore.data.map {
        !it[Chaves.FAMILIA_ID].isNullOrBlank() && !it[Chaves.DISPOSITIVO_ID].isNullOrBlank()
    }

    /** Esquece tudo (desemparelhamento). */
    suspend fun limparTudo() {
        context.dataStore.edit { it.clear() }
    }

    // ---------- Senha de administrador ----------

    suspend fun guardarSegredoAdmin(hash: String, sal: String) {
        context.dataStore.edit {
            it[Chaves.SENHA_HASH] = hash
            it[Chaves.SENHA_SAL] = sal
        }
    }

    suspend fun lerSegredoAdmin(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val hash = prefs[Chaves.SENHA_HASH] ?: return null
        val sal = prefs[Chaves.SENHA_SAL] ?: return null
        return hash to sal
    }

    /** Valida o PIN localmente (offline) e aplica o limite de tentativas. */
    suspend fun validarPin(pin: String): ResultadoPin {
        val agora = System.currentTimeMillis()
        val prefs = context.dataStore.data.first()
        val bloqueadoAte = prefs[Chaves.PIN_BLOQUEADO_ATE] ?: 0L
        if (agora < bloqueadoAte) return ResultadoPin(false, 0, bloqueadoAte)

        val segredo = lerSegredoAdmin()
        val correto = segredo != null && Seguranca.verificar(pin, segredo.second, segredo.first)

        var resultado = ResultadoPin(correto, 5, 0L)
        context.dataStore.edit { p ->
            if (correto) {
                p[Chaves.PIN_TENTATIVAS] = 0
                p.remove(Chaves.PIN_BLOQUEADO_ATE)
                resultado = ResultadoPin(true, 5, 0L)
            } else {
                val tentativas = (p[Chaves.PIN_TENTATIVAS] ?: 0) + 1
                if (tentativas >= 5) {
                    p[Chaves.PIN_TENTATIVAS] = 0
                    val ate = agora + 5 * 60_000L
                    p[Chaves.PIN_BLOQUEADO_ATE] = ate
                    resultado = ResultadoPin(false, 0, ate)
                } else {
                    p[Chaves.PIN_TENTATIVAS] = tentativas
                    resultado = ResultadoPin(false, 5 - tentativas, 0L)
                }
            }
        }
        return resultado
    }

    suspend fun pinBloqueadoAte(): Long = context.dataStore.data.first()[Chaves.PIN_BLOQUEADO_ATE] ?: 0L

    /** Depois de o pai/a mãe introduzir o PIN, as proteções ficam suspensas durante alguns minutos. */
    suspend fun abrirJanelaAdmin(minutos: Int = 5) {
        context.dataStore.edit { it[Chaves.JANELA_ADMIN_ATE] = System.currentTimeMillis() + minutos * 60_000L }
    }

    suspend fun fecharJanelaAdmin() {
        context.dataStore.edit { it.remove(Chaves.JANELA_ADMIN_ATE) }
    }

    suspend fun janelaAdminAtiva(): Boolean =
        System.currentTimeMillis() < (context.dataStore.data.first()[Chaves.JANELA_ADMIN_ATE] ?: 0L)

    // ---------- Apps ----------

    suspend fun guardarApps(apps: List<AppInstalada>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject().apply {
                    put("nomePacote", app.nomePacote)
                    put("nome", app.nome)
                    put("regra", app.regra.name)
                    if (app.perfilHorarioId != null) put("perfilHorarioId", app.perfilHorarioId)
                }
            )
        }
        context.dataStore.edit { it[Chaves.APPS] = array.toString() }
    }

    private fun lerAppsDeJson(json: String?): List<AppInstalada> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AppInstalada(
                nomePacote = o.getString("nomePacote"),
                nome = o.optString("nome", ""),
                regra = try { RegraApp.valueOf(o.getString("regra")) } catch (e: Exception) { RegraApp.PERMITIDO },
                perfilHorarioId = if (o.has("perfilHorarioId")) o.getString("perfilHorarioId") else null
            )
        }
    }

    suspend fun lerApps(): List<AppInstalada> = lerAppsDeJson(context.dataStore.data.first()[Chaves.APPS])

    fun observarApps(): Flow<List<AppInstalada>> =
        context.dataStore.data.map { lerAppsDeJson(it[Chaves.APPS]) }

    // ---------- Regras de URL / redes sociais ----------

    suspend fun guardarRegrasUrl(regras: List<RegraUrl>) {
        val array = JSONArray()
        regras.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("rotulo", r.rotulo)
                    put("tipo", r.tipo.name)
                    put("ativo", r.ativo)
                    put("dominios", JSONArray(r.dominios))
                    put("pacotes", JSONArray(r.pacotes))
                    if (r.perfilHorarioId != null) put("perfilHorarioId", r.perfilHorarioId)
                }
            )
        }
        context.dataStore.edit { it[Chaves.REGRAS_URL] = array.toString() }
    }

    private fun listaDeJson(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }

    private fun lerRegrasDeJson(json: String?): List<RegraUrl> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RegraUrl(
                id = o.getString("id"),
                rotulo = o.optString("rotulo", ""),
                tipo = try { TipoRegraUrl.valueOf(o.getString("tipo")) } catch (e: Exception) { TipoRegraUrl.PERSONALIZADO },
                ativo = o.optBoolean("ativo", false),
                dominios = listaDeJson(o.optJSONArray("dominios")),
                pacotes = listaDeJson(o.optJSONArray("pacotes")),
                perfilHorarioId = if (o.has("perfilHorarioId")) o.getString("perfilHorarioId") else null
            )
        }
    }

    suspend fun lerRegrasUrl(): List<RegraUrl> = lerRegrasDeJson(context.dataStore.data.first()[Chaves.REGRAS_URL])

    fun observarRegrasUrl(): Flow<List<RegraUrl>> =
        context.dataStore.data.map { lerRegrasDeJson(it[Chaves.REGRAS_URL]) }

    // ---------- Perfis de horário ----------

    suspend fun guardarPerfisHorario(perfis: List<PerfilHorario>) {
        val array = JSONArray()
        perfis.forEach { p ->
            val janelas = JSONObject()
            p.janelasPorDia.forEach { (dia, lista) ->
                val arr = JSONArray()
                lista.forEach { j ->
                    arr.put(JSONObject().apply { put("inicio", j.inicio); put("fim", j.fim) })
                }
                janelas.put(dia.name, arr)
            }
            array.put(JSONObject().apply {
                put("id", p.id)
                put("nome", p.nome)
                put("janelas", janelas)
            })
        }
        context.dataStore.edit { it[Chaves.PERFIS_HORARIO] = array.toString() }
    }

    suspend fun lerPerfisHorario(): List<PerfilHorario> {
        val json = context.dataStore.data.first()[Chaves.PERFIS_HORARIO] ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val janelasObj = o.getJSONObject("janelas")
            val janelasPorDia = mutableMapOf<DiaSemana, List<JanelaHorario>>()
            janelasObj.keys().forEach { diaStr ->
                val dia = try { DiaSemana.valueOf(diaStr) } catch (e: Exception) { null }
                if (dia != null) {
                    val arr = janelasObj.getJSONArray(diaStr)
                    janelasPorDia[dia] = (0 until arr.length()).map { j ->
                        val jo = arr.getJSONObject(j)
                        JanelaHorario(jo.getString("inicio"), jo.getString("fim"))
                    }
                }
            }
            PerfilHorario(id = o.getString("id"), nome = o.getString("nome"), janelasPorDia = janelasPorDia)
        }
    }

    // ---------- Liberações pontuais aprovadas (chave -> expira em) ----------

    suspend fun guardarLiberacao(chave: String, ateMs: Long) {
        val atuais = lerLiberacoesBrutas().toMutableMap()
        atuais[chave] = ateMs
        gravarLiberacoes(atuais)
    }

    suspend fun lerLiberacoes(): Map<String, Long> {
        val agora = System.currentTimeMillis()
        return lerLiberacoesBrutas().filterValues { it > agora }
    }

    private suspend fun lerLiberacoesBrutas(): Map<String, Long> {
        val json = context.dataStore.data.first()[Chaves.LIBERACOES] ?: return emptyMap()
        val o = JSONObject(json)
        val mapa = mutableMapOf<String, Long>()
        o.keys().forEach { mapa[it] = o.getLong(it) }
        return mapa
    }

    private suspend fun gravarLiberacoes(mapa: Map<String, Long>) {
        val agora = System.currentTimeMillis()
        val o = JSONObject()
        mapa.filterValues { it > agora }.forEach { (k, v) -> o.put(k, v) }
        context.dataStore.edit { it[Chaves.LIBERACOES] = o.toString() }
    }
}
