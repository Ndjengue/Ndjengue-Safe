package com.familia.controloparental.vpn

/**
 * Leitura e construção de pacotes DNS sobre UDP/IPv4 (suficiente para o filtro DNS).
 * Constrói respostas com os checksums de IP e UDP corretos.
 */
object AnalisadorPacoteDns {

    class ConsultaDns(
        val pacote: ByteArray,
        val tamanhoPacote: Int,
        val tamanhoCabecalhoIp: Int,
        val dominio: String,
        val offsetDns: Int
    ) {
        val offsetUdp: Int get() = tamanhoCabecalhoIp
        val tamanhoDns: Int get() = tamanhoPacote - offsetDns
        fun payloadDns(): ByteArray = pacote.copyOfRange(offsetDns, tamanhoPacote)
        fun ipDestino(): String = (16..19).joinToString(".") { (pacote[it].toInt() and 0xFF).toString() }
    }

    fun analisar(buf: ByteArray, tamanho: Int): ConsultaDns? {
        if (tamanho < 28) return null
        val versao = (buf[0].toInt() shr 4) and 0xF
        if (versao != 4) return null
        val ihl = (buf[0].toInt() and 0xF) * 4
        if (ihl < 20 || tamanho < ihl + 8) return null
        val protocolo = buf[9].toInt() and 0xFF
        if (protocolo != 17) return null
        val portoDestino = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        if (portoDestino != 53) return null
        val totalIp = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
        val tamanhoReal = if (totalIp in 28..tamanho) totalIp else tamanho
        val offsetDns = ihl + 8
        if (tamanhoReal < offsetDns + 12) return null
        val dominio = lerNome(buf, offsetDns + 12, tamanhoReal) ?: return null
        return ConsultaDns(buf.copyOf(tamanhoReal), tamanhoReal, ihl, dominio, offsetDns)
    }

    private fun lerNome(dados: ByteArray, inicio: Int, fim: Int): String? {
        val partes = ArrayList<String>()
        var o = inicio
        while (o < fim) {
            val n = dados[o].toInt() and 0xFF
            if (n == 0) break
            if (n >= 0xC0) return null // compressão não ocorre em perguntas
            if (o + 1 + n > fim) return null
            partes.add(String(dados, o + 1, n, Charsets.US_ASCII))
            o += 1 + n
        }
        return if (partes.isEmpty()) null else partes.joinToString(".").lowercase()
    }

    /** Fim da secção "pergunta" (nome + tipo + classe) dentro do payload DNS. */
    private fun fimDaPergunta(dns: ByteArray): Int {
        var o = 12
        while (o < dns.size) {
            val n = dns[o].toInt() and 0xFF
            if (n == 0) { o += 1; break }
            o += 1 + n
        }
        return (o + 4).coerceAtMost(dns.size)
    }

    /** Resposta DNS NXDOMAIN (domínio inexistente) para a consulta dada. */
    fun construirNxDomain(c: ConsultaDns): ByteArray {
        val consulta = c.payloadDns()
        val fim = fimDaPergunta(consulta)
        val dns = consulta.copyOfRange(0, fim)
        val rd = dns[2].toInt() and 0x01
        dns[2] = (0x80 or rd).toByte() // QR=1, RD copiado
        dns[3] = 0x83.toByte()          // RA=1, RCODE=3
        dns[6] = 0; dns[7] = 0; dns[8] = 0; dns[9] = 0; dns[10] = 0; dns[11] = 0
        return envolver(c, dns)
    }

    /** Embrulha um payload DNS numa resposta IP/UDP com origem/destino trocados. */
    fun envolver(c: ConsultaDns, payload: ByteArray): ByteArray {
        val ihl = c.tamanhoCabecalhoIp
        val total = ihl + 8 + payload.size
        val saida = ByteArray(total)
        val original = c.pacote

        // Cabeçalho IP: copia e ajusta
        System.arraycopy(original, 0, saida, 0, ihl)
        saida[2] = (total shr 8).toByte()
        saida[3] = total.toByte()
        saida[6] = 0; saida[7] = 0 // sem fragmentação
        saida[8] = 64               // TTL
        // troca IPs
        for (i in 0 until 4) {
            saida[12 + i] = original[16 + i]
            saida[16 + i] = original[12 + i]
        }
        saida[10] = 0; saida[11] = 0
        val ck = checksum(saida, 0, ihl, 0)
        saida[10] = (ck shr 8).toByte()
        saida[11] = ck.toByte()

        // UDP: troca portos
        saida[ihl] = original[ihl + 2]
        saida[ihl + 1] = original[ihl + 3]
        saida[ihl + 2] = original[ihl]
        saida[ihl + 3] = original[ihl + 1]
        val udpLen = 8 + payload.size
        saida[ihl + 4] = (udpLen shr 8).toByte()
        saida[ihl + 5] = udpLen.toByte()
        saida[ihl + 6] = 0; saida[ihl + 7] = 0
        System.arraycopy(payload, 0, saida, ihl + 8, payload.size)

        // checksum UDP com pseudo-cabeçalho
        var soma = 0L
        for (i in 12 until 20 step 2) {
            soma += ((saida[i].toInt() and 0xFF) shl 8) or (saida[i + 1].toInt() and 0xFF)
        }
        soma += 17
        soma += udpLen
        val ckUdp = checksum(saida, ihl, udpLen, soma)
        val final = if (ckUdp == 0) 0xFFFF else ckUdp
        saida[ihl + 6] = (final shr 8).toByte()
        saida[ihl + 7] = final.toByte()
        return saida
    }

    private fun checksum(dados: ByteArray, inicio: Int, tamanho: Int, somaInicial: Long): Int {
        var soma = somaInicial
        var i = inicio
        val fim = inicio + tamanho
        while (i + 1 < fim) {
            soma += ((dados[i].toInt() and 0xFF) shl 8) or (dados[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < fim) soma += (dados[i].toInt() and 0xFF) shl 8
        while ((soma shr 16) != 0L) soma = (soma and 0xFFFF) + (soma shr 16)
        return (soma.inv() and 0xFFFF).toInt()
    }
}
