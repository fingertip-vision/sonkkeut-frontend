package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SignalSessionTheme(light: Boolean, content: @Composable () -> Unit) {
    val colors=if(light) lightColorScheme(
        primary=Color(0xFF5D4A06),onPrimary=SignalPaper,primaryContainer=SignalGold,onPrimaryContainer=SignalInk,
        background=SignalPaper,onBackground=SignalInk,surface=SignalPaper,onSurface=SignalInk,
        surfaceVariant=Color(0xFFE5E8DD),onSurfaceVariant=Color(0xFF465750),outline=Color(0xFF69776E)
    ) else darkColorScheme(
        primary=SignalGold,onPrimary=SignalInk,primaryContainer=SignalGold,onPrimaryContainer=SignalInk,
        background=SignalInk,onBackground=SignalPaper,surface=SignalInk,onSurface=SignalPaper,
        surfaceVariant=Color(0xFF1C2624),onSurfaceVariant=Color(0xFFBAC5BF),outline=Color(0xFF596960)
    )
    val emphasize=MaterialTheme.typography.bodyLarge.fontWeight==FontWeight.Bold
    fun type(size: Int, line: Int, bold: Boolean=false)=TextStyle(fontFamily=SignalFont,fontSize=size.sp,lineHeight=line.sp,
        fontWeight=if(emphasize) FontWeight.Bold else if(bold) FontWeight.SemiBold else FontWeight.Normal,lineBreak=LineBreak.Heading)
    MaterialTheme(colorScheme=colors,shapes=Shapes(small=RoundedCornerShape(16.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(24.dp)),
        typography=Typography(titleLarge=type(23,30,true),titleMedium=type(17,24,true),bodyLarge=type(17,25),bodyMedium=type(15,22),labelLarge=type(16,22,true)),content=content)
}

/** Camera occupies the same composition slot through all order/voice/guide state changes. */
@Composable
internal fun SignalSession(
    presentation: SignalPresentation,
    message: String,
    repeatEnabled: Boolean,
    onSettings: () -> Unit,
    onEnd: () -> Unit,
    onRepeat: () -> Unit,
    camera: @Composable (Modifier) -> Unit,
    order: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit,
    accessibilityMessage: String = message,
    announceStatus: Boolean = true
) {
    val large=LocalDensity.current.fontScale>=1.6f
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.background,contentColor=colors.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=20.dp)
            .testTag("signalSession").semantics { isTraversalGroup=true }) {
            Row(Modifier.fillMaxWidth().heightIn(min=60.dp).testTag("homeToolbar").semantics { traversalIndex=0f },verticalAlignment=Alignment.CenterVertically) {
                if(!large) Text("손끝길",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                TextButton(onClick=onSettings,modifier=Modifier.heightIn(min=56.dp)) { Text("설정",color=colors.onSurface) }
                FilledTonalButton(onClick=onEnd,modifier=Modifier.heightIn(min=56.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=colors.surfaceVariant,contentColor=colors.onSurface)) { Text("종료") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().semantics { traversalIndex=1f; isTraversalGroup=true }) {
                val controlMax=maxHeight*.46f
                Column(Modifier.fillMaxSize().then(if(large) Modifier.verticalScroll(rememberScrollState()) else Modifier),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SignalHeadline(presentation,large)
                    camera(if(large) Modifier.fillMaxWidth().height(560.dp) else Modifier.fillMaxWidth().weight(1f))
                    order?.invoke()
                    Column(Modifier.fillMaxWidth().heightIn(max=if(large) 480.dp else controlMax)
                        .verticalScroll(rememberScrollState()).testTag("sessionControls"),verticalArrangement=Arrangement.spacedBy(6.dp)) { controls() }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp).semantics { traversalIndex=2f },verticalArrangement=Arrangement.spacedBy(8.dp)) {
                // One existing status live region; the rapidly changing direction headline is deliberately not live.
                NativeStatus(message,accessibilityMessage,announceStatus)
                if(large) Column(Modifier.fillMaxWidth().testTag("homeActions"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    SignalRepeatActions(Modifier.fillMaxWidth(),repeatEnabled,onRepeat)
                } else Row(Modifier.fillMaxWidth().testTag("homeActions"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SignalRepeatActions(Modifier.weight(1f),repeatEnabled,onRepeat)
                }
            }
        }
    }
}

@Composable
private fun SignalRepeatActions(modifier: Modifier, repeatEnabled: Boolean, onRepeat: () -> Unit) {
    val colors=MaterialTheme.colorScheme
    Button(onClick={},enabled=false,modifier=modifier.heightIn(min=64.dp).semantics { stateDescription="준비 중" },
        colors=ButtonDefaults.buttonColors(disabledContainerColor=colors.surfaceVariant,disabledContentColor=colors.onSurfaceVariant),contentPadding=PaddingValues(10.dp)) { Text("음성 재인식") }
    Button(onClick=onRepeat,enabled=repeatEnabled,modifier=modifier.heightIn(min=64.dp),
        colors=ButtonDefaults.buttonColors(containerColor=SignalGold,contentColor=SignalInk,disabledContainerColor=colors.surfaceVariant,disabledContentColor=colors.onSurfaceVariant),contentPadding=PaddingValues(10.dp)) { Text("재안내",fontWeight=FontWeight.Bold) }
}

@Composable
private fun SignalHeadline(state: SignalPresentation, large: Boolean) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=2.dp).testTag("signalHeadline").semantics(mergeDescendants=true) { heading() },
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(colors.primary,CircleShape).clearAndSetSemantics {})
                Text(state.label,color=colors.primary,style=MaterialTheme.typography.labelLarge)
            }
            Text(state.title,fontFamily=SignalFont,fontWeight=FontWeight.Bold,fontSize=(if(large) 24 else 30).sp,
                lineHeight=(if(large) 32 else 38).sp,letterSpacing=(-.6).sp,style=TextStyle(lineBreak=LineBreak.Heading))
            // Full instruction remains readable with magnification; don't shrink to fit.
            if(large) Text(state.detail,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
        }
        if(!large) SignalMark(state,Modifier.size(70.dp))
    }
}

@Composable
private fun SignalMark(state: SignalPresentation, modifier: Modifier) {
    val colors=MaterialTheme.colorScheme
    val accent=colors.primary
    Surface(modifier.clearAndSetSemantics {},color=colors.surfaceVariant,shape=RoundedCornerShape(23.dp)) {
        Canvas(Modifier.padding(20.dp)) {
            val stroke=Stroke(3.dp.toPx(),cap=StrokeCap.Round)
            val w=size.width; val h=size.height
            if(state.direction!=null) {
                val angle=mapOf("right" to 0f,"down_right" to 45f,"down" to 90f,"down_left" to 135f,"left" to 180f,"up_left" to 225f,"up" to 270f,"up_right" to 315f)[state.direction] ?: 0f
                rotate(angle) {
                    drawLine(accent,Offset(0f,h/2),Offset(w,h/2),stroke.width,StrokeCap.Round)
                    drawPath(Path().apply { moveTo(w*.55f,0f); lineTo(w,h/2); lineTo(w*.55f,h) },accent,style=stroke)
                }
            } else when(state.phase) {
                SignalPhase.LISTENING -> (0..3).forEach { i -> val height=if(i==1 || i==2) h else h*.4f
                    drawLine(accent,Offset(w*i/3,(h-height)/2),Offset(w*i/3,(h+height)/2),stroke.width,StrokeCap.Round) }
                SignalPhase.PRESS -> { drawCircle(accent,w*.46f,style=stroke); drawCircle(accent,w*.12f) }
                SignalPhase.COMPLETE -> drawPath(Path().apply { moveTo(0f,h*.5f); lineTo(w*.35f,h*.85f); lineTo(w,h*.1f) },accent,style=stroke)
                else -> { drawCircle(accent,w*.4f,style=stroke); drawCircle(accent,w*.06f) }
            }
        }
    }
}

@Composable
internal fun SignalOrderCard(order: NativeOrder, progress: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val large=LocalDensity.current.fontScale>=1.6f
    val summary=order.items.firstOrNull()?.menu.orEmpty()+if(order.items.size>1) " 외 ${order.items.size-1}종" else " · ${order.items.sumOf { it.qty }}개"
    Surface(onClick=onOpen,modifier=modifier.fillMaxWidth().testTag("signalOrderCard"),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface) {
        if(large) Column(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("주문 보기",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            Text(progress,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(summary,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(progress,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("주문 보기",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SignalOrderPanel(order: NativeOrder, progress: String, modifier: Modifier, onClose: () -> Unit) {
    val request=remember { BringIntoViewRequester() }
    LaunchedEffect(order) { withFrameNanos { }; request.bringIntoView() }
    Surface(modifier.bringIntoViewRequester(request),color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface,
        shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),tonalElevation=0.dp) {
        NativeOrderConfirmation(order,progress,Modifier.padding(horizontal=14.dp,vertical=8.dp),onClose)
    }
}

@Composable
internal fun SignalSpeechControls(
    recording: Boolean, busy: Boolean, editing: Boolean, installed: Boolean, canListen: Boolean,
    onPrepare: () -> Unit, onListen: () -> Unit, onFinish: () -> Unit, onCancel: () -> Unit, onText: () -> Unit
) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(!installed && !recording && !busy) TextButton(onClick=onPrepare,modifier=Modifier.heightIn(min=56.dp)) { Text("음성 모델 준비") }
        else if(!recording && !busy && !editing) NativeButton("주문 말하기",enabled=canListen,action=onListen)
        if(recording) NativeButton("말하기 완료",action=onFinish)
        if(busy) OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("음성 작업 취소") }
        if(!editing && !recording && !busy) OutlinedButton(onClick=onText,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("직접 입력 주문") }
    }
}
