package com.familia.controloparental.comum

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Senha de administrador (PIN numérico de 4 a 8 dígitos).
 *
 * Guardada no Firestore apenas como hash PBKDF2 com sal; o telemóvel da criança
 * recebe o hash e valida localmente, por isso funciona offline (secção 9).
 * O número de tentativas é limitado no próprio dispositivo (ver CacheLocal).
 */
object Seguranca {
    private const val ITERACOES = 120_000

    fun gerarSal(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun calcularHash(pin: String, sal: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(sal), ITERACOES, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verificar(pin: String, sal: String, hashEsperado: String): Boolean {
        if (sal.isBlank() || hashEsperado.isBlank()) return false
        val calculado = calcularHash(pin, sal)
        // comparação em tempo constante
        var diff = calculado.length xor hashEsperado.length
        val n = minOf(calculado.length, hashEsperado.length)
        for (i in 0 until n) diff = diff or (calculado[i].code xor hashEsperado[i].code)
        return diff == 0
    }
}
