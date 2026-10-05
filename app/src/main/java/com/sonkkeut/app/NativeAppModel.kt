package com.sonkkeut.app

import android.app.Application
import android.media.Image
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kr.sonkkeut.android.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class NativeAppModel(application: Application) : AndroidViewModel(application) {
    private val storage = application.getSharedPreferences("native_app",0)
    private val menuClient = NativeMenuClient(application)
    private val database = MenuRagDatabase(application)
    private val whisper = KoreanWhisper(application)
    private val systemSpeech = NativeSystemSpeech(application)
    val systemSpeechAvailable get()=systemSpeech.available
    private var speechProvider="custom"
    private val usage = NativeUsageReporter(application)
    private val usageSteps = mutableListOf<JSONObject>()
    private var usageEventId = ""
    private var orderStartedAt = 0L
    private var completionReported = false
    var usageConsent by mutableStateOf(usage.enabled); private set
    fun changeUsageConsent(value: Boolean) { usage.consent(value); usageConsent=value }
    private val commands = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val cameraGeneration = AtomicLong()
    private var speechGeneration = 0L
    private var connectionGeneration = 0L
    private var connection: Job? = null
    private var menuVersion = -1
    private var pendingMenu: NativeMenu? = null
    private var orderPrompted = false
    var running by mutableStateOf(false); private set
    var textOrderOpen by mutableStateOf(false); private set
    var speechRequested by mutableStateOf(false); private set
    private var flow = NativeOrderFlow()
    @Volatile private var lastTarget: String? = null
    private var pendingManual: Pair<String,String>? = null
    private var manualGuidance=false
    private var targetStartedAt=0L
    private var targetHints=0
    private var targetReached: Double?=null
    var captions by mutableStateOf<List<String>>(emptyList()); private set
    var server by mutableStateOf(storage.getString("server", DEFAULT_SERVER)!!); private set
    var storeCode by mutableStateOf(storage.getString("storeCode","Z9XZSN")!!); private set
    var page by mutableStateOf("home"); private set
    var paused by mutableStateOf(true); private set
    var ready by mutableStateOf(false); private set
    var message by mutableStateOf("손끝길 시작을 눌러 주세요."); private set
    var connectionMessage by mutableStateOf("매장 메뉴를 연결하고 있습니다."); private set
    var storeName by mutableStateOf(""); private set
    var menu by mutableStateOf<List<MenuDocument>>(emptyList()); private set
    var screen by mutableStateOf<RecognizedScreen?>(null); private set
    var frame by mutableStateOf<Map<String,Any?>>(emptyMap()); private set
    var frames by mutableIntStateOf(0); private set
    var found by mutableStateOf(false); private set
    var order by mutableStateOf<NativeOrder?>(null); private set
    var rawSpeech by mutableStateOf(""); private set
    var rag by mutableStateOf<MenuSpeechResult?>(null); private set
    var recording by mutableStateOf(false); private set
    var speechBusy by mutableStateOf(false); private set
    var speechStatus by mutableStateOf("자체 음성 모델 상태를 확인하고 있습니다."); private set
    var modelInstalled by mutableStateOf(false); private set
    var announcement by mutableStateOf(""); private set
    var announcementNumber by mutableIntStateOf(0); private set
    var pressAnnouncement by mutableStateOf(false); private set
    var vibeHz by mutableStateOf(0.0); private set
    var flowState by mutableStateOf("S0"); private set
    var targetAttempt by mutableIntStateOf(0); private set
    fun progressText() = order?.let { "담기 확인 ${flow.completedQuantity()} / ${it.items.sumOf { item -> item.qty }}개" } ?: ""
    fun visualGuidance(): String {
        if(paused) return "Ⅱ 안내가 멈췄어요"
        if(flowState=="S6") return "✓ 결제 화면에 도착했어요"
        if(recording) return "● 주문을 말씀해 주세요"
        if(flowState=="S5") return "● 지금 누르세요 · 화면 반응 확인 중"
        if(flowState=="SE") return "! 다시 확인이 필요해요"
        if(!found) return "▣ 키오스크 화면을 비춰 주세요"
        if(flowState!="S4") return "◎ 주문을 입력하고 확인해 주세요"
        val event=frame["event"] as? Map<*,*> ?: return "◎ 손끝 위치를 확인하고 있어요"
        if(event["target_id"]!=null && event["target_id"]!=lastTarget) return "◎ 다음 안내를 기다려 주세요"
        return when(event["type"]) {
            "no_hand" -> "☝ 손끝을 보여 주세요"
            "point" -> "☝ 검지만 펴 주세요"
            "hold","reset" -> "Ⅱ 잠시 멈춰 주세요"
            "direction" -> mapOf("right" to "→ 오른쪽", "up_right" to "↗ 오른쪽 위", "up" to "↑ 위쪽", "up_left" to "↖ 왼쪽 위", "left" to "← 왼쪽", "down_left" to "↙ 왼쪽 아래", "down" to "↓ 아래쪽", "down_right" to "↘ 오른쪽 아래")[event["dir"]]?.plus("으로 이동") ?: "◎ 손끝 위치를 확인하고 있어요"
            else -> "◎ 다음 안내를 기다려 주세요"
        }
    }
    private val network = application.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post { if (!closed) connect() } }
    }
    init {
        refreshSpeechStatus()
        runCatching { network.registerDefaultNetworkCallback(callback) }
        connect()
        commands.execute {
            try {
                SonkkeutEngine.init(application,nativeFeedback=false)
                main.post { if (!closed) { ready=true; if (paused) message="키오스크를 비추고 손끝길을 시작해 주세요." } }
            } catch (e: Throwable) { main.post { if (!closed) message="AI 준비 실패: ${e.message}" } }
        }
        viewModelScope.launch { while (isActive) { delay(15000); if (menu.isEmpty() || connectionMessage.startsWith("오프라인") || connectionMessage.startsWith("연결 실패")) connect() } }
    }
    fun connect() {
        connection?.cancel(); val token = ++connectionGeneration
        val base=server; val code=storeCode
        connection = viewModelScope.launch {
            try {
                val result=menuClient.load(base,code)
                if (token != connectionGeneration) return@launch
                connectionMessage=if (result.offline) "오프라인 · 저장된 ${result.store} 메뉴" else "연결됨 · ${result.store}"
                if(running) { pendingMenu=result; if(menuVersion!=result.version) connectionMessage += " · 새 메뉴는 안내 종료 후 적용" }
                else applyMenu(result)
                if(usage.enabled) viewModelScope.launch { usage.enqueue(base) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token==connectionGeneration) connectionMessage="연결 실패 · ${e.message ?: "인터넷 연결을 확인해 주세요."}" }
        }
    }
    fun saveConnection(base: String, code: String) {
        if(running) { connectionMessage="서버·매장은 주문 안내 종료 후 바꿀 수 있습니다."; return }
        try {
            val normalized=BackendAddress.normalize(base,BuildConfig.DEBUG)
            val normalizedCode=code.trim().uppercase(); require(Regex("[A-Z0-9]{6}").matches(normalizedCode)) { "매장 코드는 영문·숫자 6자리입니다." }
            if (normalized != server || normalizedCode != storeCode) { resetOrder(); menu=emptyList(); menuVersion=-1; screen=null; rawSpeech=""; rag=null; cancelSpeech() }
            server=normalized; storeCode=normalizedCode
            storage.edit().putString("server",server).putString("storeCode",storeCode).apply(); connect()
        } catch (e: Exception) { connectionMessage=e.message ?: "주소와 코드를 확인해 주세요." }
    }
    private fun applyMenu(result: NativeMenu) {
        menu=result.items; menuVersion=result.version; storeName=result.store
        whisper.setMenuContext(menu.map { it.name })
        val aliases=menu.flatMap { m -> (listOf(m.name)+m.aliases).map { it to m.name } }.groupBy { it.first }
            .filterValues { matches -> matches.map { it.second }.distinct().size==1 }.mapValues { it.value.first().second }
        commands.execute { SonkkeutEngine.setMenuAliases(aliases) }
    }
    fun open(value: String) {
        if(value=="order") { page="home"; toggleTextOrder(); return }
        if(page!=value) { cancelSpeech(); if(value!="home" && running) pause(); page=value }
        SonkkeutEngine.running=running && !paused && ready && page=="home" && flow.state!="S6"
    }
    fun start() {
        if(running && flow.state=="S6") { announce("결제 화면입니다. 안내를 종료하고 키오스크에서 직접 결제해 주세요."); return }
        if(!ready) { announce("AI를 준비하고 있습니다. 잠시 후 다시 시작해 주세요."); return }
        if(!running) {
            resetOrder(); running=true; orderPrompted=false; textOrderOpen=false; rawSpeech=""; rag=null
            usageEventId=java.util.UUID.randomUUID().toString(); orderStartedAt=SystemClock.elapsedRealtime(); completionReported=false; usageSteps.clear()
        }
        cameraGeneration.incrementAndGet(); paused=false; flow.paused=false; flow.recover(); flowState=flow.state; lastTarget=null; screen=null; frame=emptyMap(); found=false
        SonkkeutEngine.running=page=="home" && flow.state!="S6"
        commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() }
        announce("카메라로 키오스크 전체 화면을 비춰 주세요.")
    }
    fun pause() { cameraGeneration.incrementAndGet(); if(flow.state!="S6") flow.recover(); paused=true; flow.paused=true; SonkkeutEngine.running=false; cancelSpeech(); speechRequested=false; commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null }; message="안내를 중지했습니다." }
    fun end() {
        reportSession(false); pause(); resetOrder(); running=false; page="home"; textOrderOpen=false; orderPrompted=false
        screen=null; frame=emptyMap(); found=false; rawSpeech=""; rag=null; flowState="S0"
        pendingMenu?.let { applyMenu(it) }; pendingMenu=null
        announce("주문 안내를 종료했습니다. 손끝길 시작을 눌러 주세요.")
    }
    fun toggleTextOrder() { cancelSpeech(); speechRequested=false; orderPrompted=true; textOrderOpen=!textOrderOpen }
    fun consumeSpeechRequest() { speechRequested=false }
    fun stopForBackground() { pause() }
    fun process(image: Image, rotation: Int) {
        if (closed || !running || paused || page!="home" || !ready || flow.state=="S6") return
        val token=cameraGeneration.get()
        val result=SonkkeutEngine.processYuv(image,rotation) ?: return
        main.post { if (!closed && !paused && page=="home" && cameraGeneration.get()==token) accept(result) }
    }
    private fun accept(value: Map<String,Any?>) {
        frames++; frame=value; found=value["found"]==true
        if(!found) {
            if(lastTarget!=null) { cameraGeneration.incrementAndGet(); flow.recover(); flowState=flow.state; lastTarget=null; screen=null; commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() }; announce("화면을 놓쳤습니다. 손을 멈추고 키오스크 전체 화면을 다시 비춰 주세요.") }
            return
        }
        val json=JSONObject(value)
        json.optJSONObject("structure")?.let { structure -> runCatching { RecognizedScreen.from(structure) }.onSuccess { current ->
            screen=current
            if(menuVersion<0 && order==null && !speechBusy) {
                val detected=current.detectedMenu()
                if(detected.isNotEmpty()) applyMenu(NativeMenu("카메라에서 읽은 메뉴 · 품절 정보 미확인",-1,menu.filter { old -> detected.none { it.name==old.name } }+detected))
            }
            val requested=pendingManual
            if(requested!=null && found) {
                pendingManual=null
                val candidates=current.elements.filter { it.readable && it.text==requested.first && it.kind==requested.second }
                if(candidates.size==1) {
                    val candidate=candidates.single(); val keyframe=current.keyframe
                    val generation=cameraGeneration.get(); manualGuidance=true; lastTarget=candidate.id
                    targetAttempt++
                    commands.execute { if(cameraGeneration.get()!=generation) return@execute; val okay=SonkkeutEngine.setTarget(candidate.id)
                        main.post { if(!closed && cameraGeneration.get()==generation && screen?.keyframe==keyframe) announce(if(okay) "${candidate.text} 버튼으로 안내합니다" else "화면이 바뀌었습니다. 다시 확인해 주세요.") }
                    }
                } else announce("선택한 버튼을 현재 화면에서 확실하게 찾지 못했습니다. 화면 읽기로 다시 확인해 주세요.")
            } else if(requested==null) applyAction(flow.accept(current))
            if(order==null && !orderPrompted && !textOrderOpen && !speechBusy && menu.isNotEmpty()) { orderPrompted=true; speechRequested=true }
        } }
        json.optJSONObject("event")?.takeIf { !recording && !speechBusy && (manualGuidance || flow.state in listOf("S4","S5")) && lastTarget!=null &&
            (it.optString("target_id")==lastTarget || it.optString("target_id").isBlank() && it.optString("type")!="press") }?.let { event ->
            if (event.optString("type")=="press" && event.optString("target_id")==lastTarget) { flow.press(); targetReached=((SystemClock.elapsedRealtime()-targetStartedAt)/1000.0).coerceIn(0.0,600.0) }
            vibeHz=event.optDouble("vibe_hz",0.0)
            val text=event.optString("speak")
            if (text.isNotBlank()) { targetHints=(targetHints+1).coerceAtMost(1000); announce(text,event.optString("type")=="press") }
        }
        json.optJSONObject("verdict")?.takeIf { flow.state=="S5" || manualGuidance }?.let { verdict ->
            flow.action?.let { previous -> usageSteps += JSONObject().put("screen_type",flow.screen?.type ?: "other").put("target_kind",previous.target.kind)
                .put("result",verdict.optString("result")).put("hints",targetHints).put("time_to_reach_s",targetReached ?: JSONObject.NULL).put("fail_reason",if(verdict.optString("result")=="success") JSONObject.NULL else verdict.optString("reason")) }
            if(order!=null) { flow.verdict(verdict.optString("result"),verdict.optString("reason"),verdict.optString("speak")); announce(flow.message) }
            else { manualGuidance=false; announce(verdict.optString("speak")); commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null } }
            commands.execute { SonkkeutEngine.requestKeyframe() }
        }
        if (json.optBoolean("target_missing")) { flow.recover(); lastTarget=null; announce("화면이 바뀌었습니다. 목표를 다시 확인합니다."); commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() } }
        val hint=json.optString("hint")
        if (!found && hint.isNotBlank()) message=hint
        flowState=flow.state
        reportCompletion()
    }
    private fun applyAction(action: NativeAction?) {
        flowState=flow.state
        if (order!=null) message=flow.message
        if (action==null || action.target.id==lastTarget) return
        val target=action.target.id; val keyframe=screen?.keyframe; val generation=cameraGeneration.get()
        lastTarget=target
        targetStartedAt=SystemClock.elapsedRealtime(); targetHints=0; targetReached=null
        targetAttempt++
        commands.execute {
            if(cameraGeneration.get()!=generation) return@execute
            val success=SonkkeutEngine.setTarget(target,action.expect)
            main.post { if (!closed && cameraGeneration.get()==generation && !paused && page=="home" && screen?.keyframe==keyframe && flow.action?.target?.id==target) {
                if (success) announce(action.message) else { lastTarget=null; flow.recover(); announce("화면이 바뀌었습니다. 다시 확인해 주세요.") }
            } }
        }
    }
    fun guide(element: RecognizedElement) {
        if (!element.readable) { announce("현재 화면의 글자를 다시 확인해 주세요."); return }
        resetOrder(); orderPrompted=true; speechRequested=false; pendingManual=element.text to element.kind
        open("home"); start(); announce("현재 화면에서 ${element.text} 버튼을 다시 확인합니다.")
    }
    fun recoverScreen() { flow.recover(); lastTarget=null; commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() }; open("home"); start() }
    fun submit(text: String, fromSpeech: Boolean=false) {
        orderPrompted=true; speechRequested=false; textOrderOpen=false; cameraGeneration.incrementAndGet()
        resetOrder()
        val token=++speechGeneration
        recording=false; speechBusy=true
        if (fromSpeech) rawSpeech=text else { rawSpeech=""; rag=null }
        viewModelScope.launch {
            try {
                require(menu.isNotEmpty()) { "먼저 매장 메뉴를 연결해 주세요." }
                val result=if (fromSpeech) withContext(Dispatchers.IO) {
                    val payload=JSONArray(menu.map { m -> JSONObject().put("name",m.name).put("aliases",JSONArray(m.aliases)).put("sold_out",m.soldOut).put("category",m.category) })
                    val stored=JSONArray(database.catalog(JSONArray(listOf(server,storeCode,menuVersion)).toString(),payload.toString()))
                    val docs=(0 until stored.length()).map { i -> val m=stored.getJSONObject(i); val a=m.getJSONArray("aliases")
                        MenuDocument(m.getString("name"),(0 until a.length()).map { a.getString(it) },m.getBoolean("sold_out"),m.optString("category")) }
                    MenuSpeechIndex(docs).correct(text)
                } else null
                if (token!=speechGeneration) return@launch
                rag=result
                if (result?.ambiguities?.isNotEmpty()==true) { announce("비슷한 메뉴가 있습니다. 후보를 선택해 주세요."); return@launch }
                val next=NativeOrderParser.parse(result?.text ?: text,menu)
                flow.submit(next); order=next; flowState=flow.state; announce(next.confirmation())
                commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token==speechGeneration) { order=null; announce(e.message ?: "주문을 다시 말씀해 주세요.") } }
            finally { if (token==speechGeneration) { speechBusy=false; refreshSpeechStatus() } }
        }
    }
    fun selectCandidate(candidate: MenuCandidate) {
        val result=rag ?: return; val ambiguity=result.ambiguities.firstOrNull() ?: return
        if (candidate.menu.soldOut) return
        val originalRaw=rawSpeech
        submit(result.original.substring(0,ambiguity.start)+candidate.menu.name+result.original.substring(ambiguity.end),true)
        rawSpeech=originalRaw
    }
    fun confirm() {
        if(flow.state!="S3" || order==null) return
        flow.recover(); flow.submit(order!!); flow.paused=false; flow.confirm(); open("home"); start(); announce(flow.message)
    }
    private fun reportCompletion() {
        if(flow.state!="S6") return
        SonkkeutEngine.running=false; cameraGeneration.incrementAndGet(); commands.execute { SonkkeutEngine.clearTarget() }
        reportSession(true)
    }
    private fun reportSession(completed: Boolean) {
        if(completionReported || usageEventId.isBlank()) return
        completionReported=true
        if(!usageConsent || menuVersion<0) return
        val payload=JSONObject().put("store_code",storeCode).put("app_version",BuildConfig.VERSION_NAME).put("model_version","2026.10.03")
            .put("completed",completed).put("duration_s",((SystemClock.elapsedRealtime()-orderStartedAt)/1000.0).coerceAtMost(7200.0)).put("steps",JSONArray(usageSteps.take(200))).put("event_id",usageEventId)
        val base=server
        viewModelScope.launch { usage.enqueue(base,payload) }
    }
    private fun resetOrder() { flow=NativeOrderFlow(); flowState=flow.state; order=null; lastTarget=null; pendingManual=null; manualGuidance=false; commands.execute { SonkkeutEngine.clearTarget() } }
    fun refreshSpeechStatus() { val status=whisper.status(); modelInstalled=status["installed"]==true; if (!speechBusy) speechStatus=if(modelInstalled) "자체 Whisper v3 준비됨" else "자체 Whisper v3 다운로드 필요 · 약 485MB" }
    fun downloadSpeech() {
        if (speechBusy) return
        val token=++speechGeneration; speechBusy=true
        whisper.install(onProgress={ done,total,stage -> if(token==speechGeneration) speechStatus=if(total>0) "음성 모델 ${done*100/total}% · $stage" else "음성 모델 준비 · $stage" },
            onReady={ if(token==speechGeneration) { speechBusy=false; refreshSpeechStatus() } },onError={ error -> if(token==speechGeneration) { speechBusy=false; speechStatus="음성 모델 오류: ${error.message}" } })
    }
    fun listen() {
        if (speechBusy || paused || !running || page!="home") return
        orderPrompted=true; speechRequested=false; textOrderOpen=false; cameraGeneration.incrementAndGet()
        speechProvider="custom"
        resetOrder()
        val token=++speechGeneration; speechBusy=true; recording=true; rawSpeech=""; rag=null; order=null; message="메뉴와 수량을 말씀해 주세요."
        whisper.listen(onResult={ result -> if(token==speechGeneration) { speechBusy=false; recording=false
            if(result.text.isBlank() || result.noSpeechProbability>.8) message="음성을 듣지 못했습니다. 다시 말씀해 주세요." else submit(result.text,true)
        } },onError={ error -> if(token==speechGeneration) { speechBusy=false; recording=false; message="음성 인식 오류: ${error.message}" } })
    }
    fun listenSystem() {
        if(speechBusy || paused || !running || page!="home") return
        orderPrompted=true; speechRequested=false; textOrderOpen=false; cameraGeneration.incrementAndGet()
        resetOrder(); speechProvider="system"
        val token=++speechGeneration; speechBusy=true; recording=true; rawSpeech=""; rag=null; message="기기 음성 인식으로 주문을 말씀해 주세요."
        systemSpeech.listen(menu.map { it.name },{ text -> if(token==speechGeneration) { speechBusy=false; recording=false; submit(text,true) } },
            { text -> if(token==speechGeneration) { speechBusy=false; recording=false; announce(text) } })
    }
    fun finishSpeech() { if(recording) { recording=false; if(speechProvider=="system") systemSpeech.finish() else whisper.finishCapture(); message="말씀하신 주문을 분석하고 있습니다." } }
    fun cancelSpeech() { speechGeneration++; whisper.cancel(); systemSpeech.cancel(); recording=false; speechBusy=false }
    fun announce(text: String, press: Boolean=false) { message=text; if (announcement!=text || press) { announcement=text; pressAnnouncement=press; announcementNumber++; if(text.isNotBlank()) captions=(listOf(text)+captions.filter { it!=text }).take(5) } }
    override fun onCleared() {
        closed=true; speechGeneration++; connectionGeneration++; runCatching { network.unregisterNetworkCallback(callback) }
        usage.close(); systemSpeech.cancel(); whisper.close(); database.close(); SonkkeutEngine.running=false; commands.execute { SonkkeutEngine.release() }; commands.shutdown()
        main.removeCallbacksAndMessages(null)
    }
    companion object { const val DEFAULT_SERVER="https://amazing-manually-transcript-est.trycloudflare.com" }
}
