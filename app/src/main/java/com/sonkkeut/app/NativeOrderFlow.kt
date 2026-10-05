package com.sonkkeut.app

/** Keeps explicit confirmation and the original duplicate-add/cart verification policy. */
class NativeOrderFlow {
    var state = "S0"; private set
    var order: NativeOrder? = null; private set
    var screen: RecognizedScreen? = null; private set
    var action: NativeAction? = null; private set
    var message = "메뉴를 읽고 주문을 말씀해 주세요."; private set
    var paused = false
    private var confirmed = false
    private var lastKeyframe = -1
    private var remaining = mutableListOf<Int>()
    private val configured = mutableSetOf<String>()
    private val visited = mutableSetOf<String>()
    private data class PendingAdd(val item: Int, val count: Int?, val keyframe: Int)
    private var pending: PendingAdd? = null
    fun currentItem() = order?.items?.getOrNull(remaining.indexOfFirst { it>0 })
    fun allAdded() = order!=null && remaining.none { it>0 }
    fun optionApplied(value: String) = value in configured || screen?.elements.orEmpty().any { e ->
        e.id in screen?.selected.orEmpty() && when(value) { "ice" -> Regex("^(ice|iced|아이스|차갑게)$").matches(NativeOrderParser.normalize(e.text)); "hot" -> Regex("^(hot|핫|따뜻한|따뜻하게)$").matches(NativeOrderParser.normalize(e.text)); else -> NativeOrderParser.normalize(e.text)==NativeOrderParser.normalize(value) }
    }
    fun updateChoice(field: String,value: String): NativeOrder? {
        val intent=order ?: return null; val index=remaining.indexOfFirst { it>0 }
        order=when(field) { "dine" -> intent.copy(dine=value); "temperature" -> intent.copy(items=intent.items.mapIndexed { i,item -> if(i==index) item.copy(temperature=value) else item }); "size" -> intent.copy(items=intent.items.mapIndexed { i,item -> if(i==index) item.copy(size=value) else item }); else -> intent }
        action=null; enter("S2","화면의 선택을 다시 확인합니다."); return order
    }
    private fun enter(next: String, text: String) { state = next; message = text; if (next in listOf("SE","S6")) action = null }
    fun submit(value: NativeOrder) {
        order = value; remaining = value.items.map { it.qty }.toMutableList(); confirmed = false; action = null
        pending = null; configured.clear(); visited.clear(); enter("S3", value.confirmation())
    }
    fun confirm(): NativeAction? { if (state != "S3" || paused) return null; confirmed = true; return plan() }
    fun accept(value: RecognizedScreen): NativeAction? {
        if (paused || state == "S6" || value.keyframe <= lastKeyframe) return null
        lastKeyframe = value.keyframe; screen = value
        if (state == "S5") return null
        pending?.let {
            if (value.keyframe > it.keyframe && it.count != null && value.cartCount == it.count + 1 && value.type in listOf("menu","cart")) complete(it.item)
            else { enter("SE", "중단된 담기의 결과를 확인하지 못했습니다. 장바구니를 확인해 주세요."); return null }
        }
        if (!confirmed) { enter("S3", order?.confirmation() ?: "무엇을 주문할까요?"); return null }
        return plan()
    }
    fun press() {
        if (state != "S4" || paused) return
        if (action?.role == "add") pending = PendingAdd(remaining.indexOfFirst { it > 0 }, screen?.cartCount, lastKeyframe)
        enter("S5", "지금 누르세요")
    }
    private fun complete(index: Int) {
        if (index in remaining.indices && remaining[index] > 0) remaining[index]--
        pending = null; configured.clear(); visited.clear()
    }
    fun verdict(result: String, reason: String, text: String) {
        if (paused || state != "S5") return
        val previous = action; action = null
        if (result != "success") {
            if (result == "fail" && reason == "no_change") pending = null
            enter("SE", text); return
        }
        if (previous?.role == "option") previous.value?.let { configured += it }
        if (previous?.role == "add") complete(pending?.item ?: remaining.indexOfFirst { it > 0 })
        enter("S2", "다음 화면을 확인합니다")
        // Re-plan only after a fresh screen arrives; never re-use a pre-press snapshot.
    }
    fun recover() { action = null; enter("S2", "화면을 다시 확인합니다") }
    fun plan(): NativeAction? {
        if (paused || state in listOf("S5","S6") || !confirmed) return null
        val intent = order ?: return null; val current = screen ?: run { enter("S2","카메라로 키오스크 화면을 비춰 주세요."); return null }
        action = null
        if (pending != null) { enter("SE", "중단된 담기의 결과를 먼저 확인해 주세요."); return null }
        if (current.type == "payment") { enter(if (remaining.any { it > 0 }) "SE" else "S6", if (remaining.any { it > 0 }) "남은 주문이 있습니다. 주문 내역을 확인해 주세요." else "결제 화면입니다. 안내를 마칩니다."); return null }
        val usable = current.elements.filter { it.readable && it.kind in listOf("tab","menu","button","back") && it.text.isNotBlank()
            && it.box.size == 4 && it.box.all { p -> p.isFinite() && p in 0.0..1.0 } && it.box[2] > it.box[0] && it.box[3] > it.box[1] }
        fun find(pattern: String) = usable.firstOrNull { Regex(pattern).containsMatchIn(NativeOrderParser.normalize(it.text)) }
        fun choose(target: RecognizedElement?, role: String, expect: Map<String, Any?>, text: String? = null, value: String? = null): NativeAction? {
            if (target == null) { enter("SE", "버튼을 확실하게 읽지 못했습니다. 각도를 바꾸고 다시 확인해 주세요."); return null }
            val next = NativeAction(target, expect, role, text ?: "${target.text} 버튼으로 안내합니다", value)
            action = next; enter("S4", next.message); return next
        }
        val index = remaining.indexOfFirst { it > 0 }
        if (current.type == "method") {
            val dine = intent.dine ?: run { enter("SE", "매장 이용인지 포장인지 주문 문장에 넣어 확인해 주세요."); return null }
            return choose(find(if (dine == "포장") "^(포장|포장하기|테이크아웃)$" else "^(매장|매장이용|매장에서먹기|먹고가기)$"), "navigate", mapOf("screen_type" to "menu"))
        }
        if (index < 0) {
            if (current.type != "cart") return choose(find("장바구니|주문내역"), "navigate", mapOf("screen_type" to "cart"))
            if (current.cartCount != null && current.cartCount != intent.items.sumOf { it.qty }) { enter("SE","주문 수량과 장바구니 수량이 다릅니다."); return null }
            val expected = if (intent.items.all { it.price != null }) intent.items.sumOf { it.price!! * it.qty } else null
            if (expected != null && current.total != expected) { enter("SE", if (current.total == null) "총 금액을 읽지 못했습니다. 장바구니를 다시 확인해 주세요." else "주문 예상 금액과 장바구니 금액이 다릅니다."); return null }
            return choose(find("^(결제|결제하기|주문하기|카드결제)$"), "checkout", mapOf("screen_type" to "payment"), "${current.total?.let { "${it}원입니다. " } ?: ""}결제 버튼으로 안내합니다")
        }
        val item = intent.items[index]
        fun isItem(e: RecognizedElement) = NativeOrderParser.normalize(e.text.replace(Regex("[\\d,]+\\s*원"),"")) == NativeOrderParser.normalize(item.menu)
        when (current.type) {
            "menu" -> {
                usable.firstOrNull { it.kind == "menu" && isItem(it) }?.let { return choose(it,"menu",mapOf("screen_type" to "option")) }
                val move = usable.firstOrNull { it.kind == "tab" && NativeOrderParser.normalize(it.text) !in visited } ?: find("다음페이지|다음|더보기")
                if (move != null && visited.size < 4) { visited += NativeOrderParser.normalize(move.text); return choose(move,"navigate",mapOf("changed" to true)) }
                enter("SE", "${item.menu}를 찾지 못했습니다. 화면 읽기로 메뉴를 확인해 주세요.")
            }
            "option" -> {
                if (usable.none(::isItem)) { enter("SE","다른 메뉴의 옵션 화면입니다. 뒤로 돌아가 주문 메뉴를 확인해 주세요."); return null }
                for (value in listOfNotNull(item.temperature,item.size)) {
                    val target = find(when(value) { "hot" -> "^(hot|핫|따뜻한|따뜻하게)$"; "ice" -> "^(ice|iced|아이스|차갑게)$"; else -> "^${Regex.escape(NativeOrderParser.normalize(value))}$" })
                    if (target != null && current.selected?.contains(target.id) == true) configured += value
                    else if (target == null || current.selected != null || value !in configured) return choose(target,"option",mapOf("selected" to target?.id,"changed" to true),value=value)
                }
                return choose(find("^(담기|장바구니담기|장바구니에담기|추가하기)$"),"add",mapOf("screen_type_not" to "option","cart_delta" to 1,"success_speak" to "담겼습니다"))
            }
            "cart" -> return choose(find("계속주문|메뉴로|추가주문|더주문|뒤로"),"navigate",mapOf("screen_type" to "menu"))
            "start" -> return choose(find("주문시작|시작하기"),"navigate",mapOf("screen_type" to "menu"))
            else -> enter("SE","화면 종류를 확실히 알 수 없습니다. 화면 읽기를 사용하거나 다시 확인해 주세요.")
        }
        return null
    }
}
