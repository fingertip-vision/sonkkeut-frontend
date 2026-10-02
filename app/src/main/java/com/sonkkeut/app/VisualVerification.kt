package com.sonkkeut.app

// Python verification.py, revision ba55be0. Observations/expected effect supplied by caller.
private val screenTypes=setOf("menu","option","cart","payment","other")
data class CoreScreen(val type: String,val keyframe: Int,val elements: List<CoreElement>) {
    init { require(type in screenTypes && keyframe>=0 && elements.map { it.target.id }.toSet().size==elements.size) }
}
data class CoreExpectedEffect(val screenType: String?=null,val cartItemKey: String?=null,val beforeQuantity: Int?=null,val quantityDelta: Int=1) {
    init {
        require((screenType==null) != (cartItemKey==null) && quantityDelta>0)
        if(screenType != null) require(screenType in screenTypes-"other" && beforeQuantity==null)
        else require(!cartItemKey.isNullOrBlank() && beforeQuantity != null && beforeQuantity>=0)
    }
}
data class CoreVerificationResult(val verdict: String,val reason: String,val terminal: Boolean,val retainProgress: Boolean=true)
fun coreScreenChanged(before: CoreScreen,after: CoreScreen,boxIou: Double=.85): Boolean {
    require(boxIou.isFinite() && boxIou in 0.0..1.0)
    if(before.type != after.type || before.elements.size != after.elements.size) return true
    val unmatched=after.elements.toMutableList()
    for(old in before.elements) {
        val candidates=unmatched.mapIndexedNotNull { index,new ->
            if(old.target.kind==new.target.kind && old.text==new.text && old.price==new.price) index to old.target.box.iou(new.target.box) else null
        }
        if(candidates.isEmpty()) return true
        val selected=candidates.maxBy { it.second }
        if(selected.second<boxIou) return true
        unmatched.removeAt(selected.first)
    }
    return false
}
class CorePressVerifier(private val timeout: Double=1.5,private val minConfidence: Double=.6) {
    init { require(timeout.isFinite() && timeout>0 && minConfidence.isFinite() && minConfidence in 0.0..1.0) }
    private var before: CoreScreen?=null
    private var effect: CoreExpectedEffect?=null
    private var started=0.0
    private var lastTimestamp: Double?=null
    private var lastKeyframe: Int?=null
    private var lastCapture: Double?=null
    private var sawChange=false
    private var terminalResult: CoreVerificationResult?=null
    fun begin(screen: CoreScreen,expected: CoreExpectedEffect,now: Double) {
        require(now.isFinite() && now>=0 && expected.screenType != screen.type)
        before=screen; effect=expected; started=now; lastTimestamp=now; lastKeyframe=screen.keyframe
        lastCapture=null; sawChange=false; terminalResult=null
    }
    private fun finish(verdict: String,reason: String): CoreVerificationResult = CoreVerificationResult(verdict,reason,true).also { terminalResult=it }
    private fun uncertain(reason: String,terminal: Boolean): CoreVerificationResult = CoreVerificationResult("uncertain",reason,terminal).also { if(terminal) terminalResult=it }
    fun observe(after: CoreScreen?,now: Double,capturedAt: Double?=null,cartQuantities: Map<String,Int>?=null,cartKeyframe: Int?=null,returnedToStart: Boolean=false): CoreVerificationResult {
        require(now.isFinite() && (capturedAt==null || capturedAt.isFinite()))
        require(cartQuantities==null || cartQuantities.all { (key,qty) -> key.isNotBlank() && qty>=0 })
        val before=checkNotNull(this.before) { "begin before observing" }; val effect=checkNotNull(this.effect)
        require(now>=lastTimestamp!!); lastTimestamp=now
        terminalResult?.let { return it }
        val expired=now-started>=timeout
        if(returnedToStart) return finish("failure","returned_to_start")
        if(after==null) return uncertain("missing_screen",expired)
        if(capturedAt==null) return uncertain("missing_capture_time",expired)
        if(capturedAt<started || capturedAt>now || lastCapture?.let { capturedAt<=it }==true) return uncertain("stale_capture",expired)
        if(after.keyframe<=lastKeyframe!!) return uncertain("stale_keyframe",expired)
        lastKeyframe=after.keyframe; lastCapture=capturedAt
        if(after.type=="other" || after.elements.isEmpty() || after.elements.any { it.target.confidence<minConfidence } || before.elements.isEmpty() || before.type=="other" || before.elements.any { it.target.confidence<minConfidence }) return uncertain("uncertain_screen",expired)
        val changed=coreScreenChanged(before,after); sawChange=sawChange || changed
        if(capturedAt-started>timeout) {
            if(!sawChange) {
                if(effect.screenType != null) return finish("failure","no_screen_change")
                val item=effect.cartItemKey!!
                if(cartQuantities==null || item !in cartQuantities) return uncertain("missing_cart_evidence",true)
                if(cartKeyframe != after.keyframe) return uncertain("unbound_cart_evidence",true)
                if(cartQuantities[item]==effect.beforeQuantity) return finish("failure","cart_unchanged")
            }
            return uncertain("late_observation",true)
        }
        if(effect.screenType != null) {
            if(after.type==effect.screenType) return finish("success","expected_screen")
            if(changed && after.type != before.type) return finish("failure","unexpected_screen")
            if(expired) {
                if(capturedAt-started<timeout) return uncertain("incomplete_window",true)
                return finish("failure",if(sawChange) "unexpected_change" else "no_screen_change")
            }
            return uncertain("awaiting_expected_screen",false)
        }
        val item=effect.cartItemKey!!
        if(cartQuantities==null || item !in cartQuantities) return uncertain("missing_cart_evidence",expired)
        if(cartKeyframe != after.keyframe) return uncertain("unbound_cart_evidence",expired)
        val quantity=cartQuantities.getValue(item)
        val expectedQuantity=effect.beforeQuantity!!.toLong()+effect.quantityDelta.toLong()
        if(quantity.toLong()==expectedQuantity) return finish("success","expected_cart_quantity")
        if(quantity != effect.beforeQuantity) return finish("failure","unexpected_cart_quantity")
        if(expired) {
            if(capturedAt-started<timeout) return uncertain("incomplete_window",true)
            return finish("failure","cart_unchanged")
        }
        return uncertain("awaiting_cart_quantity",false)
    }
}
