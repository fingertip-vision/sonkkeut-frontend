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
                else -> return@mapNotNull null
            }
            val price=Regex("\\+?([0-9,]+)원[）)]?$").find(n)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            group to ExtraOption(text,price)
        }.groupBy({it.first},{it.second}).map { ScreenOptionGroup(it.key,it.value) }
    }
}
