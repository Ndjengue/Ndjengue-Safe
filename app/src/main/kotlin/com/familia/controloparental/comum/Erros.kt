package com.familia.controloparental.comum

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Traduz qualquer erro técnico (Firebase, rede, etc.) para uma mensagem em
 * português, clara para um pai/mãe. Nenhum texto em inglês chega ao ecrã.
 */
fun traduzirErro(e: Throwable?): String {
    if (e == null) return "Ocorreu um erro inesperado. Tenta novamente."
    return when (e) {
        is FirebaseAuthWeakPasswordException ->
            "A senha é demasiado fraca. Usa pelo menos 6 caracteres."
        is FirebaseAuthUserCollisionException ->
            "Já existe uma conta com este e-mail. Tenta entrar ou recupera a senha."
        is FirebaseAuthInvalidUserException -> when (e.errorCode) {
            "ERROR_USER_DISABLED" -> "Esta conta foi desativada."
            "ERROR_USER_TOKEN_EXPIRED" -> "A sessão expirou. Inicia sessão novamente."
            else -> "Não existe nenhuma conta com este e-mail."
        }
        is FirebaseAuthInvalidCredentialsException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "O formato do e-mail não é válido."
            "ERROR_WRONG_PASSWORD" -> "Senha incorreta."
            else -> "E-mail ou senha incorretos."
        }
        is FirebaseAuthRecentLoginRequiredException ->
            "Por segurança, volta a iniciar sessão e tenta novamente."
        is FirebaseTooManyRequestsException ->
            "Demasiadas tentativas. Aguarda uns minutos e tenta de novo."
        is FirebaseNetworkException ->
            "Sem ligação à internet. Verifica a rede e tenta novamente."
        is FirebaseAuthException -> traduzirCodigoAuth(e.errorCode)
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Sem permissão para esta operação. Inicia sessão novamente."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Serviço indisponível ou sem ligação à internet. Tenta novamente."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "A operação demorou demasiado. Verifica a ligação e tenta novamente."
            FirebaseFirestoreException.Code.NOT_FOUND ->
                "Não foi possível encontrar os dados pedidos."
            FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                "Esses dados já existem."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Limite de utilização do serviço atingido. Tenta mais tarde."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Sessão inválida. Inicia sessão novamente."
            else -> "Não foi possível concluir a operação. Tenta novamente."
        }
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.io.IOException ->
            "Sem ligação à internet. Verifica a rede e tenta novamente."
        is SecurityException -> "Falta uma permissão do Android para concluir esta ação."
        else -> {
            val m = (e.message ?: "").lowercase()
            when {
                "network" in m || "unable to resolve host" in m || "timeout" in m ->
                    "Sem ligação à internet. Verifica a rede e tenta novamente."
                "permission" in m -> "Sem permissão para esta operação."
                else -> "Ocorreu um erro inesperado. Tenta novamente."
            }
        }
    }
}

private fun traduzirCodigoAuth(codigo: String?): String = when (codigo) {
    "ERROR_INVALID_EMAIL" -> "O formato do e-mail não é válido."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "Já existe uma conta com este e-mail."
    "ERROR_WEAK_PASSWORD" -> "A senha é demasiado fraca. Usa pelo menos 6 caracteres."
    "ERROR_USER_NOT_FOUND" -> "Não existe nenhuma conta com este e-mail."
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "E-mail ou senha incorretos."
    "ERROR_USER_DISABLED" -> "Esta conta foi desativada."
    "ERROR_TOO_MANY_REQUESTS" -> "Demasiadas tentativas. Aguarda uns minutos e tenta de novo."
    "ERROR_NETWORK_REQUEST_FAILED" -> "Sem ligação à internet. Verifica a rede e tenta novamente."
    "ERROR_OPERATION_NOT_ALLOWED" -> "Este método de acesso não está ativo no servidor."
    "ERROR_REQUIRES_RECENT_LOGIN" -> "Por segurança, volta a iniciar sessão e tenta novamente."
    "ERROR_INVALID_ACTION_CODE", "ERROR_EXPIRED_ACTION_CODE" -> "O link já não é válido. Pede um novo."
    else -> "Não foi possível autenticar. Tenta novamente."
}

// ---------- Validação de formulários (mensagens em português) ----------

private val PADRAO_EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

fun validarEmail(email: String): String? = when {
    email.isBlank() -> "Indica o e-mail."
    !PADRAO_EMAIL.matches(email.trim()) -> "O formato do e-mail não é válido."
    else -> null
}

fun validarSenhaConta(senha: String): String? = when {
    senha.isEmpty() -> "Indica a senha."
    senha.length < 6 -> "A senha deve ter pelo menos 6 caracteres."
    else -> null
}

fun validarPinAdmin(pin: String): String? = when {
    pin.isEmpty() -> "Indica a senha de administrador."
    !pin.all { it.isDigit() } -> "Usa apenas números (4 a 8 dígitos)."
    pin.length < 4 -> "Usa pelo menos 4 dígitos."
    pin.length > 8 -> "Usa no máximo 8 dígitos."
    else -> null
}

fun validarTelefone(tel: String): String? {
    if (tel.isBlank()) return null // opcional
    val digitos = tel.count { it.isDigit() }
    return if (digitos < 7 || digitos > 15) "O número de telemóvel não parece válido." else null
}

fun validarDominio(entrada: String): String? {
    val d = normalizarDominio(entrada)
    return when {
        d.isBlank() -> "Indica o domínio, por exemplo: exemplo.com"
        !d.contains('.') -> "Indica um domínio válido, por exemplo: exemplo.com"
        d.contains(' ') || d.length > 253 -> "O domínio não é válido."
        !Regex("^[a-z0-9.\\-]+$").matches(d) -> "Usa apenas letras, números, pontos e hífenes."
        else -> null
    }
}

/** "https://www.Exemplo.com/pagina" -> "exemplo.com" (o "www." é irrelevante: subdomínios também ficam bloqueados). */
fun normalizarDominio(entrada: String): String {
    var d = entrada.trim().lowercase()
    d = d.removePrefix("https://").removePrefix("http://")
    d = d.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    d = d.removePrefix("www.")
    return d.trim('.')
}
