package com.sonkkeut.app

data class CartReading(val message: String,val mismatch: Boolean,val complete: Boolean)
object CartReader {
    fun inspect(order: NativeOrder,screen: RecognizedScreen): CartReading {
        if(screen.type!="cart") return CartReading("장바구니 화면을 비춰 주세요.",false,false)
        val expected=order.items.groupBy { it.menu }.mapValues { it.value.sumOf { item -> item.qty } }
        val rows=screen.elements.filter { it.readable }.mapNotNull { e ->
            val text=NativeOrderParser.normalize(e.text)
            expected.keys.sortedByDescending { it.length }.firstNotNullOfOrNull { name ->
                Regex("^${Regex.escape(NativeOrderParser.normalize(name))}(?:수량)?([0-9]{1,2})(?:개|잔)(?:[0-9]+원)?$").matchEntire(text)?.let { name to it.groupValues[1].toInt() }
            }
        }
        // Repeated OCR rows may be duplicates or distinct items: do not aggregate by guesswork.
        val unique=rows.groupBy { it.first }.filterValues { it.size==1 }.mapValues { it.value.single().second }
        val differences=unique.filter { (name,qty) -> qty!=expected[name] }
        if(differences.isNotEmpty()) return CartReading(differences.entries.joinToString(". ") { "${it.key}는 주문 ${expected[it.key]}개, 화면 ${it.value}개로 읽혔습니다" }+". 내용을 다시 확인해 주세요.",true,false)
        val hasOptions=order.items.any { it.temperature!=null || it.size!=null || it.extras.isNotEmpty() }
        val complete=unique.keys==expected.keys && screen.cartCount==unique.values.sum() && !hasOptions
        val details=unique.entries.joinToString(", ") { "${it.key} ${it.value}개" }
        return CartReading(if(complete) "$details. 읽은 상품명과 수량이 주문과 일치합니다." else "${if(details.isBlank()) "" else "$details. "}각 상품의 수량 또는 옵션을 모두 대조하지 못했습니다.",false,complete)
    }
}

data class ReceiptReading(val completeText: String,val orderNumber: String?,val pickupText: String?) {
    fun speech()="화면에서 '$completeText' 문구를 읽었습니다. "+(orderNumber?.let { "주문 번호는 ${it.toCharArray().joinToString(" ")}입니다. " } ?: "주문 번호는 확인하지 못했습니다. ")+(pickupText?.let { "화면 안내: $it" } ?: "영수증과 매장의 수령 안내를 확인해 주세요.")
}
class ReceiptReader {
    private var previous: ReceiptReading?=null
    private var lastFrame=-1
    fun reset() { previous=null; lastFrame=-1 }
    fun observe(screen: RecognizedScreen): ReceiptReading? {
        if(screen.keyframe<=lastFrame) return null
        lastFrame=screen.keyframe
        val lines=screen.elements.filter { it.readable }.map { it.text.trim() }
        val normalized=lines.map { NativeOrderParser.normalize(it).trim('!', '?', ':') }
        if(normalized.any { Regex("결제실패|승인거절|결제취소|취소완료|결제미완료|결제오류").containsMatchIn(it) }) { previous=null; return null }
        val marker=normalized.firstOrNull { it in listOf("결제완료","결제가완료되었습니다","주문완료","주문이완료되었습니다","주문접수완료") } ?: run { previous=null; return null }
        val numbers=normalized.mapNotNull { Regex("^(?:주문번호|대기번호)[:：#]?([A-Za-z]?[0-9]{1,6})$").matchEntire(it)?.groupValues?.get(1) }.distinct()
        // A marker without a number is still useful; competing numbers are never guessed.
        val pickup=lines.singleOrNull { NativeOrderParser.normalize(it).startsWith("수령장소:") || NativeOrderParser.normalize(it).startsWith("픽업장소:") }
        val current=ReceiptReading(marker,numbers.singleOrNull(),pickup?.take(120))
        val stable=current==previous; previous=current
        return if(stable) current else null
    }
}
