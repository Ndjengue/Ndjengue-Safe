package com.familia.controloparental.comum

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR de emparelhamento: o conteúdo é "ndjengue-safe://parear/123456". */
object CodigoQr {
    private const val PREFIXO = "ndjengue-safe://parear/"

    fun conteudoDe(codigo: String) = PREFIXO + codigo

    /** Aceita o QR da app ou só os 6 dígitos; devolve o código ou null. */
    fun extrairCodigo(texto: String?): String? {
        val t = texto?.trim() ?: return null
        val c = if (t.startsWith(PREFIXO)) t.removePrefix(PREFIXO) else t
        return c.takeIf { it.length == 6 && it.all(Char::isDigit) }
    }

    fun gerar(texto: String, tamanho: Int = 640): Bitmap {
        val matriz = QRCodeWriter().encode(
            texto, BarcodeFormat.QR_CODE, tamanho, tamanho,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        )
        val px = IntArray(tamanho * tamanho)
        for (y in 0 until tamanho) for (x in 0 until tamanho) px[y * tamanho + x] = if (matriz[x, y]) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, tamanho, tamanho, Bitmap.Config.ARGB_8888)
    }
}
