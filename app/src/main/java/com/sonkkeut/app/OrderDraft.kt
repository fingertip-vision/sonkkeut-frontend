package com.sonkkeut.app

/** An editable proposal only. Never mutates an order already sent to kiosk guidance. */
class OrderDraft {
    var items: List<NativeOrderItem> = emptyList(); private set
    private val history = ArrayDeque<List<NativeOrderItem>>()
    private fun save(next: List<NativeOrderItem>) {
        require(next.size <= 10 && next.all { it.qty in 1..10 })
        history.addLast(items); if(history.size>30) history.removeFirst()
        items=next
    }
    fun add(item: NativeOrderItem) = save(items+item)
    fun quantity(index: Int, count: Int) { require(index in items.indices); save(items.mapIndexed { i,v -> if(i==index) v.copy(qty=count) else v }) }
    fun remove(index: Int) { require(index in items.indices); save(items.filterIndexed { i,_ -> i!=index }) }
    fun undo(): Boolean { if(history.isEmpty()) return false; items=history.removeLast(); return true }
    fun clear() { items=emptyList(); history.clear() }
    fun summary() = if(items.isEmpty()) "주문 목록이 비어 있습니다." else items.mapIndexed { i,v -> "${i+1}번 ${v.menu} ${v.qty}개" }.joinToString(". ")
    fun choices(): Map<String,String> {
        val words=linkedMapOf("메뉴 추가" to "메뉴 추가", "추가" to "메뉴 추가", "하나 더 주문" to "메뉴 추가",
            "목록 읽기" to "목록 읽기", "목록" to "목록 읽기", "되돌리기" to "되돌리기")
        if(items.isNotEmpty()) { words["주문 시작"]="주문 시작"; words["다 골랐어"]="주문 시작"; words["완료"]="주문 시작" }
        val ordinals=listOf("첫번째","두번째","세번째","네번째","다섯번째","여섯번째","일곱번째","여덟번째","아홉번째","열번째")
        items.forEachIndexed { i,item ->
            val prefixes=mutableListOf("${i+1}번",ordinals[i])
            if(items.count { NativeOrderParser.normalize(it.menu)==NativeOrderParser.normalize(item.menu) }==1) prefixes+=item.menu
            prefixes.forEach { prefix ->
                words["$prefix 삭제"]="${i+1}번 삭제"
                if(item.qty<10) words["$prefix 하나 더"]="${i+1}번 ${item.qty+1}개로 변경"
                if(item.qty>1) words["$prefix 하나 빼"]="${i+1}번 ${item.qty-1}개로 변경"
                (1..10).forEach { qty ->
                    val canonical="${i+1}번 ${qty}개로 변경"
                    words["$prefix ${qty}개"]=canonical; words[canonical]=canonical
                    val korean=listOf("한","두","세","네","다섯","여섯","일곱","여덟","아홉","열")[qty-1]
                    words["$prefix $korean 개로 변경"]=canonical
                }
            }
        }
        return words
    }
    fun edit(command: String): Boolean {
        if(command=="되돌리기") return undo()
        Regex("(\\d+)번 삭제").matchEntire(command)?.let { remove(it.groupValues[1].toInt()-1); return true }
        Regex("(\\d+)번 (\\d+)개로 변경").matchEntire(command)?.let { quantity(it.groupValues[1].toInt()-1,it.groupValues[2].toInt()); return true }
        return false
    }
}
