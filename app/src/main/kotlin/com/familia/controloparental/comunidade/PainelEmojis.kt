package com.familia.controloparental.comunidade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun faixa(vararg intervalos: IntRange): List<String> =
    intervalos.flatMap { it.map { cp -> String(Character.toChars(cp)) } }

/** Emojis do próprio Android (usam o tipo de letra do telemóvel, por isso ficam iguais aos do teclado). */
private val CATEGORIAS: List<Pair<String, List<String>>> = listOf(
    "😀" to faixa(0x1F600..0x1F64F, 0x1F910..0x1F92F, 0x1F970..0x1F976, 0x1F978..0x1F97A),
    "👍" to faixa(0x1F44A..0x1F450, 0x1F4AA..0x1F4AA, 0x1F64F..0x1F64F, 0x270A..0x270D, 0x1F446..0x1F449, 0x1F918..0x1F91F, 0x1F440..0x1F441, 0x1F444..0x1F445, 0x1F9D0..0x1F9D6),
    "❤️" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨", "⭐", "🌟", "🔥", "💯", "✅", "❌", "❗", "❓", "🎉", "🎊", "🎁"),
    "🐶" to faixa(0x1F400..0x1F43E, 0x1F980..0x1F9A2, 0x1F331..0x1F344),
    "🍔" to faixa(0x1F345..0x1F37F, 0x1F950..0x1F96B),
    "⚽" to faixa(0x1F3A0..0x1F3CA, 0x26BD..0x26BE, 0x1F680..0x1F6A4, 0x1F3E0..0x1F3F0)
)

@Composable
fun PainelEmojis(aoEscolher: (String) -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().height(260.dp)) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                CATEGORIAS.forEachIndexed { i, (icone, _) ->
                    Box(
                        Modifier.weight(1f).clickable { cat = i }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            icone, fontSize = 22.sp,
                            modifier = Modifier.then(if (i == cat) Modifier else Modifier),
                            color = if (i == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            HorizontalDivider()
            LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(CATEGORIAS[cat].second) { e ->
                    Box(Modifier.size(44.dp).clickable { aoEscolher(e) }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 26.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
