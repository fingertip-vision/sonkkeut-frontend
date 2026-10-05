package com.sonkkeut.app

import android.os.SystemClock
import androidx.compose.runtime.*
import kr.sonkkeut.android.*

/** Main-thread coordinator; camera/ASR/TTS workers return only generation-scoped results. */
class NativeConversation(private val model: NativeAppModel) {
    val dialog=VoiceDialogManager()
    val navigator=ScreenNavigator()
    val verifier=VisionVerifier()
    var active by mutableStateOf(false); private set
    var closeup by mutableStateOf(false); private set
    var navigating by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    private var context=""
    private var requested=""
    private var pendingMenu: MenuDocument?=null
    private var recommendation: MenuDocument?=null
    private var checkoutConfirmed=false
    private var pendingScan: DetailScan?=null
    private var fullScanFound=false
    private var lastHint=0L
    private var navigationAction: DetectionResult?=null
    private var navigationStarted=0L
    private var draftDine: String?=null
    private var preOrderNavigation=false
    val blocking get()=active && (dialog.state!=DialogState.WAITING_SCREEN || navigating || closeup || model.order==null)
    val guidanceAllowed get()=active && dialog.state==DialogState.WAITING_SCREEN && !closeup && !model.recording && !model.speechBusy
    fun begin() {
        active=true; context=""; requested=""; pendingMenu=null; recommendation=null; checkoutConfirmed=false
        closeup=false; fullScanFound=false; navigator.reset(); verifier.cancel(); navigating=false; navigationStarted=0L
        draftDine=null; preOrderNavigation=false; dialog.waitForScreen(); model.dialogTurn=null
        status="현재 키오스크 화면을 확인하고 있습니다."; model.messageFromDialog(status); model.forceFreshScreen()
    }
    fun stop(message: String="음성 주문을 중지했습니다.",pauseCamera: Boolean=true) {
        if(!active) return
        active=false; closeup=false; navigating=false; verifier.cancel(); dialog.stop()
        model.dialogTurn=null; model.cancelSpeech(); model.orderFlow.paused=false; model.clearConversationTarget()
        if(pauseCamera) model.pause()
        status=message; model.messageFromDialog(message)
    }
    private fun emit(turn: DialogTurn) {
        model.cancelSpeech(); if(!navigating) model.clearConversationTarget(); model.orderFlow.paused=true
        model.dialogTurn=turn; status=turn.prompt; model.messageFromDialog(turn.prompt)
    }
    private fun ask(slot: VoiceSlot,prompt: String,choices: Map<String,String>,implicit: Boolean=false) = emit(dialog.begin(slot,prompt,choices,implicit))
    private fun askMenu(reason: String="") {
        val groups=model.menu.filter { !it.soldOut }.flatMap { menu -> (listOf(menu.name)+menu.aliases).map { it to menu.name } }.groupBy { NativeOrderParser.normalize(it.first) }
        val names=groups.filterValues { it.map { p -> p.second }.distinct().size==1 }.values.flatten().toMap()
        ask(VoiceSlot.MENU,reason+"메뉴 이름만 말씀해 주세요. 수량과 옵션은 다음에 물어볼게요.",names,true)
    }
    fun onScreen(rawScreen: RecognizedScreen) {
        if(!active || closeup) return
        val screen=if(rawScreen.type=="unknown" && rawScreen.elements.any { it.readable && it.kind=="tab" }) rawScreen.copy(type="category") else rawScreen
        if(navigating && navigator.hasChanged(screen)) { model.cancelSpeech(); dialog.waitForScreen(); model.dialogTurn=null }
        val signature=screen.type+":"+when(screen.type) { "option" -> screen.elements.filter { it.readable }.map { NativeOrderParser.normalize(it.text) }.sorted().joinToString("|"); "cart" -> "${screen.cartCount}:${screen.total}"; else -> "" }
        if(navigationAction?.target?.kind=="back" && context!=signature) { navigating=false; navigationAction=null; model.clearConversationTarget() }
        if(context.isNotBlank() && context!=signature) {
            model.cancelSpeech(); dialog.waitForScreen(); model.dialogTurn=null; checkoutConfirmed=false
            if(preOrderNavigation) { preOrderNavigation=false; navigating=false; model.clearConversationTarget() }
        }
        context=signature
        if(dialog.state in listOf(DialogState.PROMPTING,DialogState.LISTENING,DialogState.VERIFYING,DialogState.FALLBACK)) return
        if(fullScanFound) { fullScanFound=false; status="전체 화면에서 버튼 위치를 다시 확인합니다." }
        if(model.order==null) {
            if(preOrderNavigation) return
            when(screen.type) {
                "method" -> ask(VoiceSlot.DINE,"매장 이용 선택 화면입니다. 매장에서 드시나요, 포장하시나요?",mapOf("매장" to "매장","매장에서" to "매장","포장" to "포장","테이크아웃" to "포장"),true)
                "menu" -> if(requested.isNotBlank()) search(requested,screen) else askMenu()
                "category" -> {
                    val choices=screen.elements.filter { it.readable && it.kind=="tab" }.groupBy { NativeOrderParser.normalize(it.text) }.filterValues { it.size==1 }.values.map { it.single().text }.associateWith { it }
                    ask(VoiceSlot.CATEGORY,"분류 선택 화면입니다. ${choices.keys.take(3).joinToString(", ")} 중 원하시는 분류를 말씀해 주세요.",choices,true)
                }
                "start" -> screen.elements.firstOrNull { it.readable && NativeOrderParser.normalize(it.text) in listOf("주문시작","시작하기") }?.let { preOrderNavigation=true; navigating=true; navigationStarted=SystemClock.elapsedRealtime(); model.navigationTarget(it,"주문 시작 화면입니다. ${it.text} 버튼으로 안내합니다.") }
                "option","cart" -> if(backTarget(screen)!=null) ask(VoiceSlot.NAVIGATION,"현재 ${if(screen.type=="cart") "장바구니" else "옵션"} 화면입니다. 새 주문을 위해 메뉴 화면으로 돌아갈까요?",yesNo()) else stop("주문 정보를 확인할 수 없습니다. 키오스크 메뉴 화면에서 다시 시작해 주세요.")
                "payment" -> stop("결제 화면입니다. 새 주문 안내를 시작하지 않습니다.")
                else -> model.messageFromDialog("화면 종류를 확실히 읽지 못했습니다. 키오스크 전체 화면을 비춰 주세요.")
            }
            return
        }
        val flow=model.orderFlow; val item=flow.currentItem()
        when(screen.type) {
            "method" -> if(model.order?.dine==null) ask(VoiceSlot.DINE,"매장에서 드시나요, 포장하시나요?",mapOf("매장" to "매장","매장에서" to "매장","포장" to "포장","테이크아웃" to "포장"),true)
            "menu","category" -> if(item!=null) search(item.menu,screen)
            "option" -> if(item!=null) {
                val names=screen.elements.filter { it.readable }.map { NativeOrderParser.normalize(it.text) }
                if(names.none { it.replace(Regex("[0-9,]+원"),"")==NativeOrderParser.normalize(item.menu) }) {
                    if(navigating && navigationAction?.target?.kind=="back") return
                    if(screen.elements.any { it.readable && it.kind=="back" }) ask(VoiceSlot.NAVIGATION,"다른 메뉴의 옵션 화면입니다. 뒤로 돌아갈까요?",yesNo())
                    else stop("주문 메뉴의 옵션 화면인지 확인하지 못했습니다. 화면을 다시 비춰 주세요.")
                    return
                }
                val temps=linkedMapOf<String,String>()
                if(names.any { it in listOf("ice","iced","아이스","차갑게") }) { temps["아이스"]="ice"; temps["차갑게"]="ice" }
                if(names.any { it in listOf("hot","핫","따뜻한","따뜻하게") }) { temps["따뜻한"]="hot"; temps["따뜻하게"]="hot" }
                if(item.temperature==null && temps.isNotEmpty()) ask(VoiceSlot.TEMPERATURE,"온도 선택 화면입니다. ${temps.values.distinct().joinToString(" 또는 ") { if(it=="ice") "아이스" else "따뜻한 음료" }} 중 말씀해 주세요.",temps,true)
                else if(item.temperature==null || flow.optionApplied(item.temperature)) {
                    val sizes=screen.elements.filter { it.readable && NativeOrderParser.normalize(it.text) in listOf("스몰","미디엄","라지","톨","그란데","벤티","small","medium","large") }.associate { it.text to it.text }
                    if(item.size==null && sizes.isNotEmpty()) ask(VoiceSlot.SIZE,"크기 선택입니다. ${sizes.keys.joinToString(" 또는 ")} 중 하나를 말씀해 주세요.",sizes,true)
                }
            }
            "cart" -> if(flow.allAdded() && !checkoutConfirmed) {
                val expected=model.order!!.items.takeIf { it.all { i -> i.price!=null } }?.sumOf { it.price!!*it.qty }
                if(screen.cartCount==model.order!!.items.sumOf { it.qty } && (expected==null || screen.total==expected))
                    ask(VoiceSlot.CHECKOUT,"장바구니 ${screen.cartCount}개${screen.total?.let { ", ${it}원" } ?: ""}입니다. 결제 화면으로 안내할까요?",yesNo())
            }
            "payment" -> stop("결제 화면입니다. 음성 주문 안내를 마칩니다.",false)
        }
        model.orderFlow.paused=blocking
    }
    private fun search(name: String,screen: RecognizedScreen) {
        if(screen.type !in listOf("menu","category")) return
        val result=navigator.findMenuItem(name,screen,SystemClock.elapsedRealtime())
        when(result.status) {
            DetectionStatus.FOUND -> {
                navigating=false; navigationAction=null; navigationStarted=0L
                if(model.order==null) chooseMenu(name) else { dialog.waitForScreen(); model.orderFlow.paused=false }
            }
            DetectionStatus.SCROLL -> { navigating=true; if(navigationStarted==0L) navigationStarted=SystemClock.elapsedRealtime(); navigationAction=result; model.clearConversationTarget(); model.forceFreshScreen(); ask(VoiceSlot.NAVIGATION,result.message+" 다음 또는 취소라고 말씀하셔도 됩니다.",navigationWords()) }
            DetectionStatus.SWITCH_TAB -> { navigating=true; if(navigationStarted==0L) navigationStarted=SystemClock.elapsedRealtime(); navigationAction=result; model.navigationTarget(result.target!!,result.message); ask(VoiceSlot.NAVIGATION,result.message+" 손끝 위치를 안내합니다. 다음 또는 취소라고 말씀하셔도 됩니다.",navigationWords()) }
            DetectionStatus.NOT_FOUND_IN_VIEWPORT,DetectionStatus.UNREADABLE -> {
                navigating=false; navigationAction=null; model.clearConversationTarget(); requested=name
                val matches=MenuMatcher(model.menu).recommend(name,navigator.observed).filter { NativeOrderParser.normalize(it.menu.name)!=NativeOrderParser.normalize(name) }
                recommendation=matches.firstOrNull()?.menu
                if(recommendation!=null) ask(VoiceSlot.RECOMMENDATION,"${name}를 화면에서 확인하지 못했습니다. 비슷한 ${recommendation!!.name}로 주문할까요?",yesNo())
                else ask(VoiceSlot.EXACT_SEARCH,"${name}를 화면에서 확인하지 못했습니다. 가까이 다시 읽을까요? 네 또는 아니요라고 말씀해 주세요.",yesNo())
            }
            DetectionStatus.WAITING_CHANGE -> Unit
        }
    }
    private fun chooseMenu(name: String) {
        val exact=MenuMatcher(model.menu).exact(name)
        val menu=exact.singleOrNull()?.menu
        if(menu==null) {
            requested=name; navigator.reset(); dialog.waitForScreen(); model.dialogTurn=null
            val matches=MenuMatcher(model.menu).recommend(name,navigator.observed)
            recommendation=matches.firstOrNull()?.menu
            if(recommendation!=null) ask(VoiceSlot.RECOMMENDATION,"${name}와 관련된 ${recommendation!!.name}가 매장 메뉴에 있습니다. 이 메뉴로 주문할까요?",yesNo())
            else ask(VoiceSlot.EXACT_SEARCH,"매장 DB에서 ${name}를 확인하지 못했습니다. 가까이 화면을 다시 읽을까요?",yesNo())
            return
        }
        pendingMenu=menu; requested=menu.name
        val numbers=mapOf("한" to "1","한개" to "1","한잔" to "1","하나" to "1","두" to "2","두개" to "2","두잔" to "2","둘" to "2","세" to "3","세개" to "3","세잔" to "3","셋" to "3","네개" to "4","네잔" to "4","넷" to "4","다섯" to "5","여섯" to "6","일곱" to "7","여덟" to "8","아홉" to "9","열" to "10")
        ask(VoiceSlot.QUANTITY,"${menu.name} 몇 개 드릴까요? 1개에서 10개까지 말씀해 주세요.",numbers+(1..10).flatMap { listOf("$it" to "$it","${it}개" to "$it","${it}잔" to "$it") }.toMap(),true)
    }
    fun spoken(text: String,evidence: SpeechEvidence) {
        if(!active) return
        model.rawDialogSpeech(text)
        if(navigating && dialog.slot==VoiceSlot.NAVIGATION && text.isBlank()) { dialog.waitForScreen(); model.dialogTurn=null; return }
        val value=if(dialog.slot in listOf(VoiceSlot.RECOMMENDATION,VoiceSlot.EXACT_SEARCH) && text.trim().startsWith("아니")) "아니요" else text
        emit(dialog.input(value,evidence))
    }
    fun afterPrompt(token: Long,success: Boolean) {
        val turn=model.dialogTurn ?: return
        if(!active || token!=dialog.generation || token!=turn.generation) return
        if(!success) { stop("한국어 음성 출력을 준비하지 못했습니다. 음성 설정을 확인하고 다시 시작해 주세요."); model.messageFromDialog(status); return }
        if(turn.stopped) { stop(); model.pause(); return }
        if(turn.accepted!=null) { val slot=dialog.slot; dialog.waitForScreen(); model.dialogTurn=null; accept(slot,turn.accepted); return }
        if(turn.listen && dialog.afterPrompt(token)) model.captureDialogVoice(token)
    }
    private fun accept(slot: VoiceSlot,value: String) {
        when(slot) {
            VoiceSlot.CATEGORY -> model.screen?.elements?.singleOrNull { it.readable && it.kind=="tab" && it.text==value }?.let { preOrderNavigation=true; navigating=true; navigationStarted=SystemClock.elapsedRealtime(); model.navigationTarget(it,"${it.text} 분류 버튼으로 안내합니다.") }
            VoiceSlot.MENU -> chooseMenu(value)
            VoiceSlot.QUANTITY -> { val selected=pendingMenu ?: return; navigator.reset(); model.confirmStepOrder(NativeOrder(listOf(NativeOrderItem(selected.name,value.toInt(),selected.price)),draftDine)); dialog.waitForScreen(); model.forceFreshScreen() }
            VoiceSlot.DINE -> {
                if(model.order!=null) model.updateStepChoice("dine",value)
                else {
                    draftDine=value
                    val target=model.screen?.elements?.firstOrNull { it.readable && NativeOrderParser.normalize(it.text) in if(value=="포장") listOf("포장","포장하기","테이크아웃") else listOf("매장","매장이용","매장에서먹기","먹고가기") }
                    if(target!=null) { preOrderNavigation=true; navigating=true; navigationStarted=SystemClock.elapsedRealtime(); model.navigationTarget(target,"${target.text} 버튼으로 안내합니다.") }
                }
                model.forceFreshScreen()
            }
            VoiceSlot.TEMPERATURE -> { model.updateStepChoice("temperature",value); model.forceFreshScreen() }
            VoiceSlot.SIZE -> { model.updateStepChoice("size",value); model.forceFreshScreen() }
            VoiceSlot.RECOMMENDATION -> if(value=="네") { val menu=recommendation ?: return; model.discardStepOrder(); chooseMenu(menu.name) } else beginCloseup()
            VoiceSlot.EXACT_SEARCH -> if(value=="네") beginCloseup() else { requested=""; model.discardStepOrder(); askMenu() }
            VoiceSlot.SCAN_CONTROL -> if(value=="읽기") {
                pendingScan=null; model.prepareDetailScan(); model.messageFromDialog("가까운 구역을 읽고 있습니다. 잠시 기다려 주세요.")
            } else if(value=="다음 구역") {
                pendingScan?.let { scan -> val finding=verifier.accept(scan,SystemClock.elapsedRealtime()); pendingScan=null; finishScan(finding) }
                if(closeup) askScan()
            }
            VoiceSlot.CHECKOUT -> if(value=="네") { checkoutConfirmed=true; model.orderFlow.paused=false; model.forceFreshScreen() } else { stop("장바구니 확인에서 안내를 중지했습니다."); model.pause() }
            VoiceSlot.NAVIGATION -> if(value=="네") {
                model.screen?.let(::backTarget)?.let { preOrderNavigation=model.order==null; navigating=true; navigationStarted=SystemClock.elapsedRealtime(); navigationAction=DetectionResult(DetectionStatus.SWITCH_TAB,it); model.navigationTarget(it,"${it.text} 버튼으로 안내합니다.") }
                model.forceFreshScreen()
            } else if(value=="아니요") { stop("현재 화면의 안내를 중지했습니다."); model.pause() } else model.forceFreshScreen()
            else -> Unit
        }
        model.orderFlow.paused=blocking
    }
    private fun beginCloseup() {
        model.clearConversationTarget(); navigating=false; closeup=true; pendingScan=null
        verifier.begin(requested,SystemClock.elapsedRealtime()); askScan()
    }
    private fun askScan()=ask(VoiceSlot.SCAN_CONTROL,listOf("화면 위쪽","화면 가운데","화면 아래쪽")[verifier.section.coerceIn(0,2)]+"을 가까이 비춰 주세요. 준비되면 읽어줘, 그만하려면 취소라고 말씀해 주세요.",mapOf("읽어줘" to "읽기","읽기" to "읽기","스캔" to "읽기","준비됐어" to "읽기","준비됐어요" to "읽기"))
    fun detail(scan: DetailScan) {
        if(!active || !closeup) return
        pendingScan=scan
        if(verifier.detectsRequested(scan)) finishScan(verifier.accept(scan,SystemClock.elapsedRealtime()))
        else ask(VoiceSlot.SCAN_CONTROL,"${verifier.section+1}번째 구역에서 ${scan.lines.size}줄을 읽었지만 ${requested}를 확인하지 못했습니다. 다음 구역, 다시 읽기 또는 취소라고 말씀해 주세요.",mapOf("다음" to "다음 구역","다음구역" to "다음 구역","다시읽기" to "읽기","다시읽어줘" to "읽기"))
    }
    private fun finishScan(finding: VisionFinding) {
        if(finding==VisionFinding.SEARCHING) return
        closeup=false; model.cancelSpeech(); dialog.waitForScreen(); model.dialogTurn=null
        if(finding==VisionFinding.RESOLVED_BY_CLOSEUP) {
            fullScanFound=true
            val exact=MenuMatcher(model.menu).exact(requested).singleOrNull()
            if(exact==null) { requested=""; model.discardStepOrder(); askMenu("근접 화면에서 글자를 읽었지만 매장 DB에서 주문 메뉴를 확인하지 못했습니다. ") }
            else { navigator.reset(); ask(VoiceSlot.NAVIGATION,"근접 읽기로 ${requested}를 확인했습니다. 카메라를 뒤로 옮겨 전체 화면을 비춰 주세요. 준비되면 다음 또는 전체 화면이라고 말씀해 주세요.",navigationWords()+mapOf("전체화면" to "다음")) }
        } else {
            val reason=if(finding==VisionFinding.TIMED_OUT) "재스캔 시간이 지났습니다." else if(verifier.reads==0) "근접 화면도 확실하게 읽지 못했습니다." else "읽은 구역에서 요청 메뉴를 확인하지 못했습니다."
            ask(VoiceSlot.EXACT_SEARCH,"$reason 메뉴가 없다고 단정할 수 없습니다. 가까이 다시 읽을까요?",yesNo())
        }
    }
    fun tick(now: Long) {
        if(active && closeup && verifier.expire(now)) finishScan(VisionFinding.TIMED_OUT)
        if(active && navigating && navigationStarted>0 && now-navigationStarted>=90000) { navigating=false; model.clearConversationTarget(); ask(VoiceSlot.EXACT_SEARCH,"화면 탐색 시간이 지났습니다. 가까이 화면을 다시 읽을까요?",yesNo()) }
    }
    fun frame(tip: List<Double>?,now: Long) {
        if(!active || !navigating || !guidanceAllowed || now-lastHint<2000) return
        navigator.swipeHint(tip,now)?.let { lastHint=now; model.announce(it) }
    }
    private fun yesNo()=mapOf("네" to "네","예" to "네","맞아요" to "네","좋아요" to "네","아니" to "아니요","아니요" to "아니요","아니오" to "아니요","꼭있어야해요" to "아니요","정확히찾아줘" to "아니요")
    private fun navigationWords()=mapOf("다음" to "다음","완료" to "다음","다했어요" to "다음","다시읽어줘" to "다음")
    private fun backTarget(screen: RecognizedScreen)=screen.elements.firstOrNull { it.readable && (it.kind=="back" || NativeOrderParser.normalize(it.text) in listOf("뒤로","계속주문","메뉴로","추가주문","더주문")) }
}
