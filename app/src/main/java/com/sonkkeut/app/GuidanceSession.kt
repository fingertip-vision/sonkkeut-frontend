package com.sonkkeut.app

enum class ObservationMode { SYNTHETIC, LIVE }
// Coordinates: corners/21 landmarks in camera pixels; detections in normalized screen plane.
// Both timestamps are monotonic seconds from the same session clock.
data class SessionObservation(
    val session: Long,val sequence: Int,val keyframe: Int,val capturedAt: Double,val processedAt: Double,
    val corners: List<CorePoint>?,val detections: List<CoreDetection>,val hands: List<CoreHand>,
    val screenType: String,val cornerConfidence: Double=.95,val cartQuantities: Map<String,Int>?=null,
)
data class SessionTarget(val keyframe: Int,val elementId: String,val expected: CoreExpectedEffect)
interface ObservationAdapter {
    val mode: ObservationMode
    fun next(session: Long): SessionObservation?
}
enum class GuidanceSessionState { PAUSED, GUIDING, VERIFYING, VERIFIED, RECOVERY, PAYMENT }
data class SessionOutput(val state: GuidanceSessionState,val message: String,val decision: CoreDecision?=null,val result: CoreVerificationResult?=null,val ignored: Boolean=false)

class GuidanceSession {
    var token=0L; private set
    var state=GuidanceSessionState.PAUSED; private set
    private var target: SessionTarget?=null
    private var plane=CorePlaneTracker()
    private var finger=CoreFingertipTracker()
    private var guidance=VisualGuidanceCore()
    private var verifier=CorePressVerifier()
    private var lastSequence=-1
    private var lastCapture: Double?=null
    private var lastProcessed: Double?=null
    fun start(selection: SessionTarget): SessionOutput {
        require(selection.keyframe>=0 && selection.elementId.isNotBlank())
        token++; target=selection; state=GuidanceSessionState.GUIDING
        plane=CorePlaneTracker(); finger=CoreFingertipTracker(); guidance=VisualGuidanceCore(); verifier=CorePressVerifier()
        lastSequence=-1; lastCapture=null; lastProcessed=null
        return SessionOutput(state,"새 관측 세션을 시작했습니다.")
    }
    fun stop(): SessionOutput { token++; state=GuidanceSessionState.PAUSED; return SessionOutput(state,"세션과 안내를 중지했습니다. 새 시나리오를 선택해 주세요.") }
    private fun recover(message: String): SessionOutput { state=GuidanceSessionState.RECOVERY; return SessionOutput(state,"$message 멈춘 뒤 새 시나리오를 선택해 주세요.") }
    fun accept(observation: SessionObservation): SessionOutput {
        if(observation.session != token || state in listOf(GuidanceSessionState.PAUSED,GuidanceSessionState.RECOVERY,GuidanceSessionState.PAYMENT,GuidanceSessionState.VERIFIED)) return SessionOutput(state,"종료되었거나 이전 세션의 입력을 거부했습니다.",ignored=true)
        if(observation.sequence<=lastSequence) return SessionOutput(state,"중복·역순 결과를 거부했습니다.",ignored=true)
        if(!observation.capturedAt.isFinite() || !observation.processedAt.isFinite() || observation.capturedAt<0 || observation.processedAt<observation.capturedAt || lastCapture?.let { observation.capturedAt<=it }==true || lastProcessed?.let { observation.processedAt<it }==true) return recover("관측 시각이 잘못되었거나 오래된 입력입니다.")
        lastSequence=observation.sequence; lastCapture=observation.capturedAt; lastProcessed=observation.processedAt
        val selection=target ?: return recover("선택된 목표가 없습니다.")
        return try {
            val elements=CoreElementProcessor.process(observation.detections.toList(),observation.keyframe).elements
            val screen=CoreScreen(observation.screenType,observation.keyframe,elements)
            if(state==GuidanceSessionState.VERIFYING) {
                val result=verifier.observe(screen,observation.processedAt,observation.capturedAt,observation.cartQuantities?.toMap(),if(observation.cartQuantities != null) observation.keyframe else null)
                if(result.terminal) {
                    state=if(result.verdict=="success") { if(screen.type=="payment") GuidanceSessionState.PAYMENT else GuidanceSessionState.VERIFIED } else GuidanceSessionState.RECOVERY
                    return SessionOutput(state,when(state) {
                        GuidanceSessionState.PAYMENT -> "기대 결제 화면을 확인해 안내를 종료했습니다. 실제 결제는 진행하지 않습니다."
                        GuidanceSessionState.VERIFIED -> "기대 화면 또는 정확한 항목 수량을 계산으로 확인했습니다."
                        else -> "결과가 실패 또는 불확실합니다. 새 시나리오를 선택해 주세요."
                    },result=result)
                }
                return SessionOutput(state,"결과 확인 대기: ${result.reason}",result=result)
            }
            if(observation.processedAt-observation.capturedAt>.2+1e-9) return recover("처리가 지연된 좌표로는 누름을 안내할 수 없습니다.")
            if(observation.keyframe != selection.keyframe) return recover("화면이 바뀌어 기존 목표를 무효화했습니다.")
            if(screen.type=="payment") { state=GuidanceSessionState.PAYMENT; return SessionOutput(state,"결제 화면에서 안내를 종료합니다.") }
            if(screen.type=="other") return recover("화면 종류가 불확실합니다.")
            val selected=elements.find { it.target.id==selection.elementId }?.target ?: return recover("목표 요소가 없어 화면을 다시 확인해야 합니다.")
            val planeState=plane.update(observation.corners?.toList(),observation.capturedAt,observation.cornerConfidence)
            if(!planeState.valid) return recover("화면 좌표 입력을 잃었습니다.")
            val fingertip=finger.update(observation.hands.toList(),planeState.homography,observation.capturedAt,observation.keyframe)
            if(!fingertip.valid) return recover("손끝 입력을 사용할 수 없습니다: ${fingertip.reason}.")
            val decision=guidance.update(fingertip.point,selected,observation.capturedAt,fingertip.rawPoint,fingertip.confidence,observation.keyframe,fingertip.handId)
            if(decision.action=="stop" || decision.recenter || decision.reacquire) return recover("안내 계산이 복구를 요청했습니다: ${decision.reason}.")
            if(decision.action=="press") {
                verifier.begin(screen,selection.expected,observation.capturedAt)
                state=GuidanceSessionState.VERIFYING
            }
            SessionOutput(state,when(decision.action) {
                "move","near" -> GuidancePhrases.movement(decision.direction ?: "",decision.action=="near") ?: "방향을 확인할 수 없습니다."
                "press" -> "누름 안내 조건에 도달했습니다. 결과 화면을 기다립니다."
                else -> "위치를 유지해 주세요. 아직 누름 안내가 아닙니다."
            },decision)
        } catch(_: IllegalArgumentException) { recover("입력 형식이나 계산 조건이 올바르지 않습니다.") }
    }
}

enum class IntegratedScenario(val label: String) { NORMAL("정상 화면 전환"), CART("정확한 수량 확인"), HAND_LOSS("손끝 누락"), PLANE_LOSS("화면 누락"), TARGET_CHANGE("목표 화면 변경"), WRONG_RESULT("예상 밖 결과"), TIMEOUT("결과 시간 초과"), PAYMENT("결제 화면 종료") }
class SyntheticObservationAdapter(private val scenario: IntegratedScenario): ObservationAdapter {
    override val mode=ObservationMode.SYNTHETIC
    private var index=0
    val selection=SessionTarget(1,"e1_1",when(scenario) {
        IntegratedScenario.CART -> CoreExpectedEffect(cartItemKey="coffee|hot",beforeQuantity=0)
        IntegratedScenario.PAYMENT -> CoreExpectedEffect(screenType="payment")
        else -> CoreExpectedEffect(screenType="option")
    })
    override fun next(session: Long): SessionObservation? {
        if(index>=14) return null
        val i=index++; val after=i>=12
        val now=if(after) { if(scenario==IntegratedScenario.TIMEOUT) 3.0 else 1.3+(i-12)*.1 } else i/10.0
        val keyframe=if(after || scenario==IntegratedScenario.TARGET_CHANGE && i>=2) 2+(if(after) i-12 else 0) else 1
        val corners=listOf(CorePoint(100.0,80.0),CorePoint(1180.0,100.0),CorePoint(1130.0,650.0),CorePoint(130.0,620.0))
        val normalized=when(i) { 0 -> CorePoint(.35,.5); 1 -> CorePoint(.46,.5); else -> CorePoint(.5,.5) }
        val camera=CoreHomography.fromCorners(corners).screenToCamera(normalized)
        val hands=if(scenario==IntegratedScenario.HAND_LOSS && i>=2) emptyList() else listOf(CoreHand("fixture-hand",List(21) { camera },.95))
        val type=if(!after || scenario==IntegratedScenario.TIMEOUT) "menu" else when(scenario) { IntegratedScenario.PAYMENT -> "payment"; IntegratedScenario.CART,IntegratedScenario.WRONG_RESULT -> "cart"; else -> "option" }
        val detections=listOf(CoreDetection("button",CoreBox(.48,.48,.52,.52),.95),CoreDetection("button",CoreBox(.479,.479,.519,.519),.8))
        return SessionObservation(session,i,keyframe,now,now+.01,if(scenario==IntegratedScenario.PLANE_LOSS && i>=2) null else corners,detections,hands,type,
            cartQuantities=if(scenario==IntegratedScenario.CART && after) mapOf("coffee|hot" to 1) else null)
    }
}
