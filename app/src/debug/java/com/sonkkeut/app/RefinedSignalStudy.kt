package com.sonkkeut.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night=Color(0xFF09121D)
private val Paper=Color(0xFFF4F5EE)
private val Signal=Color(0xFFFFE76A)
private val Muted=Color(0xFFB8C6D3)
private val Edge=Color(0xFF344454)

/** Presentation only. This Activity is never included in release. */
@Composable
internal fun RefinedSignalStudy(camera:Boolean) {
    MaterialTheme(colorScheme=darkColorScheme(background=Night,surface=Night,onSurface=Paper,primary=Signal,onPrimary=Night)) {
        Surface(Modifier.fillMaxSize(),color=Night,contentColor=Paper) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    BrandMark(Modifier.size(30.dp))
                    Text("손끝길",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick={},modifier=Modifier.heightIn(min=56.dp)) { Text("설정",color=Paper,fontSize=20.sp) }
                    if(camera) Button(onClick={},shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=56.dp)) { Text("종료",fontSize=20.sp,fontWeight=FontWeight.Bold) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    if(camera) RefinedCamera() else RefinedWelcome()
                }
                if(camera) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick={},enabled=false,shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1f).heightIn(min=76.dp).semantics { stateDescription="준비 중, 사용할 수 없음" },colors=ButtonDefaults.outlinedButtonColors(disabledContentColor=Muted)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("음성 재인식",fontSize=19.sp); Text("준비 중",fontSize=16.sp) }
                        }
                        Button(onClick={},shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1f).heightIn(min=76.dp)) { Text("재안내  ↻",fontSize=24.sp,fontWeight=FontWeight.Bold) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).background(Signal,RoundedCornerShape(4.dp)))
                        Text("시작할 준비가 됐어요",color=Paper,fontSize=20.sp,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                    }
                    Button(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=80.dp),shape=RoundedCornerShape(22.dp),contentPadding=PaddingValues(20.dp)) {
                        Text("손끝길 시작",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        Text("↗",fontSize=32.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RefinedWelcome() {
    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(32.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1D3444),Night)))) {
        Canvas(Modifier.fillMaxSize()) {
            val c=Offset(size.width*.5f,size.height*.51f)
            drawCircle(Color(0xFF253B47),size.minDimension*.4f,c,style=Stroke(1.dp.toPx()))
            drawCircle(Color(0xFF253B47),size.minDimension*.28f,c,style=Stroke(1.dp.toPx()))
            drawCircle(Color(0xFF233843),size.minDimension*.16f,c)
            val p=Path().apply { moveTo(size.width*.22f,size.height*.78f); cubicTo(size.width*.22f,size.height*.53f,size.width*.5f,size.height*.68f,size.width*.5f,size.height*.46f); cubicTo(size.width*.5f,size.height*.24f,size.width*.77f,size.height*.42f,size.width*.77f,size.height*.22f) }
            drawPath(p,Signal.copy(alpha=.08f),style=Stroke(30.dp.toPx(),cap=StrokeCap.Round))
            drawPath(p,Signal,style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
            drawCircle(Night,13.dp.toPx(),Offset(size.width*.22f,size.height*.78f))
            drawCircle(Signal,7.dp.toPx(),Offset(size.width*.22f,size.height*.78f))
            val goal=Offset(size.width*.77f,size.height*.22f)
            drawCircle(Signal.copy(alpha=.10f),25.dp.toPx(),goal)
            drawCircle(Signal,12.dp.toPx(),goal,style=Stroke(2.dp.toPx()))
            drawCircle(Signal,4.dp.toPx(),goal)
        }
        Text("나의 손끝에서, 다음 한 걸음까지",color=Muted,fontSize=16.sp,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp))
    }
    Text("주문의 다음 길,\n내 손끝에.",fontSize=40.sp,lineHeight=50.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp,modifier=Modifier.semantics { heading() })
    Text("화면을 비추고 메뉴를 말하면,\n손끝이 갈 곳을 안내해요.",color=Muted,fontSize=22.sp,lineHeight=32.sp)
    HorizontalDivider(color=Edge)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf("비추기","말하기","따라가기").forEachIndexed { index,label ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("0${index+1}",color=Signal,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(label,fontSize=20.sp)
            }
        }
    }
    Text("결제는 키오스크에서 직접 진행해요.",color=Muted,fontSize=18.sp,lineHeight=26.sp)
}

@Composable
private fun RefinedCamera() {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text("손끝 안내",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.semantics { heading() })
        Text("● 안내 중",color=Signal,fontSize=18.sp)
    }
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF293A41),Color(0xFF101C25)))).semantics { contentDescription="카메라 영역 디자인 예시. 실제 촬영 또는 인식 결과 아님" }) {
        // An illustrative kiosk surface; never presented as actual CameraX footage.
        Column(Modifier.align(Alignment.Center).padding(20.dp).width(225.dp).background(Color(0xFFEAEDE7),RoundedCornerShape(10.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("COFFEE & MORE",color=Color(0xFF364139),fontSize=13.sp,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                CoffeeTile("아메리카노",true,Modifier.weight(1f))
                CoffeeTile("카페라떼",false,Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFD2D9D0),RoundedCornerShape(4.dp)))
        }
        Text("구도 예시",color=Paper,fontSize=14.sp,modifier=Modifier.align(Alignment.TopStart).padding(16.dp).background(Night,RoundedCornerShape(8.dp)).padding(horizontal=10.dp,vertical=5.dp))
        Text("목표 · 아메리카노",color=Signal,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=14.dp).background(Night,RoundedCornerShape(10.dp)).padding(horizontal=14.dp,vertical=6.dp))
    }
    Surface(color=Color(0xFF1B2B3A),contentColor=Paper,shape=RoundedCornerShape(24.dp),modifier=Modifier.border(1.dp,Edge,RoundedCornerShape(24.dp))) {
        Column(Modifier.fillMaxWidth().padding(22.dp).semantics(mergeDescendants=true) { liveRegion=LiveRegionMode.Polite },verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("이동 방향",fontSize=18.sp,color=Muted,modifier=Modifier.weight(1f))
                Text("→",fontSize=40.sp,color=Signal,fontWeight=FontWeight.Bold)
            }
            Text("오른쪽으로\n이동해 주세요",fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold)
            Text("목표 · 아메리카노",fontSize=20.sp,color=Muted)
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("담기 확인",fontSize=18.sp,color=Muted)
            Text("0 / 2개",fontSize=26.sp,fontWeight=FontWeight.Bold)
        }
        OutlinedButton(onClick={},shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=64.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Paper)) { Text("주문 보기",fontSize=20.sp) }
    }
}

@Composable
private fun CoffeeTile(label:String,target:Boolean,modifier:Modifier) {
    Column(modifier.background(Color.White,RoundedCornerShape(8.dp)).then(if(target) Modifier.border(3.dp,Color(0xFF273426),RoundedCornerShape(8.dp)).border(2.dp,Signal,RoundedCornerShape(8.dp)) else Modifier).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(42.dp)) {
            drawOval(Color(0xFF5F442D),topLeft=Offset(0f,size.height*.3f),size=androidx.compose.ui.geometry.Size(size.width,size.height*.5f))
            drawRoundRect(Color(0xFFB59670),topLeft=Offset(size.width*.15f,size.height*.4f),size=androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.5f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawOval(Color(0xFF38251C),topLeft=Offset(size.width*.15f,size.height*.32f),size=androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.22f))
        }
        Text(label,color=Color(0xFF18261E),fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun BrandMark(modifier:Modifier) {
    Canvas(modifier) {
        val p=Path().apply { moveTo(size.width*.15f,size.height*.85f); lineTo(size.width*.15f,size.height*.5f); quadraticBezierTo(size.width*.15f,size.height*.3f,size.width*.5f,size.height*.3f); lineTo(size.width*.75f,size.height*.3f); lineTo(size.width*.75f,size.height*.1f) }
        drawPath(p,Signal,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
        drawCircle(Signal,3.dp.toPx(),Offset(size.width*.75f,size.height*.1f))
    }
}
