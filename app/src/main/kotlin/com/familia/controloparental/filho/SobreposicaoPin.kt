package com.familia.controloparental.filho

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.ResultadoPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ecrã de PIN desenhado por cima de qualquer app (TYPE_ACCESSIBILITY_OVERLAY).
 * É mostrado quando alguém tenta desinstalar ou desativar a proteção. Feito com Views
 * clássicas para não depender de um ciclo de vida de Activity.
 */
class SobreposicaoPin(
    private val servico: AccessibilityService,
    private val cache: CacheLocal,
    private val aoValidar: () -> Unit,
    private val aoCancelar: () -> Unit,
    private val aoErrar: () -> Unit
) {
    private val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val escopo = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var vista: View? = null
    private val pin = StringBuilder()
    private var txtPontos: TextView? = null
    private var txtMensagem: TextView? = null
    private var aVerificar = false

    val visivel: Boolean get() = vista != null

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()

    private fun fundoBotao(cor: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(cor)
    }

    private fun botaoTecla(texto: String, aoClicar: () -> Unit): Button = Button(servico).apply {
        this.text = texto
        textSize = 22f
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = fundoBotao(0xFF1C2638.toInt())
        setOnClickListener { aoClicar() }
        layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
    }

    fun mostrar(titulo: String, subtitulo: String) {
        if (vista != null) return
        pin.setLength(0)

        val raiz = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFA0B1220.toInt())
            setPadding(dp(28), dp(28), dp(28), dp(28))
            isFocusableInTouchMode = true
            isClickable = true
        }

        raiz.addView(TextView(servico).apply {
            text = titulo
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        raiz.addView(TextView(servico).apply {
            text = subtitulo
            setTextColor(0xFFA7B2C4.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })

        val pontos = TextView(servico).apply {
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, dp(4), 0, dp(8))
        }
        txtPontos = pontos
        raiz.addView(pontos)

        val msg = TextView(servico).apply {
            setTextColor(0xFFFF6B6B.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            minHeight = dp(22)
        }
        txtMensagem = msg
        raiz.addView(msg)

        fun linha(vararg teclas: View): LinearLayout = LinearLayout(servico).apply {
            orientation = LinearLayout.HORIZONTAL
            teclas.forEach { addView(it) }
        }

        for (inicio in listOf(1, 4, 7)) {
            raiz.addView(linha(
                botaoTecla("$inicio") { digitar("$inicio") },
                botaoTecla("${inicio + 1}") { digitar("${inicio + 1}") },
                botaoTecla("${inicio + 2}") { digitar("${inicio + 2}") }
            ))
        }
        raiz.addView(linha(
            botaoTecla("Cancelar") { cancelar() }.apply { textSize = 13f },
            botaoTecla("0") { digitar("0") },
            botaoTecla("⌫") { apagar() }
        ))

        raiz.addView(Button(servico).apply {
            text = "Confirmar"
            textSize = 17f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = fundoBotao(0xFF1565C0.toInt())
            setOnClickListener { confirmar() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                setMargins(dp(6), dp(14), dp(6), 0)
            }
        })

        raiz.setOnKeyListener { _, codigo, evento ->
            if (codigo == KeyEvent.KEYCODE_BACK && evento.action == KeyEvent.ACTION_UP) {
                cancelar(); true
            } else false
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(raiz, params)
            vista = raiz
            raiz.requestFocus()
            atualizarPontos()
        } catch (e: Exception) {
            vista = null
        }
    }

    private fun atualizarPontos() {
        val n = pin.length
        val texto = if (n == 0) "○ ○ ○ ○" else "●".repeat(n).toCharArray().joinToString(" ")
        txtPontos?.text = texto
    }

    private fun digitar(d: String) {
        if (aVerificar || pin.length >= 8) return
        pin.append(d)
        txtMensagem?.text = ""
        atualizarPontos()
    }

    private fun apagar() {
        if (aVerificar || pin.isEmpty()) return
        pin.setLength(pin.length - 1)
        atualizarPontos()
    }

    private fun cancelar() {
        ocultar()
        aoCancelar()
    }

    private fun confirmar() {
        if (aVerificar) return
        if (pin.length < 4) {
            txtMensagem?.text = "O PIN tem pelo menos 4 dígitos."
            return
        }
        aVerificar = true
        val tentativa = pin.toString()
        escopo.launch {
            val r: ResultadoPin = cache.validarPin(tentativa)
            aVerificar = false
            if (r.correto) {
                cache.abrirJanelaAdmin(5)
                ocultar()
                aoValidar()
            } else {
                pin.setLength(0)
                atualizarPontos()
                aoErrar()
                txtMensagem?.text = if (r.bloqueadoAteMs > System.currentTimeMillis()) {
                    val hora = SimpleDateFormat("HH:mm", Locale("pt", "PT")).format(Date(r.bloqueadoAteMs))
                    "Demasiadas tentativas. Tenta novamente às $hora."
                } else {
                    "PIN incorreto. Restam ${r.tentativasRestantes} tentativa(s)."
                }
            }
        }
    }

    fun ocultar() {
        val v = vista ?: return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
        vista = null
        pin.setLength(0)
    }

    fun destruir() {
        ocultar()
        escopo.cancel()
    }
}
