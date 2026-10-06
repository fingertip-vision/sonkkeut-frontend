package com.sonkkeut.app

enum class DialogState { IDLE, PROMPTING, LISTENING, VERIFYING, RETRY_VOICE_CAPTURE, WAITING_SCREEN, FALLBACK, STOPPED }
enum class VoiceSlot { CATEGORY, MENU, QUANTITY, DINE, TEMPERATURE, SIZE, RECOMMENDATION, EXACT_SEARCH, SCAN_CONTROL, NAVIGATION, CHECKOUT, DRAFT, DRAFT_CONFIRM, EXTRA_OPTION, MENU_CANDIDATE, SCREEN_MENU_CONFIRM, VISIBLE_OPTION }
data class SpeechEvidence(val probability: Double?=null,val decoderScore: Double?=null,val noSpeech: Double=0.0) {
    // These are conservative provider-specific heuristics, not calibrated accuracy probabilities.
    fun strong() = noSpeech<.2 && (probability?.let { it.isFinite() && it>=.9 && it<=1 } ?: (decoderScore?.let { it.isFinite() && it>=-.15 && it<=0 } ?: false))
}
data class DialogTurn(val generation: Long,val prompt: String,val listen: Boolean,val accepted: String?=null,val stopped: Boolean=false)

/** Screen-scoped input; only a completed current utterance may open the microphone. */
class VoiceDialogManager(val maxRetries: Int=3) {
    var alwaysConfirm=false
    var state=DialogState.IDLE; private set
    var generation=0L; private set
    var slot=VoiceSlot.MENU; private set
    var prompt=""; private set
    var retries=0; private set
    private var choices=mapOf<String,String>()
    private var question=""
    private var pending: String?=null
    private var implicit=false
    private var awaitingConfirmation=false
    private fun n(s: String)=s.lowercase().replace(Regex("[\\s.,?!]"),"")
    private fun turn(text: String,listen: Boolean=true,accepted: String?=null,stopped: Boolean=false): DialogTurn {
        generation++; prompt=text
        state=when { stopped -> DialogState.STOPPED; accepted!=null -> DialogState.PROMPTING; awaitingConfirmation -> DialogState.VERIFYING; retries>=maxRetries -> DialogState.FALLBACK; else -> DialogState.PROMPTING }
        return DialogTurn(generation,text,listen,accepted,stopped)
    }
    fun begin(next: VoiceSlot,text: String,valid: Map<String,String>,allowImplicit: Boolean=false): DialogTurn {
        slot=next; choices=valid.mapKeys { n(it.key) }; question=text; implicit=allowImplicit
        pending=null; awaitingConfirmation=false; retries=0
        return turn(text)
    }
    fun waitForScreen() { generation++; state=DialogState.WAITING_SCREEN; awaitingConfirmation=false; pending=null }
    fun stop() = turn("음성 주문을 중지했습니다.",false,stopped=true)
    fun afterPrompt(token: Long): Boolean {
        if(token!=generation || state==DialogState.STOPPED || state==DialogState.WAITING_SCREEN) return false
        state=if(awaitingConfirmation) DialogState.VERIFYING else if(retries>=maxRetries) DialogState.FALLBACK else DialogState.LISTENING
        return true
    }
    fun input(text: String,evidence: SpeechEvidence=SpeechEvidence()): DialogTurn {
        val value=n(text)
        if(value in listOf("취소","중지","그만","그만해","음성주문중지","안내중지")) return stop()
        if(value in listOf("다시","다시말할게","처음부터","다시말하기")) { retries=0; awaitingConfirmation=false; pending=null; return turn(question) }
        if(value in listOf("재안내","다시안내","다시읽어줘")) return turn(prompt)
        if(awaitingConfirmation) {
            if(value in listOf("네","예","응","맞아","맞아요","맞습니다","좋아요","확인")) {
                val accepted=pending!!; awaitingConfirmation=false; pending=null; retries=0
                return turn("${label(accepted)} 선택할게요.",false,accepted)
            }
            if(value in listOf("아니","아니요","아니오","틀렸어","틀렸어요","틀립니다")) {
                awaitingConfirmation=false; pending=null; return retry("다시 말씀해 주세요.")
            }
            // Immediate re-speaking replaces the pending choice and gets a new confirmation.
        }
        val clean=value.replace(Regex("주세요$|로할게요$|으로할게요$|할게요$|요$"),"")
        val exact=choices[value] ?: choices[clean]
        val accepted=exact ?: if(slot==VoiceSlot.MENU && clean.length in 2..60 && Regex("[가-힣a-z0-9]+").matches(clean)
            && clean !in listOf("아니","아니오","아니요","맞아요","맞아","취소","다음","없어요")) clean else null
        if(accepted==null) return retry(if(value.isBlank()) "음성을 듣지 못했습니다." else "현재 화면의 선택으로 이해하지 못했습니다.")
        if(exact!=null && slot in listOf(VoiceSlot.RECOMMENDATION,VoiceSlot.EXACT_SEARCH,VoiceSlot.SCAN_CONTROL,VoiceSlot.NAVIGATION,VoiceSlot.CHECKOUT,VoiceSlot.DRAFT_CONFIRM,VoiceSlot.SCREEN_MENU_CONFIRM)) {
            awaitingConfirmation=false; pending=null; retries=0
            return turn(if(accepted=="아니요") "다른 방법으로 확인할게요." else "알겠습니다.",false,accepted)
        }
        if(slot==VoiceSlot.DRAFT && exact in listOf("메뉴 추가","목록 읽기","주문 시작")) return turn("알겠습니다.",false,exact)
        if(implicit && !alwaysConfirm && slot !in listOf(VoiceSlot.DRAFT,VoiceSlot.EXTRA_OPTION,VoiceSlot.MENU_CANDIDATE) && exact!=null && evidence.strong() && !awaitingConfirmation) { retries=0; return turn("${label(accepted)} 선택할게요.",false,accepted) }
        pending=accepted; awaitingConfirmation=true; retries=0
        return turn("${label(accepted)} 맞으신가요? 네 또는 아니요라고 말씀해 주세요.")
    }
    private fun label(value: String)=when(value) { "ice" -> "아이스"; "hot" -> "따뜻한 음료"; else -> value }
    private fun retry(reason: String): DialogTurn {
        pending=null; awaitingConfirmation=false; retries++
        state=DialogState.RETRY_VOICE_CAPTURE
        val examples=choices.values.distinct().take(3).joinToString(", ") { label(it) }
        return if(retries>=maxRetries) turn("$reason 선택 예시는 $examples 입니다. 다시 또는 취소라고 말씀해 주세요.")
        else turn("$reason $question")
    }
}
