package com.sonkkeut.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.*

/** Visible status is concise; the original message remains available to speech and accessibility. */
internal object NativeStatusText {
    fun compact(text: String): String = when {
        text.contains("화면 종류") -> "화면을 확인하지 못했어요. 다시 확인해 주세요."
        text.contains("일부 메뉴") || text.contains("이해하지") -> "주문을 이해하지 못했어요. 메뉴와 수량을 다시 입력해 주세요."
        text.contains("찾지 못") -> "메뉴를 찾지 못했어요. 다른 후보나 화면을 확인해 주세요."
        text.contains("입력란에 넣") -> "수량과 옵션을 입력하고 주문을 확인해 주세요."
        text.contains("이 주문으로 안내") -> "주문을 확인했어요. 화면에서 메뉴를 찾습니다."
        text.length>65 -> text.substringBefore(".").take(65)
        else -> text
    }
}

@androidx.compose.runtime.Composable
internal fun NativeStatus(message: String) {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val style=androidx.compose.material3.MaterialTheme.typography.titleMedium
    val measurer=androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxWidth()) {
        val width=with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val brief=NativeStatusText.compact(message)
        val fallback=when {
            message.contains("찾지 못") -> "메뉴 확인 필요"
            message.contains("주문") -> "주문 확인 필요"
            else -> "화면 확인 필요"
        }
        val text=if(measurer.measure(brief,style=style,constraints=androidx.compose.ui.unit.Constraints(maxWidth=width)).lineCount<=2) brief else fallback
        androidx.compose.material3.Text(text,style=style,modifier=androidx.compose.ui.Modifier.semantics {
            liveRegion=androidx.compose.ui.semantics.LiveRegionMode.Polite
            contentDescription=message
        })
    }
}
