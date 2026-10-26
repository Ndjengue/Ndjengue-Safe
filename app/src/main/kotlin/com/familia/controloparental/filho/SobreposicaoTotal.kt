package com.familia.controloparental.filho

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bloqueio total do ecrã, ligado e desligado pelos pais na app deles. Cobre tudo (TYPE_ACCESSIBILITY_OVERLAY)
 * e só desaparece quando os pais o desligam. Não pede PIN.
 */
class SobreposicaoTotal(private val servico: AccessibilityService) {
    private val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var vista: View? = null
    val visivel: Boolean get() = vista != null

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()

    fun mostrar(nome: String) {
        if (vista != null) return
        val raiz = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF0B1F3A.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
            isFocusable = true
        }
        raiz.addView(TextView(servico).apply { text = "🔒"; textSize = 64f; gravity = Gravity.CENTER })
        raiz.addView(TextView(servico).apply {
            text = "Ecrã bloqueado pelos teus pais"
            setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        })
        raiz.addView(TextView(servico).apply {
            text = if (nome.isBlank()) "Vai ficar livre quando eles o permitirem." else "$nome, vai ficar livre quando os teus pais o permitirem."
            setTextColor(0xFFA7B2C4.toInt()); textSize = 16f; gravity = Gravity.CENTER
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(raiz, params)
            vista = raiz
        } catch (_: Exception) {
            vista = null
        }
    }

    fun ocultar() {
        val v = vista ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        vista = null
    }
}
