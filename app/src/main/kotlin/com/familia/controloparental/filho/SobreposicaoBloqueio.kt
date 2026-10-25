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

/**
 * Ecrã de bloqueio desenhado como janela de acessibilidade (TYPE_ACCESSIBILITY_OVERLAY).
 * Ao contrário de abrir uma Activity a partir do serviço, isto funciona nos Xiaomi/Redmi (MIUI/HyperOS),
 * Samsung, etc., sem precisar da permissão "janelas pop-up em segundo plano".
 */
class SobreposicaoBloqueio(
    private val servico: AccessibilityService,
    private val aoPedir: (chave: String, rotulo: String, duracaoMin: Int) -> Unit,
    private val aoVoltar: () -> Unit
) {
    private val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var vista: View? = null
    var chaveAtual: String? = null
        private set
    val visivel: Boolean get() = vista != null

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()

    private fun fundo(cor: Int, raio: Int = 16): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(raio).toFloat()
        setColor(cor)
    }

    fun mostrar(chave: String, rotulo: String, titulo: String, detalhe: String) {
        if (vista != null && chaveAtual == chave) return
        ocultar()
        chaveAtual = chave
        var duracao = 15

        val raiz = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0B1F3A.toInt())
            setPadding(dp(28), dp(28), dp(28), dp(28))
            isFocusableInTouchMode = true
            isClickable = true
        }
        raiz.addView(TextView(servico).apply {
            text = "🔒"; textSize = 54f; gravity = Gravity.CENTER
        })
        raiz.addView(TextView(servico).apply {
            text = titulo; setTextColor(Color.WHITE); textSize = 24f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(6))
        })
        raiz.addView(TextView(servico).apply {
            text = rotulo; setTextColor(0xFF7FE3C0.toInt()); textSize = 20f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        raiz.addView(TextView(servico).apply {
            text = detalhe; setTextColor(0xFFC9D6EA.toInt()); textSize = 15f
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(24))
        })

        raiz.addView(TextView(servico).apply {
            text = "Precisas de usar? Pede aos teus pais por:"
            setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        val botoesDuracao = ArrayList<Button>()
        val linha = LinearLayout(servico).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun pintar() {
            botoesDuracao.forEachIndexed { i, b ->
                val sel = listOf(15, 30, 60)[i] == duracao
                b.background = fundo(if (sel) 0xFF1565C0.toInt() else 0xFF1C2E4A.toInt(), 20)
            }
        }
        listOf(15 to "15 min", 30 to "30 min", 60 to "1 hora").forEach { (m, texto) ->
            val b = Button(servico).apply {
                this.text = texto; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f
                setOnClickListener { duracao = m; pintar() }
                layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
            }
            botoesDuracao.add(b); linha.addView(b)
        }
        pintar()
        raiz.addView(linha)

        val estado = TextView(servico).apply {
            setTextColor(0xFF7FE3C0.toInt()); textSize = 14f; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        val pedir = Button(servico).apply {
            text = "Pedir liberação"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 17f
            background = fundo(0xFF00A86B.toInt())
            setOnClickListener {
                isEnabled = false
                text = "Pedido enviado ✔"
                estado.text = "Esta janela fecha sozinha quando os teus pais aprovarem."
                aoPedir(chave, rotulo, duracao)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                setMargins(dp(4), dp(16), dp(4), 0)
            }
        }
        raiz.addView(pedir)
        raiz.addView(estado)
        raiz.addView(Button(servico).apply {
            text = "Voltar ao ecrã inicial"; setTextColor(Color.WHITE); isAllCaps = false; textSize = 15f
            background = fundo(0x00000000)
            setOnClickListener { ocultar(); aoVoltar() }
        })

        raiz.setOnKeyListener { _, codigo, evento ->
            if (codigo == KeyEvent.KEYCODE_BACK && evento.action == KeyEvent.ACTION_UP) {
                ocultar(); aoVoltar(); true
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
        } catch (e: Exception) {
            vista = null
            chaveAtual = null
            aoVoltar()
        }
    }

    fun ocultar() {
        val v = vista ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        vista = null
        chaveAtual = null
    }
}
