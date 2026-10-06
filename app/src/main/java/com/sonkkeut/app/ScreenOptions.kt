package com.sonkkeut.app

data class ExtraOption(val label: String, val surcharge: Int?)
data class ScreenOptionGroup(val name: String, val choices: List<ExtraOption>)

/** Only recognize explicit, bounded option labels; never invent options from menu names. */
object ScreenOptions {
    fun groups(screen: RecognizedScreen): List<ScreenOptionGroup> {
        if(screen.type!="option") return emptyList()
        val candidates=screen.elements.filter { it.readable && it.kind=="button" && it.box.size==4 &&
            it.box.all { v -> v.isFinite() && v in 0.0..1.0 } && it.box[2]>it.box[0] && it.box[3]>it.box[1] }
        return candidates.groupBy { NativeOrderParser.normalize(it.text) }.values.filter { it.size==1 }.mapNotNull { entries ->
            val text=entries.single().text
            val n=NativeOrderParser.normalize(text)
            val label=n.replace(Regex("[（(]?\\+?[0-9,]+원[）)]?$"), "")
            val group=when {
                Regex("당도(0|25|30|50|70|75|100)%").matches(label) -> "당도"
                label in listOf("얼음없음","얼음적게","얼음보통","얼음많이") -> "얼음"
                Regex("샷추가없음|기본샷|[1-3]샷추가|샷[1-3]개추가").matches(label) -> "샷"
                Regex("맵기(?:순한맛|순하게|보통|매운맛|아주매운맛|[0-5]단계)").matches(label) -> "맵기"
                Regex("굽기(?:레어|미디엄|미디엄레어|미디엄웰던|웰던)").matches(label) -> "굽기"
                else -> return@mapNotNull null
            }
            val price=Regex("\\+?([0-9,]+)원[）)]?$").find(n)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            group to ExtraOption(text,price)
        }.groupBy({it.first},{it.second}).map { ScreenOptionGroup(it.key,it.value) }
    }
    /** Unknown labels are read as visible choices; their grouping/meaning is never guessed. */
    fun unclassified(screen: RecognizedScreen,menuRegionId: String?): List<RecognizedElement> {
        val known=groups(screen).flatMap { it.choices }.map { NativeOrderParser.normalize(it.label) }.toSet()
        return screen.elements.filter { e -> e.readable && e.kind=="button" && e.id!=menuRegionId && e.box.size==4 &&
            e.box.all { v -> v.isFinite() && v in 0.0..1.0 } && e.box[2]>e.box[0] && e.box[3]>e.box[1] &&
            e.text.isNotBlank() && e.text.length<=80 && NativeOrderParser.normalize(e.text) !in known &&
            !Regex("^(담기|장바구니담기|장바구니에담기|추가하기|취소|닫기|뒤로|결제|결제하기|주문하기|[-+]|수량|ice|iced|아이스|차갑게|hot|핫|따뜻한|따뜻하게|스몰|미디엄|라지|톨|그란데|벤티|small|medium|large|[0-9]+)$").matches(NativeOrderParser.normalize(e.text)) }
            .take(20)
    }
}
