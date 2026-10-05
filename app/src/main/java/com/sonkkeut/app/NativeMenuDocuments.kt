package com.sonkkeut.app

import kr.sonkkeut.android.MenuDocument
import kr.sonkkeut.android.MenuSpeechIndex
import org.json.JSONArray
import org.json.JSONObject

/** Store-provided knowledge is recommendation metadata, never an exact ordering alias. */
internal object NativeMenuDocuments {
    fun parse(item: JSONObject): MenuDocument {
        val name=item.getString("name")
        require(name.isNotBlank() && name.length<=80)
        val aliases=item.getJSONArray("aliases")
        require(aliases.length()<=100)
        val names=(0 until aliases.length()).map { aliases.getString(it).also { value -> require(value.length in 1..80) } }
        val price=if(item.has("price") && !item.isNull("price")) item.getInt("price").also { require(it in 0..10000000) } else null
        val key=if(item.has("related_terms")) "related_terms" else "relatedTerms"
        val terms=if(!item.has(key) || item.isNull(key)) emptyList() else {
            val array=item.getJSONArray(key); require(array.length()<=100)
            (0 until array.length()).map { array.getString(it).also { value -> require(value.isNotBlank() && value.length<=80) } }.distinct()
        }
        val description=if(!item.has("description") || item.isNull("description")) "" else item.getString("description").also { require(it.length<=2000) }
        return MenuDocument(name,names,item.getBoolean("sold_out"),item.optString("category"),price,terms,description)
    }

    /** Canonical names reserve their spelling even when sold out. Other aliases stay ambiguous. */
    fun canonicalAliases(catalog: List<MenuDocument>): List<MenuDocument> {
        val names=catalog.groupBy { MenuSpeechIndex.normalize(it.name) }
        return catalog.map { menu -> menu.copy(aliases=menu.aliases.filter { alias ->
            names[MenuSpeechIndex.normalize(alias)].orEmpty().all { it.name==menu.name }
        }) }
    }

    /** The legacy scoped SQLite snapshot omits optional metadata; restore it by canonical name. */
    fun restoreCatalog(stored: JSONArray, source: List<MenuDocument>): List<MenuDocument> {
        val metadata=source.associateBy { it.name }
        return canonicalAliases((0 until stored.length()).map { index ->
            val row=parse(stored.getJSONObject(index))
            val original=requireNotNull(metadata[row.name]) { "매장 메뉴 문맥이 변경되었습니다." }
            row.copy(price=original.price,relatedTerms=original.relatedTerms,description=original.description)
        })
    }
}
