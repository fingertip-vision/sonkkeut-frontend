package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EInk=Color(0xFF0B1015)
private val EText=Color(0xFFF3F4EF)
private val EYellow=Color(0xFFFFE16A)
private val EQuiet=Color(0xFFBBC7CA)

/** Event presentation concepts. Fixed examples, no production state or functional callbacks. */
@Composable
internal fun EventConcept(concept:Int,camera:Boolean) {
    val bg=listOf(Color(0xFF10191E),Color(0xFF101320),Color(0xFF131816))[concept]
    MaterialTheme(colorScheme=darkColorScheme(surface=bg,background=bg,onSurface=EText,primary=EYellow,onPrimary=EInk)) {
        Surface(Modifier.fillMaxSize(),color=bg,contentColor=EText) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("손끝길",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick={},modifier=Modifier.heightIn(min=56.dp)) { Text("설정",fontSize=18.sp,color=EText) }
                    if(camera) Button(onClick={},shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=56.dp)) { Text("종료",fontSize=19.sp,fontWeight=FontWeight.Bold) }
                }
                if(camera) EventGuidance(concept,Modifier.weight(1f)) else EventWelcome(concept,Modifier.weight(1f))
                if(camera) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={},enabled=false,modifier=Modifier.weight(1f).heightIn(min=72.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.outlinedButtonColors(disabledContentColor=EQuiet)) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("음성 재인식",fontSize=18.sp);Text("준비 중",fontSize=15.sp) } }
                    Button(onClick={},modifier=Modifier.weight(1f).heightIn(min=72.dp),shape=RoundedCornerShape(18.dp)) { Text("재안내  ↻",fontSize=23.sp,fontWeight=FontWeight.Bold) }
                } else {
                    Text("● 시작할 준비가 됐어요",fontSize=19.sp,color=EQuiet,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                    Button(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=76.dp),shape=RoundedCornerShape(20.dp)) { Text("손끝길 시작",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("↗",fontSize=30.sp) }
                }
            }
        }
    }
}

@Composable
private fun EventWelcome(c:Int,modifier:Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        if(c==0) {
            Text("내 손끝의 길잡이",fontSize=18.sp,color=EYellow)
            Text("주문은 가볍게.\n안내는 또렷하게.",fontSize=36.sp,lineHeight=45.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
            RouteArt(0,Modifier.fillMaxWidth().weight(1f))
        } else if(c==1) {
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
                RouteArt(1,Modifier.fillMaxSize())
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("손끝에서 시작되는",fontSize=19.sp,color=EQuiet);Text("다음 한 걸음",fontSize=34.sp,fontWeight=FontWeight.Bold) }
            }
            Text("내 손끝의 길잡이",fontSize=18.sp,color=EYellow)
            Text("주문은 가볍게.\n안내는 또렷하게.",fontSize=30.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
        } else {
            Surface(color=EYellow,contentColor=EInk,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(26.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("내 손끝의 길잡이",fontSize=18.sp)
                    Text("주문은\n가볍게.\n안내는\n또렷하게.",fontSize=42.sp,lineHeight=47.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                    RouteArt(2,Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        Text("화면을 비추고 메뉴와 수량을 말하면,\n손끝이 갈 곳을 안내해요.",fontSize=21.sp,lineHeight=30.sp,color=EQuiet)
        HorizontalDivider(color=Color(0xFF344046))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("비추기","말하기","따라가기").forEachIndexed { i,s -> Text("0${i+1}  $s",fontSize=17.sp,color=EText) }
        }
        Text("결제는 키오스크에서 직접 진행해요.",fontSize=17.sp,color=EQuiet)
    }
}

@Composable
private fun RouteArt(c:Int,modifier:Modifier) {
    Canvas(modifier) {
        val center=Offset(size.width*.5f,size.height*.5f)
        val radius=size.minDimension*.41f
        val line=if(c==2) EInk else EYellow
        if(c==1) {
            for(i in 1..3) drawCircle(Color(0xFF303A4F),radius*i/3f,center,style=Stroke(1.dp.toPx()))
            drawArc(Brush.sweepGradient(listOf(Color.Transparent,EYellow)),210f,220f,false,Offset(center.x-radius,center.y-radius),Size(radius*2,radius*2),style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))
            drawCircle(EYellow,7.dp.toPx(),Offset(center.x+radius*.76f,center.y+radius*.64f))
        } else {
            for(i in 0..7) drawLine(if(c==2) EInk.copy(alpha=.12f) else Color(0xFF2D3B3F),Offset(size.width*i/7f,0f),Offset(size.width*i/7f,size.height),1.dp.toPx())
            val p=Path().apply { moveTo(size.width*.1f,size.height*.78f);cubicTo(size.width*.46f,size.height*.8f,size.width*.43f,size.height*.25f,size.width*.86f,size.height*.25f) }
            drawPath(p,line.copy(alpha=.08f),style=Stroke(35.dp.toPx(),cap=StrokeCap.Round))
            drawPath(p,line,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))
            drawCircle(line,10.dp.toPx(),Offset(size.width*.86f,size.height*.25f))
            drawCircle(line,17.dp.toPx(),Offset(size.width*.1f,size.height*.78f),style=Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun EventGuidance(c:Int,modifier:Modifier) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(c==0) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("손끝 안내",fontSize=20.sp);Text("● 안내 중",fontSize=17.sp,color=EYellow) }
            Surface(color=EYellow,contentColor=EInk,shape=RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Text("→",fontSize=42.sp,modifier=Modifier.padding(end=18.dp));Column { Text("오른쪽으로 이동",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("목표 · 아메리카노",fontSize=18.sp) } }
            }
            KioskStudy(Modifier.fillMaxWidth().weight(1f),false)
        } else if(c==1) {
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp))) {
                KioskStudy(Modifier.fillMaxSize(),true)
                Surface(color=Color(0xFF1B2635),contentColor=EText,shape=RoundedCornerShape(20.dp),modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Text("→",color=EYellow,fontSize=44.sp,modifier=Modifier.padding(end=16.dp));Column { Text("오른쪽으로 이동",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("목표 · 아메리카노",fontSize=18.sp,color=EQuiet) } }
                }
            }
            Text("● 안내 중",fontSize=18.sp,color=EYellow)
        } else {
            KioskStudy(Modifier.fillMaxWidth().weight(1f),false)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("→",fontSize=58.sp,color=EYellow,modifier=Modifier.padding(end=20.dp))
                Column { Text("오른쪽으로",fontSize=31.sp,fontWeight=FontWeight.Bold);Text("이동해 주세요",fontSize=31.sp,fontWeight=FontWeight.Bold) }
            }
            Text("목표 · 아메리카노   /   안내 중",fontSize=18.sp,color=EQuiet)
        }
        HorizontalDivider(color=Color(0xFF344046))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("담기 확인",fontSize=17.sp,color=EQuiet);Text("0 / 2개",fontSize=25.sp,fontWeight=FontWeight.Bold) }
            OutlinedButton(onClick={},shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=60.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=EText)) { Text("주문 보기",fontSize=19.sp) }
        }
    }
}

@Composable
private fun KioskStudy(modifier:Modifier,overlay:Boolean) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF394440),Color(0xFF202B2C)))).semantics { contentDescription="키오스크 구도 예시. 실제 인식 화면 아님" }) {
        Column(Modifier.align(Alignment.Center).padding(horizontal=30.dp).offset(y=if(overlay) (-35).dp else 0.dp).fillMaxWidth().background(Color(0xFFE8EBE3),RoundedCornerShape(14.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("COFFEE",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Color(0xFF2D3B32))
            Text("오늘의 커피",fontSize=14.sp,color=Color(0xFF536052))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("아메리카노","카페라떼").forEachIndexed { i,label ->
                    Column(Modifier.weight(1f).background(Color.White,RoundedCornerShape(8.dp)).then(if(i==0) Modifier.border(4.dp,EInk,RoundedCornerShape(8.dp)).padding(3.dp).border(2.dp,EYellow,RoundedCornerShape(6.dp)) else Modifier).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
                            val w=size.width*.6f; val left=(size.width-w)/2
                            drawRoundRect(Color(0xFFBEA27C),Offset(left,size.height*.25f),Size(w,size.height*.65f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                            drawOval(Color(0xFF443024),Offset(left,size.height*.15f),Size(w,size.height*.3f))
                        }
                        Text(label,fontSize=12.sp,color=EInk,fontWeight=FontWeight.Bold)
                        Text(if(i==0) "3,000" else "3,500",fontSize=12.sp,color=Color(0xFF536052))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFFD4DACF),RoundedCornerShape(8.dp)))
        }
        Text("구도 예시",fontSize=14.sp,color=EText,modifier=Modifier.align(Alignment.TopStart).padding(12.dp).background(EInk,RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=5.dp))
    }
}
