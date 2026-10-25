package com.familia.controloparental.comum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream

/**
 * Utilitários de imagem. As fotos das crianças e os ícones das apps são
 * guardados em Base64 diretamente no Firestore (o Firebase Storage exige um
 * plano pago), por isso são sempre reduzidos e comprimidos primeiro.
 */
object Imagens {

    private val cache = object : LruCache<String, Bitmap>(60) {}

    /** Lê a foto escolhida, recorta ao centro em quadrado e devolve um JPEG pequeno em Base64. */
    fun fotoDeUri(contexto: Context, uri: Uri, lado: Int = 256, qualidade: Int = 80): String? {
        return try {
            val resolver = contexto.contentResolver
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
                val origem = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(origem) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val menor = minOf(info.size.width, info.size.height)
                    if (menor > lado * 2) {
                        val escala = (lado * 2f) / menor
                        decoder.setTargetSize(
                            (info.size.width * escala).toInt().coerceAtLeast(1),
                            (info.size.height * escala).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val opcoes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
                var amostra = 1
                val menor = minOf(opcoes.outWidth, opcoes.outHeight)
                while (menor / (amostra * 2) >= lado) amostra *= 2
                val decod = BitmapFactory.Options().apply { inSampleSize = amostra }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decod) }
            }
            if (bitmap == null) return null
            val l = minOf(bitmap.width, bitmap.height)
            val recortada = Bitmap.createBitmap(bitmap, (bitmap.width - l) / 2, (bitmap.height - l) / 2, l, l)
            val final = Bitmap.createScaledBitmap(recortada, lado, lado, true)
            val saida = ByteArrayOutputStream()
            final.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)
            Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun deBase64(b64: String?): Bitmap? {
        if (b64.isNullOrBlank()) return null
        val chave = b64.hashCode().toString() + ":" + b64.length
        cache.get(chave)?.let { return it }
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(chave, it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Converte o ícone de uma app (pode ser adaptativo) num PNG pequeno em Base64. */
    fun iconeParaBase64(icone: Drawable, lado: Int = 72): String? {
        return try {
            val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            icone.setBounds(0, 0, lado, lado)
            icone.draw(canvas)
            val saida = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, saida)
            Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
