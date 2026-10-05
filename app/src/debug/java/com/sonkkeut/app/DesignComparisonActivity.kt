package com.sonkkeut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Debug-only static design study: no AI, camera, permission, or order callbacks. */
class DesignComparisonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val direction = intent.getIntExtra("direction", 0).coerceIn(0, 2)
        val camera = intent.getBooleanExtra("camera", false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = direction != 0 && !intent.hasExtra("eventConcept")
            isAppearanceLightNavigationBars = direction != 0 && !intent.hasExtra("eventConcept")
        }
        setContent { if(intent.hasExtra("eventConcept")) EventConcept(intent.getIntExtra("eventConcept",0).coerceIn(0,2),camera) else if(intent.getBooleanExtra("refined", false)) RefinedSignalStudy(camera) else DesignStudy(direction, camera) }
    }
}

@Composable
internal fun DesignStudy(direction: Int, camera: Boolean) {
    val bg = listOf(Color(0xFF101820), Color(0xFFF7F8FA), Color(0xFFFFF8EC))[direction]
    val ink = listOf(Color(0xFFF7F8FA), Color(0xFF14263D), Color(0xFF29231C))[direction]
    val accent = listOf(Color(0xFFFFE45C), Color(0xFF174EA6), Color(0xFF305548))[direction]
    val onAccent = if (direction == 0) Color(0xFF101820) else Color.White
    val cameraAccent = listOf(Color(0xFFFFE45C),Color(0xFF9EC5FF),Color(0xFFA9DCC4))[direction]
    val gap = listOf(16.dp, 24.dp, 20.dp)[direction]
    val shape = RoundedCornerShape(listOf(8.dp, 16.dp, 28.dp)[direction])
    MaterialTheme(colorScheme = lightColorScheme(background=bg, surface=bg, onSurface=ink, primary=accent, onPrimary=onAccent)) {
        Surface(Modifier.fillMaxSize(), color=bg, contentColor=ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).semantics { isTraversalGroup=true }, verticalArrangement=Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text("손끝길", fontSize=28.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick={}, modifier=Modifier.heightIn(min=64.dp)) { Text("설정", fontSize=20.sp, color=ink) }
                    if(camera) Button(onClick={}, shape=shape, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFFFE45C), contentColor=Color(0xFF101820)), modifier=Modifier.heightIn(min=64.dp)) { Text("종료",fontSize=20.sp) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(gap)) {
                    if(camera) {
                        Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF080F1E),shape).semantics { contentDescription="카메라 구도 예시. 실제 인식 화면 아님" }, contentAlignment=Alignment.Center) {
                            Column(Modifier.padding(20.dp).border(3.dp,cameraAccent,shape).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("키오스크 화면",color=Color.White,fontSize=22.sp)
                                Text("아메리카노",color=Color.White,fontSize=22.sp)
                                Text("목표 버튼",color=cameraAccent,fontSize=22.sp)
                            }
                        }
                        Text("손끝 안내",fontSize=22.sp,modifier=Modifier.semantics { heading() })
                        Surface(color=if(direction==0) Color(0xFF22313F) else if(direction==1) Color(0xFFE3ECFA) else Color(0xFFE4EDE7),shape=shape) {
                            Column(Modifier.fillMaxWidth().padding(20.dp).semantics(mergeDescendants=true) { liveRegion=LiveRegionMode.Polite },verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("→ 오른쪽으로 이동",fontSize=30.sp,fontWeight=FontWeight.Bold,color=ink)
                                Text("목표 · 아메리카노",fontSize=22.sp,color=ink)
                            }
                        }
                        Text("담기 확인 0 / 2개",fontSize=22.sp)
                        OutlinedButton(onClick={},shape=shape,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) { Text("주문 보기",fontSize=22.sp,color=ink) }
                    } else {
                        Spacer(Modifier.height(36.dp))
                        Text("키오스크 주문을\n손끝으로 안내해요",fontSize=listOf(34.sp,32.sp,30.sp)[direction],fontWeight=FontWeight.Bold,lineHeight=44.sp,modifier=Modifier.semantics { heading() })
                        Text("화면을 비추고,\n메뉴와 수량을 말해 주세요.",fontSize=24.sp,lineHeight=36.sp)
                        Text("결제는 키오스크에서 직접 진행해요.",fontSize=22.sp,lineHeight=32.sp)
                        Spacer(Modifier.height(28.dp))
                        Text("✓ 시작할 준비가 됐어요",fontSize=24.sp,color=ink,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                    }
                }
                if(camera) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick={},enabled=false,shape=shape,modifier=Modifier.weight(1f).heightIn(min=72.dp).semantics { stateDescription="준비 중, 사용할 수 없음" }) { Text("음성 재인식",fontSize=20.sp) }
                    Button(onClick={},shape=shape,modifier=Modifier.weight(1f).heightIn(min=72.dp)) { Text("재안내",fontSize=24.sp) }
                } else Button(onClick={},shape=shape,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) { Text("손끝길 시작",fontSize=26.sp,fontWeight=FontWeight.Bold) }
            }
        }
    }
}
