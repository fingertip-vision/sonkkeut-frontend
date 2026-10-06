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
import java.util.concurrent.atomic.AtomicBoolean

data class ScreenMatchPrompt(val candidate: ScreenMenuCandidate,val signature: String)

class NativeAppModel(application: Application) : AndroidViewModel(application) {
    private val storage = application.getSharedPreferences("native_app",0)
    private val menuClient = NativeMenuClient(application)
    private val database = MenuRagDatabase(application)
    private val knowledge = MenuKnowledgeStore(application)
    private val receiptReader=ReceiptReader()
    private var receiptStarted=0L
    var readingReceipt by mutableStateOf(false); private set
    var knowledgeMessage by mutableStateOf(""); private set
    fun localKnowledge(name: String)=knowledge.get(server,storeCode,name)
    fun saveKnowledge(name: String,related: String,description: String,remove: Boolean=false) {
        try {
            require(menu.any { it.name==name }) { "현재 매장에 등록된 메뉴를 선택해 주세요." }
            val terms=related.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if(remove) knowledge.remove(server,storeCode,name) else knowledge.save(server,storeCode,name,terms,description.trim())
            conversation.stop(); resetOrder(); connect()
            knowledgeMessage=if(remove) "이 매장의 개인 검색 정보를 삭제했습니다." else "이 매장의 개인 검색 정보를 저장했습니다. 관련 표현은 확인 후 메뉴 후보로 사용합니다."
        } catch(e: Exception) { knowledgeMessage=if(e is IllegalArgumentException) "메뉴와 입력 길이를 확인해 주세요. 관련 표현은 30개 이하, 각 80자 이하, 설명은 500자 이하입니다." else "검색 정보를 저장하지 못했습니다. 저장 공간을 확인하고 다시 시도해 주세요." }
    }
    fun startReceiptReading() {
        if(!ready) { announce("AI를 먼저 준비해 주세요."); return }
        val reachedPayment=order!=null && flow.allAdded() && screen?.type=="payment"
        conversation.stop(pauseCamera=false); cancelSpeech(); clearConversationTarget(); open("home"); start()
        flow.finishGuidance(); flowState=flow.state
        if(reachedPayment) reportCompletion() else completionReported=true
        receiptReader.reset(); readingReceipt=true; receiptStarted=SystemClock.elapsedRealtime(); flow.paused=true
        announce("결제를 직접 마친 뒤 완료 화면을 비춰 주세요. 완료 문구와 주문 번호를 읽겠습니다. 안내 중지로 끝낼 수 있습니다.")
    }
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
    internal val screenMenus=MenuScreenLinks()
    var matchingDiagnostic by mutableStateOf(""); private set
    var screenMatchPrompt by mutableStateOf<ScreenMatchPrompt?>(null); private set
    private var flow = NativeOrderFlow(screenMenus)
    internal val orderFlow get()=flow
    val conversation=NativeConversation(this)
    var dialogTurn by mutableStateOf<DialogTurn?>(null); internal set
    @Volatile private var detailScanner: DetailScanner?=null
    private val detailRequested=AtomicBoolean(false)
    @Volatile private var lastTarget: String? = null
    private var pendingManual: Pair<String,String>? = null
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
        viewModelScope.launch { while(isActive) { delay(1000); conversation.tick(SystemClock.elapsedRealtime()); if(conversation.navigating || readingReceipt) forceFreshScreen()
            if(readingReceipt && SystemClock.elapsedRealtime()-receiptStarted>120000) { pause(); announce("완료 문구를 확인하지 못했습니다. 결제 성공 여부를 판단할 수 없습니다. 영수증이나 직원 안내를 확인해 주세요.") }
        } }
    }
    fun connect() {
        connection?.cancel(); val token = ++connectionGeneration
        val base=server; val code=storeCode
        connection = viewModelScope.launch {
            try {
                val result=menuClient.load(base,code)
                val snapshot=withContext(Dispatchers.IO) {
                    val payload=JSONArray(result.items.map { m -> JSONObject().put("name",m.name).put("aliases",JSONArray(m.aliases)).put("sold_out",m.soldOut).put("category",m.category) })
                    val stored=JSONArray(database.catalog(JSONArray(listOf(base,code,result.version)).toString(),payload.toString()))
                    val source=result.items.associateBy { it.name }
                    knowledge.enrich(base,code,(0 until stored.length()).map { i -> val m=stored.getJSONObject(i); val aliases=m.getJSONArray("aliases"); val original=source.getValue(m.getString("name"))
                        original.copy(aliases=(0 until aliases.length()).map { aliases.getString(it) },soldOut=m.getBoolean("sold_out"),category=m.optString("category")) })
                }
                if (token != connectionGeneration) return@launch
                if (menuVersion >= 0 && menuVersion != result.version && (order != null || conversation.active)) { conversation.stop(); resetOrder(); announce("메뉴가 변경됐습니다. 주문을 다시 확인해 주세요.") }
                menu=snapshot; screenMenus.configure(menu); menuVersion=result.version; storeName=result.store
                connectionMessage=if (result.offline) "오프라인 · 저장된 ${result.store} 메뉴" else "연결됨 · ${result.store}"
                whisper.setMenuContext(menu.map { it.name })
                val aliases=menu.flatMap { m -> (listOf(m.name)+m.aliases).map { it to m.name } }.groupBy { it.first }
                    .filterValues { matches -> matches.map { it.second }.distinct().size == 1 }.mapValues { it.value.first().second }
                commands.execute { SonkkeutEngine.setMenuAliases(aliases) }
                if(usage.enabled) viewModelScope.launch { usage.enqueue(base) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token==connectionGeneration) connectionMessage="연결 실패 · ${e.message ?: "인터넷 연결을 확인해 주세요."}" }
        }
    }
    fun saveConnection(base: String, code: String) {
        try {
            val normalized=BackendAddress.normalize(base,BuildConfig.DEBUG)
            val normalizedCode=code.trim().uppercase(); require(Regex("[A-Z0-9]{6}").matches(normalizedCode)) { "매장 코드는 영문·숫자 6자리입니다." }
            if (normalized != server || normalizedCode != storeCode) { conversation.stop(); resetOrder(); menu=emptyList(); screenMenus.configure(menu); menuVersion=-1; screen=null; rawSpeech=""; rag=null; cancelSpeech() }
            server=normalized; storeCode=normalizedCode
            storage.edit().putString("server",server).putString("storeCode",storeCode).apply(); connect()
        } catch (e: Exception) { connectionMessage=e.message ?: "주소와 코드를 확인해 주세요." }
    }
    fun open(value: String) { if (page!=value) { if(value!="home") { readingReceipt=false; receiptReader.reset(); conversation.stop() }; cameraGeneration.incrementAndGet(); cancelSpeech(); page=value; SonkkeutEngine.running=false }; if (value=="home") SonkkeutEngine.running=!paused && ready }
    fun start() { if (!ready) { announce("AI를 준비하고 있습니다. 잠시 후 다시 시작해 주세요."); return }; cameraGeneration.incrementAndGet(); paused=false; flow.paused=false; SonkkeutEngine.running=page=="home"; commands.execute { SonkkeutEngine.requestKeyframe() }; announce("카메라로 키오스크 전체 화면을 비춰 주세요.") }
    fun pause() { screenMenus.reset(); screenMatchPrompt=null; readingReceipt=false; receiptReader.reset(); conversation.stop(); cameraGeneration.incrementAndGet(); if(flow.state=="S5") flow.recover(); paused=true; flow.paused=true; SonkkeutEngine.running=false; cancelSpeech(); commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null }; message="안내를 중지했습니다." }
    fun stopForBackground() { pause() }
    fun process(image: Image, rotation: Int) {
        if (closed || paused || page!="home" || !ready) return
        val token=cameraGeneration.get()
        if(conversation.closeup && detailRequested.compareAndSet(true,false)) {
            try {
                val scanner=detailScanner ?: DetailScanner(getApplication()).also { detailScanner=it }
                val result=scanner.scan(image,rotation) { closed || cameraGeneration.get()!=token || !conversation.closeup }
                main.post { if(!closed && cameraGeneration.get()==token && conversation.closeup) conversation.detail(result) }
            } catch(e: Exception) { main.post { if(!closed && cameraGeneration.get()==token && conversation.closeup) conversation.detail(DetailScan(emptyList(),0,0,true)) } }
            return
        }
        val result=SonkkeutEngine.processYuv(image,rotation) ?: return
        main.post { if (!closed && !paused && page=="home" && cameraGeneration.get()==token) accept(result) }
    }
    private fun accept(value: Map<String,Any?>) {
        frames++; frame=value; found=value["found"]==true
        val json=JSONObject(value)
        if(readingReceipt) {
            if(!found) receiptReader.reset()
            else json.optJSONObject("structure")?.let { runCatching { RecognizedScreen.from(it) }.onSuccess { current ->
                screen=current; receiptReader.observe(current)?.let { reading -> pause(); announce(reading.speech()) }
            } }
            return
        }
        json.optJSONObject("structure")?.let { structure -> runCatching { RecognizedScreen.from(structure) }.onSuccess { current ->
            screen=current
            conversation.onScreen(current)
            if(readingReceipt || paused) return
            if(conversation.active) flow.paused=conversation.blocking
            val requested=pendingManual
            if(requested!=null && found) {
                pendingManual=null
                val candidates=current.elements.filter { it.readable && it.text==requested.first && it.kind==requested.second }
                if(candidates.size==1) {
                    val candidate=candidates.single(); val keyframe=current.keyframe
                    targetAttempt++
                    commands.execute { val okay=SonkkeutEngine.setTarget(candidate.id)
                        main.post { if(!closed && screen?.keyframe==keyframe) announce(if(okay) "${candidate.text} 버튼으로 안내합니다" else "화면이 바뀌었습니다. 다시 확인해 주세요.") }
                    }
                } else announce("선택한 버튼을 현재 화면에서 확실하게 찾지 못했습니다. 화면 읽기로 다시 확인해 주세요.")
            } else if(requested==null) {
                applyAction(flow.accept(current))
                matchingDiagnostic=screenMenus.diagnostic
                if(!conversation.active && flow.state=="SM") {
                    clearConversationTarget()
                    val item=flow.currentItem()
                    val match=item?.let { screenMenus.resolve(it.menu,it.menuId,current) }
                    screenMatchPrompt=match?.takeIf { it.status==ScreenMenuStatus.CONFIRM }?.candidate?.let { ScreenMatchPrompt(it,screenMenus.signature(current)) }
                    message=flow.message
                } else screenMatchPrompt=null
            }
        } }
        json.optJSONObject("event")?.let { event ->
            if (event.optString("type")=="press" && !conversation.blocking) flow.press()
            vibeHz=event.optDouble("vibe_hz",0.0)
            val text=event.optString("speak")
            if (text.isNotBlank() && (!conversation.active || conversation.guidanceAllowed)) announce(text,event.optString("type")=="press")
        }
        json.optJSONObject("verdict")?.let { verdict ->
            flow.action?.let { previous -> usageSteps += JSONObject().put("screen_type",flow.screen?.type ?: "other").put("target_kind",previous.target.kind)
                .put("result",verdict.optString("result")).put("hints",0).put("fail_reason",if(verdict.optString("result")=="success") JSONObject.NULL else verdict.optString("reason")) }
            if(conversation.navigating) { commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null } }
            else if(order!=null) { flow.verdict(verdict.optString("result"),verdict.optString("reason"),verdict.optString("speak")); if(!conversation.active || conversation.guidanceAllowed) announce(flow.message) }
            else { announce(verdict.optString("speak")); commands.execute { SonkkeutEngine.clearTarget(); lastTarget=null } }
            commands.execute { SonkkeutEngine.requestKeyframe() }
        }
        if (json.optBoolean("target_missing")) { if(!conversation.blocking) flow.recover(); lastTarget=null; if(!conversation.active || conversation.guidanceAllowed) announce("화면이 바뀌었습니다. 목표를 다시 확인합니다."); commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() } }
        val hint=json.optString("hint")
        if (!found && hint.isNotBlank() && !conversation.active) message=hint
        val tip=json.optJSONArray("tip")?.let { a -> (0 until a.length()).map { a.optDouble(it) } }
        conversation.frame(if(found && json.optDouble("tip_conf",0.0)>=.5 && json.optDouble("tip_pointing",0.0)>=.5) tip else null,SystemClock.elapsedRealtime())
        flowState=flow.state
        reportCompletion()
    }
    private fun applyAction(action: NativeAction?) {
        flowState=flow.state
        if (order!=null && !conversation.blocking) message=flow.message
        if (action==null || action.target.id==lastTarget) return
        val target=action.target.id; val keyframe=screen?.keyframe
        lastTarget=target
        targetAttempt++
        commands.execute {
            val success=SonkkeutEngine.setTarget(target,action.expect)
            main.post { if (!closed && screen?.keyframe==keyframe && flow.action?.target?.id==target) {
                if (success) announce(action.message) else { lastTarget=null; flow.recover(); announce("화면이 바뀌었습니다. 다시 확인해 주세요.") }
            } }
        }
    }
    fun guide(element: RecognizedElement) {
        if (!element.readable) { announce("현재 화면의 글자를 다시 확인해 주세요."); return }
        resetOrder(); pendingManual=element.text to element.kind
        open("home"); start(); announce("현재 화면에서 ${element.text} 버튼을 다시 확인합니다.")
    }
    fun recoverScreen() { flow.recover(); lastTarget=null; commands.execute { SonkkeutEngine.clearTarget(); SonkkeutEngine.requestKeyframe() }; open("home"); start() }
    fun submit(text: String, fromSpeech: Boolean=false) {
        conversation.stop()
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
        usageEventId=java.util.UUID.randomUUID().toString(); orderStartedAt=SystemClock.elapsedRealtime(); completionReported=false; usageSteps.clear()
        applyAction(flow.confirm()); open("home"); start(); if (flow.action==null) announce(flow.message)
    }
    fun startConversation() {
        if(!ready || menu.isEmpty()) { announce("AI와 매장 메뉴를 먼저 준비해 주세요."); return }
        if(!modelInstalled && !systemSpeechAvailable) { announce("자체 음성 모델을 받거나 기기 한국어 음성 인식을 준비해 주세요."); open("order"); return }
        conversation.stop(); resetOrder(); open("home"); start()
        if(modelInstalled && whisper.status()["ready"]!=true) {
            val token=++speechGeneration; speechBusy=true; message="자체 음성 모델을 준비한 뒤 화면별 대화를 시작합니다."
            whisper.prepare({ if(!closed && token==speechGeneration && page=="home" && !paused) { speechBusy=false; conversation.begin() } },
                { error -> if(!closed && token==speechGeneration) { speechBusy=false; pause(); message="음성 모델 준비 실패: ${error.message}" } })
        } else conversation.begin()
    }
    internal fun rawDialogSpeech(text: String) { rawSpeech=text }
    internal fun messageFromDialog(text: String) { message=text }
    internal fun clearConversationTarget() { lastTarget=null; commands.execute { SonkkeutEngine.clearTarget() } }
    internal fun forceFreshScreen() { commands.execute { SonkkeutEngine.requestKeyframe() } }
    internal fun prepareDetailScan() { detailRequested.set(true) }
    internal fun navigationTarget(target: RecognizedElement,text: String) {
        targetAttempt++; lastTarget=target.id
        commands.execute { val success=SonkkeutEngine.setTarget(target.id,mapOf("changed" to true)); main.post { if(!closed && conversation.navigating) announce(if(success) text else "화면이 바뀌었습니다. 다음 화면을 확인합니다.") } }
    }
    internal fun discardStepOrder() { resetOrder() }
    internal fun confirmStepOrder(next: NativeOrder) {
        flow.submit(next); order=next; flow.paused=false; flow.confirm(); flowState=flow.state
        usageEventId=java.util.UUID.randomUUID().toString(); orderStartedAt=SystemClock.elapsedRealtime(); completionReported=false; usageSteps.clear()
    }
    internal fun updateExtraChoice(group: String, option: ExtraOption) { order=flow.updateExtra(group,option); flowState=flow.state; clearConversationTarget() }
    internal fun updateStepChoice(field: String,value: String) { order=flow.updateChoice(field,value); flowState=flow.state; clearConversationTarget() }
    fun dialogPromptCompleted(token: Long,success: Boolean) { conversation.afterPrompt(token,success) }
    internal fun captureDialogVoice(token: Long) {
        if(!conversation.active || token!=conversation.dialog.generation) return
        val generation=++speechGeneration; speechBusy=true; recording=true
        fun result(text: String,evidence: SpeechEvidence) { if(!closed && generation==speechGeneration && token==conversation.dialog.generation) { speechBusy=false; recording=false; conversation.spoken(text,evidence) } }
        fun fail(message: String) { if(!closed && generation==speechGeneration && token==conversation.dialog.generation) { speechBusy=false; recording=false; conversation.spoken("",SpeechEvidence(noSpeech=1.0)) } }
        if(modelInstalled) {
            speechProvider="custom"
            whisper.listen({ r -> result(if(r.noSpeechProbability>.8) "" else r.text,SpeechEvidence(decoderScore=r.sequenceScore,noSpeech=r.noSpeechProbability)) },{ e -> fail(e.message ?: "음성 오류") })
        } else { speechProvider="system"; systemSpeech.listenScored(menu.map { it.name },{ text,score -> result(text,SpeechEvidence(probability=score)) },{ fail(it) }) }
    }
    private fun reportCompletion() {
        if(flow.state!="S6" || completionReported || usageEventId.isBlank()) return
        completionReported=true
        if(!usageConsent) return
        val payload=JSONObject().put("store_code",storeCode).put("app_version",BuildConfig.VERSION_NAME).put("model_version","2026.10.03")
            .put("completed",true).put("duration_s",(SystemClock.elapsedRealtime()-orderStartedAt)/1000.0).put("steps",JSONArray(usageSteps.take(200))).put("event_id",usageEventId)
        val base=server
        viewModelScope.launch { usage.enqueue(base,payload) }
    }
    fun confirmScreenMatch(yes: Boolean,expected: ScreenMatchPrompt) {
        if(expected!=screenMatchPrompt) { announce("화면이 바뀌었습니다. 새 인식 내용을 확인해 주세요."); return }
        val candidate=expected.candidate
        val current=screen; val item=flow.currentItem(); screenMatchPrompt=null
        if(yes && current!=null && item!=null && screenMenus.approve(item.menu,item.menuId,current,candidate,expected.signature)) {
            flow.recover(); forceFreshScreen(); announce("확인한 메뉴의 위치를 다시 읽습니다.")
        } else { pause(); announce(if(yes) "화면이 바뀌었습니다. 다시 확인해 주세요." else "주문을 유지하고 안내를 중지했습니다.") }
    }
    private fun resetOrder() { screenMenus.reset(); screenMatchPrompt=null; flow=NativeOrderFlow(screenMenus); order=null; lastTarget=null; pendingManual=null; completionReported=false; usageEventId=""; usageSteps.clear(); commands.execute { SonkkeutEngine.clearTarget() } }
    fun refreshSpeechStatus() { val status=whisper.status(); modelInstalled=status["installed"]==true; if (!speechBusy) speechStatus=if(modelInstalled) "자체 Whisper v3 준비됨" else "자체 Whisper v3 다운로드 필요 · 약 485MB" }
    fun downloadSpeech() {
        if (speechBusy) return
        val token=++speechGeneration; speechBusy=true
        whisper.install(onProgress={ done,total,stage -> if(token==speechGeneration) speechStatus=if(total>0) "음성 모델 ${done*100/total}% · $stage" else "음성 모델 준비 · $stage" },
            onReady={ if(token==speechGeneration) { speechBusy=false; refreshSpeechStatus() } },onError={ error -> if(token==speechGeneration) { speechBusy=false; speechStatus="음성 모델 오류: ${error.message}" } })
    }
    fun listen() {
        if (speechBusy) return
        speechProvider="custom"
        resetOrder()
        val token=++speechGeneration; speechBusy=true; recording=true; rawSpeech=""; rag=null; order=null; message="메뉴와 수량을 말씀해 주세요."
        whisper.listen(onResult={ result -> if(token==speechGeneration) { speechBusy=false; recording=false
            if(result.text.isBlank() || result.noSpeechProbability>.8) message="음성을 듣지 못했습니다. 다시 말씀해 주세요." else submit(result.text,true)
        } },onError={ error -> if(token==speechGeneration) { speechBusy=false; recording=false; message="음성 인식 오류: ${error.message}" } })
    }
    fun listenSystem() {
        if(speechBusy) return
        resetOrder(); speechProvider="system"
        val token=++speechGeneration; speechBusy=true; recording=true; rawSpeech=""; rag=null; message="기기 음성 인식으로 주문을 말씀해 주세요."
        systemSpeech.listen(menu.map { it.name },{ text -> if(token==speechGeneration) { speechBusy=false; recording=false; submit(text,true) } },
            { text -> if(token==speechGeneration) { speechBusy=false; recording=false; announce(text) } })
    }
    fun finishSpeech() { if(recording) { recording=false; if(speechProvider=="system") systemSpeech.finish() else whisper.finishCapture(); message="말씀하신 주문을 분석하고 있습니다." } }
    fun cancelSpeech() { speechGeneration++; whisper.cancel(); systemSpeech.cancel(); recording=false; speechBusy=false }
    fun announce(text: String, press: Boolean=false) { message=text; if (announcement!=text || press) { announcement=text; pressAnnouncement=press; announcementNumber++ } }
    override fun onCleared() {
        closed=true; speechGeneration++; connectionGeneration++; runCatching { network.unregisterNetworkCallback(callback) }
        conversation.stop(); usage.close(); systemSpeech.cancel(); whisper.close(); database.close(); knowledge.close(); SonkkeutEngine.running=false; commands.execute { detailScanner?.close(); detailScanner=null; SonkkeutEngine.release() }; commands.shutdown()
        main.removeCallbacksAndMessages(null)
    }
    companion object { const val DEFAULT_SERVER="https://amazing-manually-transcript-est.trycloudflare.com" }
}
