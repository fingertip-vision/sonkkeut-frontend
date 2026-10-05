package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import org.json.JSONObject

data class NativeOrderItem(val menu: String, val qty: Int, val price: Int?, val temperature: String? = null, val size: String? = null)
data class NativeOrder(val items: List<NativeOrderItem>, val dine: String?) {
    fun confirmation() = items.joinToString(", ") { "${if (it.temperature == "ice") "아이스 " else if (it.temperature == "hot") "따뜻한 " else ""}${it.menu}${it.size?.let { s -> " $s" } ?: ""} ${it.qty}개" } + (dine?.let { ", $it" } ?: "") + " 맞나요?"
}
object NativeOrderParser {
    private val quantities = Regex("(\\d+|하나|다섯|여섯|일곱|여덟|아홉|한|두|둘|세|셋|네|넷|열)(?:잔|개)")
    private val numbers = mapOf("한" to 1,"하나" to 1,"두" to 2,"둘" to 2,"세" to 3,"셋" to 3,"네" to 4,"넷" to 4,"다섯" to 5,"여섯" to 6,"일곱" to 7,"여덟" to 8,"아홉" to 9,"열" to 10)
    private val conjunction = Regex("그리고|하고|이랑|랑|와|과")
    fun normalize(s: String) = s.lowercase().replace(Regex("[\\s,·.]"), "")
    fun parse(raw: String, menu: List<MenuDocument>): NativeOrder {
        require(!Regex("\\d[.,]\\d\\s*(?:잔|개)").containsMatchIn(raw)) { "수량은 메뉴당 1개에서 10개까지 정수로 입력해 주세요." }
        val text = normalize(raw).replace("매장에서", "매장").replace(Regex("먹고갈게요|먹고가요"), "먹고갈")
            .replace(Regex("주시겠어요|주문할게요|할게요|줘요|부탁드려요"), "주세요")
        require(text.isNotBlank()) { "주문을 말씀해 주세요." }
        data class Hit(val item: MenuDocument, val start: Int, val length: Int)
        val hits = menu.flatMap { item -> (listOf(item.name) + item.aliases).map(::normalize).filter { it.isNotEmpty() }.distinct().flatMap { name ->
            val found = mutableListOf<Hit>(); var start = text.indexOf(name)
            while (start >= 0) { found += Hit(item, start, name.length); start = text.indexOf(name, start + name.length) }; found
        } }.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.length })
        val selected = mutableListOf<Hit>()
        for (hit in hits) {
            val prev = selected.lastOrNull()
            require(prev == null || prev.start != hit.start || prev.length != hit.length || prev.item.name == hit.item.name) { "같은 별칭의 메뉴가 여러 개입니다. 메뉴를 선택해 주세요." }
            if (prev == null || hit.start >= prev.start + prev.length) selected += hit
        }
        require(selected.isNotEmpty()) { "해당 메뉴를 찾지 못했습니다. 화면 읽기로 메뉴를 확인해 주세요." }
        var residue = text
        for (hit in selected.asReversed()) residue = residue.removeRange(hit.start, hit.start + hit.length)
        val takeout = Regex("포장|테이크아웃|가져갈|가지고갈|들고갈").containsMatchIn(residue)
        val dinein = Regex("매장|먹고갈|먹고가|여기서").containsMatchIn(residue)
        require(!(takeout && dinein)) { "매장 이용과 포장 중 하나만 말씀해 주세요." }
        residue = residue.replace(quantities, "")
            .replace(Regex("따뜻한|따뜻하게|뜨거운|차가운|차갑게|시원한|아이스|핫|hot|ice|라지|스몰|large|small"), "")
            .replace(Regex("포장|테이크아웃|가져갈|가지고갈|들고갈|매장|먹고갈|먹고가|여기서|해주세요"), "")
            .replace(Regex("그리고|이랑|랑|하고|와|과|으로|로|주세요|주문|부탁해요|부탁합니다|요"), "")
        require(residue.isEmpty()) { "일부 메뉴나 옵션을 이해하지 못했습니다. 메뉴, 온도, 수량으로 다시 말씀해 주세요." }
        val items = selected.mapIndexed { index, hit ->
            require(!hit.item.soldOut) { "${hit.item.name}는 품절입니다." }
            val before = text.substring(selected.getOrNull(index - 1)?.let { it.start + it.length } ?: 0, hit.start)
            val after = text.substring(hit.start + hit.length, selected.getOrNull(index + 1)?.start ?: text.length)
            val previousQty = quantities.findAll(before).lastOrNull()
            val ownBefore = if (index == 0) before else if (conjunction.containsMatchIn(before)) before.split(conjunction).last()
                else previousQty?.let { before.substring(it.range.last + 1) } ?: ""
            val suffix = after.split(conjunction).first()
            val afterQty = quantities.findAll(suffix).toList()
            val ownAfter = if (selected.getOrNull(index + 1) != null && !conjunction.containsMatchIn(after) && afterQty.isNotEmpty()) suffix.substring(0, afterQty[0].range.last + 1) else suffix
            val allQty = quantities.findAll(ownBefore).toList() + afterQty
            require(allQty.size <= 1) { "${hit.item.name}의 수량을 하나만 말씀해 주세요." }
            val value = allQty.firstOrNull()?.groupValues?.get(1)
            val qty = if (value == null) 1 else numbers[value] ?: value.toIntOrNull() ?: 0
            require(qty in 1..10) { "수량은 메뉴당 1개에서 10개까지 입력해 주세요." }
            val options = ownBefore + ownAfter
            val ice = Regex("아이스|차가운|차갑|시원|ice").containsMatchIn(options) || text.substring(hit.start, hit.start + hit.length) == "아아"
            val hot = Regex("따뜻|뜨거|핫|hot").containsMatchIn(options)
            require(!(ice && hot)) { "${hit.item.name}의 온도를 하나만 말씀해 주세요." }
            val large = Regex("라지|large").containsMatchIn(options); val small = Regex("스몰|small").containsMatchIn(options)
            require(!(large && small)) { "${hit.item.name}의 크기를 하나만 말씀해 주세요." }
            NativeOrderItem(hit.item.name, qty, hit.item.price, if (ice) "ice" else if (hot) "hot" else null, if (large) "라지" else if (small) "스몰" else null)
        }
        return NativeOrder(items, if (takeout) "포장" else if (dinein) "매장" else null)
    }
}

data class RecognizedElement(val id: String, val kind: String, val text: String, val box: List<Double>, val readable: Boolean, val price: Int? = null)
data class RecognizedScreen(val type: String, val keyframe: Int, val elements: List<RecognizedElement>, val cartCount: Int?, val total: Int?, val selected: List<String>?) {
    companion object {
        fun from(json: JSONObject): RecognizedScreen {
            val array = json.getJSONArray("elements")
            val elements = (0 until array.length()).map { i -> val e = array.getJSONObject(i); val b = e.getJSONArray("box")
                RecognizedElement(e.getString("id"),e.getString("kind"),e.optString("text"), (0 until b.length()).map { b.getDouble(it) },
                    e.optDouble("conf", 0.0) >= .8 && !e.optBoolean("uncertain") && (!e.has("conf_ocr") || e.optDouble("conf_ocr", 0.0) >= .8),
                    if(e.has("price") && !e.isNull("price")) e.getInt("price").takeIf { it in 0..10000000 } else null) }
            fun integer(name: String) = if (json.has(name) && !json.isNull(name)) json.getInt(name) else null
            val selected = json.optJSONArray("selected")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            return RecognizedScreen(json.getString("screen_type"),json.getInt("keyframe_id"),elements,integer("cart_count"),integer("total_price"),selected)
        }
    }
    fun reading() = elements.sortedWith(compareBy<RecognizedElement> { it.box.getOrElse(1) { 0.0 } }.thenBy { it.box.getOrElse(0) { 0.0 } })
        .joinToString(". ") { if (it.readable) it.text.ifBlank { "읽기 불확실" } else "읽기 불확실" }
    fun detectedMenu(): List<MenuDocument> = if(type!="menu") emptyList() else elements.filter { it.kind=="menu" && it.readable && it.text.isNotBlank() }
        .map { MenuDocument(it.text.replace(Regex("[\\d,]+\\s*원"),"").trim(),price=it.price) }.filter { it.name.isNotBlank() }.distinctBy { it.name }
}
data class NativeAction(val target: RecognizedElement, val expect: Map<String, Any?>, val role: String, val message: String, val value: String? = null)
