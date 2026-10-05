package com.sonkkeut.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Preserves the selected font size; long orders use pages rather than scrolling or truncation. */
@Composable
internal fun NativeOrderConfirmation(order: NativeOrder, progress: String, modifier: Modifier = Modifier) {
    val summary=order.confirmation()+" "+progress
    var page by remember(summary) { mutableIntStateOf(0) }
    val pageCount=remember(summary) { mutableIntStateOf(1) }
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val style=MaterialTheme.typography.bodyLarge
    Column(modifier.testTag("orderConfirmation"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("주문 확인",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("orderTitle"))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("orderTextRegion")) {
            val width=with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
            val height=with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val pages=remember(summary,width,height,style,density.fontScale) {
                val result=mutableListOf<String>()
                var start=0
                while(start<summary.length) {
                    var low=start+1
                    var high=summary.length
                    var end=start+1
                    while(low<=high) {
                        val middle=(low+high)/2
                        val candidate=measurer.measure(summary.substring(start,middle),style=style,constraints=Constraints(maxWidth=width))
                        if(candidate.size.height<=height) { end=middle; low=middle+1 } else high=middle-1
                    }
                    if(end<summary.length && Character.isHighSurrogate(summary[end-1]) && Character.isLowSurrogate(summary[end])) end--
                    if(end<=start) end=(start+2).coerceAtMost(summary.length)
                    result+=summary.substring(start,end)
                    start=end
                }
                result
            }
            val current=page.coerceIn(0,pages.lastIndex)
            Text(pages[current],style=style,modifier=Modifier.fillMaxWidth().testTag("orderPage").semantics {
                liveRegion=LiveRegionMode.Polite
                contentDescription="주문 내용 ${current+1}/${pages.size}. ${pages[current]}"
            })
            // Controls are placed outside the measured text area below.
            SideEffect { if(page!=current) page=current }
            SideEffect { pageCount.value=pages.size }
        }
        Row(Modifier.fillMaxWidth().testTag("orderNavigation").heightIn(min=56.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={page--},enabled=page>0,modifier=Modifier.weight(1f)) { Text("이전") }
            TextButton(onClick={page++},enabled=page<pageCount.value-1,modifier=Modifier.weight(1f)) { Text("다음") }
        }
    }
}
