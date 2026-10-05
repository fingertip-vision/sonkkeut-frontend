package com.sonkkeut.app

/** UI-only freshness bound, not a recognition threshold or a new audio policy. */
internal const val SIGNAL_FRAME_MAX_AGE_MS = 1500L

internal enum class SignalPhase { PAUSED, SEARCHING, ORDER, LISTENING, PROCESSING, HAND, MOVE, PRESS, RESULT, HOLD, ERROR, COMPLETE }
internal data class SignalPresentation(
    val phase: SignalPhase,
    val label: String,
    val title: String,
    val detail: String,
    val direction: String? = null,
    val targetBox: List<Float>? = null,
    val frameVisible: Boolean = false
)

internal data class SignalSnapshot(
    val paused: Boolean = false,
    val flow: String = "S0",
    val recording: Boolean = false,
    val busy: Boolean = false,
    val editing: Boolean = false,
    val hasOrder: Boolean = false,
    val frame: Map<String, Any?> = emptyMap(),
    val targetId: String? = null,
    val attempt: Int = 0,
    val frameAttempt: Int = -1,
    val frameAt: Long = 0,
    val now: Long = 0,
    val pressAt: Long = 0,
    val pressAttempt: Int = -1
)

/** Never infer a direction from a target rectangle, S4/S5, or a previous target's event. */
internal fun signalPresentation(s: SignalSnapshot): SignalPresentation {
    fun state(p: SignalPhase, label: String, title: String, detail: String) = SignalPresentation(p,label,title,detail)
    if(s.flow=="S6") return state(SignalPhase.COMPLETE,"안내 완료","결제 화면에\n도착했어요","결제는 키오스크에서 직접 진행해 주세요.")
    if(s.paused) return state(SignalPhase.PAUSED,"잠시 멈춤","준비되면,\n다시 시작해요","카메라 다시 시작을 눌러 이어가세요.")
    if(s.recording) return state(SignalPhase.LISTENING,"주문 듣는 중","메뉴와 수량을\n말씀해 주세요","다 말하면 말하기 완료를 눌러 주세요.")
    if(s.busy) return state(SignalPhase.PROCESSING,"주문 처리 중","주문을\n확인하고 있어요","잠시만 기다려 주세요.")
    if(s.editing) return state(SignalPhase.ORDER,"직접 입력","어떤 메뉴를\n주문할까요?","메뉴·수량·옵션을 함께 입력해 주세요.")
    if(s.flow=="SE") return state(SignalPhase.ERROR,"다시 확인","화면을 다시\n확인해 주세요","손을 멈추고 화면 다시 확인을 눌러 주세요.")
    val fresh=s.frameAt>0 && s.now-s.frameAt in 0..SIGNAL_FRAME_MAX_AGE_MS
    if(!fresh || s.frame["found"]!=true || s.frame["target_missing"]==true)
        return state(SignalPhase.SEARCHING,"화면 탐색","키오스크 화면을\n비춰 주세요","화면 전체가 카메라에 들어오도록 해 주세요.")
    if(!s.hasOrder) return state(SignalPhase.ORDER,"주문 준비","메뉴와 수량을\n알려 주세요","음성 또는 직접 입력으로 주문할 수 있어요.").copy(frameVisible=true)
    val waiting=state(SignalPhase.HOLD,"대상 확인","잠시 손을\n멈춰 주세요","현재 화면에서 안내할 버튼을 확인하고 있어요.").copy(frameVisible=true)
    if(s.flow !in listOf("S4","S5") || s.targetId.isNullOrBlank() || s.attempt!=s.frameAttempt) return waiting
    val event=s.frame["event"] as? Map<*,*> ?: return waiting
    if(event["target_id"]!=s.targetId) return waiting
    val type=event["type"]
    if(type=="no_hand") return state(SignalPhase.HAND,"손끝 확인","검지를 화면 앞에\n보여 주세요","손끝이 보이면 안내를 이어갈게요.").copy(frameVisible=true)
    if(type=="point") return state(SignalPhase.HAND,"손끝 확인","검지 하나만\n펴 주세요","다른 손가락은 접고 가리켜 주세요.").copy(frameVisible=true)
    if(type=="hold" || type=="reset") return waiting
    fun confidence(key: String) = (s.frame[key] as? Number)?.toDouble()?.let { it.isFinite() && it in .5..1.0 }==true
    val tip=(s.frame["tip"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }
    if(!confidence("tip_conf") || !confidence("tip_pointing") || tip?.size!=2 || tip.any { !it.isFinite() || it !in 0.0..1.0 })
        return state(SignalPhase.HAND,"손끝 확인","손끝을 다시\n보여 주세요","검지 끝이 화면 앞에 잘 보이도록 해 주세요.").copy(frameVisible=true)
    val box=signalTargetBox(s.frame)
    // The native press command is a single frame. Briefly retain it only while new,
    // matched, confident frames still report the fingertip inside the target.
    val retainedPress=s.flow=="S5" && type=="direction" && event["distance"]=="reach" &&
        s.pressAttempt==s.attempt && s.pressAt>0 && s.now-s.pressAt in 0..1200L
    if(type=="press" || retainedPress) return state(SignalPhase.PRESS,"누르기","지금\n눌러 주세요","안내한 버튼을 누르면 결과를 확인해요.").copy(targetBox=box,frameVisible=true)
    if(s.flow=="S5") return state(SignalPhase.RESULT,"결과 확인","화면 반응을\n확인하고 있어요","다음 안내가 나올 때까지 기다려 주세요.").copy(targetBox=box,frameVisible=true)
    val directions=mapOf("right" to "오른쪽으로", "up_right" to "오른쪽 위로", "up" to "위로", "up_left" to "왼쪽 위로", "left" to "왼쪽으로", "down_left" to "왼쪽 아래로", "down" to "아래로", "down_right" to "오른쪽 아래로")
    val dir=event["dir"] as? String
    if(type=="direction" && directions.containsKey(dir)) return SignalPresentation(SignalPhase.MOVE,"손끝 안내","${directions[dir]}\n천천히 이동해요","음성과 진동을 따라 손끝을 움직여 주세요.",dir,box,true)
    if(type=="direction" && event["distance"]=="reach") return state(SignalPhase.HOLD,"손끝 안내","그 자리에서\n잠시 기다려요","누르기 안내가 나올 때까지 기다려 주세요.").copy(targetBox=box,frameVisible=true)
    return waiting
}

internal fun signalTargetBox(frame: Map<String,Any?>): List<Float>? {
    val size=(frame["frame_size"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: return null
    val box=(frame["target_image_box"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: return null
    if(size.size!=2 || size.any { !it.isFinite() || it<=0 } || box.size!=4 || box.any { !it.isFinite() }) return null
    return box.takeIf { it[0]>=0 && it[1]>=0 && it[2]<=size[0] && it[3]<=size[1] && it[2]>it[0] && it[3]>it[1] }
}

/** The footer must not keep displaying a previous move/press command after its evidence expires. */
internal fun signalStatusMessage(s: SignalSnapshot, p: SignalPresentation, original: String): String =
    if(s.hasOrder && s.flow in listOf("S4","S5") && !s.paused && !s.recording && !s.busy && !s.editing) p.detail else original
