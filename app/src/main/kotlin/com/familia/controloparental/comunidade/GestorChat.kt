package com.familia.controloparental.comunidade

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pedido de abrir uma conversa (vem do toque numa notificação de mensagem). */
object GestorChat {
    var abrirConversaId by mutableStateOf<String?>(null)
}
