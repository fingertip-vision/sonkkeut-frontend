package com.sonkkeut.app

import kr.sonkkeut.android.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeMenuKnowledgeTest {
    private fun screen(name: String)=RecognizedScreen("menu",1,listOf(RecognizedElement("menu","menu",name,listOf(0.1,0.1,0.8,0.3),true)),null,null,null)
    private fun row(name: String="에스프레소")=JSONObject().put("name",name).put("aliases",JSONArray()).put("sold_out",false).put("category","커피").put("price",3000)
    @Test fun olderMenuAndNullMetadataRemainCompatible() {
        val old=NativeMenuDocuments.parse(row())
        val nullable=NativeMenuDocuments.parse(row().put("related_terms",JSONObject.NULL).put("description",JSONObject.NULL))
        assertEquals(old,nullable); assertTrue(old.relatedTerms.isEmpty()); assertEquals("",old.description)
    }
    @Test fun registeredRelatedTermsAreRecommendationsNotOrderingAliases() {
        val m=NativeMenuDocuments.parse(row().put("related_terms",JSONArray(listOf("진한 커피"))).put("description","고소한 풍미"))
        val match=NativeMenuRecommendations.suggest("진한 커피 한 잔 주세요",listOf(m),screen(m.name)).single()
        assertEquals(m.name,match.menu.name); assertEquals("store_knowledge",match.basis)
        assertTrue(MenuMatcher(listOf(m)).exact("진한 커피").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse("진한 커피 한 잔",listOf(m)) }
        assertEquals("에스프레소",NativeMenuRecommendations.draft(match,listOf(m),screen(m.name)))
    }
    @Test fun registeredDescriptionAndCategoryCanProvideCandidateEvidence() {
        val described=MenuDocument("에스프레소",description="고소한 풍미")
        assertEquals("store_knowledge",MenuMatcher(listOf(described)).recommend("고소한 풍미").single().basis)
        val categorized=MenuDocument("치즈케이크",category="디저트")
        assertEquals("치즈케이크",MenuMatcher(listOf(categorized)).recommend("디저트").single().menu.name)
    }
    @Test fun alternateKeyIsReadAndOversizedMetadataRejected() {
        assertEquals(listOf("진한 커피"),NativeMenuDocuments.parse(row().put("relatedTerms",JSONArray(listOf("진한 커피")))).relatedTerms)
        assertThrows(IllegalArgumentException::class.java) { NativeMenuDocuments.parse(row().put("description","가".repeat(2001))) }
        assertThrows(IllegalArgumentException::class.java) { NativeMenuDocuments.parse(row().put("related_terms",JSONArray(listOf("가".repeat(81))))) }
    }
    @Test fun sqliteRoundTripRetainsKnowledgeAndPriceWithoutCrossStoreFallback() {
        val source=NativeMenuDocuments.parse(row().put("related_terms",JSONArray(listOf("진한 커피"))).put("description","고소한 풍미"))
        val stored=JSONArray().put(row().removePrice())
        val restored=NativeMenuDocuments.restoreCatalog(stored,listOf(source)).single()
        assertEquals(source,restored)
        assertEquals("store_knowledge",MenuMatcher(listOf(restored)).recommend("진한 커피").single().basis)
        assertThrows(IllegalArgumentException::class.java) { NativeMenuDocuments.restoreCatalog(stored,listOf(MenuDocument("다른 매장 메뉴"))) }
    }
    private fun JSONObject.removePrice(): JSONObject { remove("price"); return this }
    @Test fun canonicalNameWinsForTypedAndRecognizedTextRegardlessOfCatalogOrder() {
        val canonical=MenuDocument("라떼",price=3000)
        val other=MenuDocument("바닐라라떼",aliases=listOf("라떼"),price=5000)
        for(catalog in listOf(listOf(other,canonical),listOf(canonical,other))) {
            val safe=NativeMenuDocuments.canonicalAliases(catalog)
            val correction=MenuSpeechIndex(safe).correct("라떼 한 잔 포장")
            assertTrue(correction.ambiguities.isEmpty())
            assertEquals("라떼",NativeOrderParser.parse(correction.text,catalog).items.single().menu)
            assertEquals("라떼",NativeOrderParser.parse("라떼 한 잔 포장",catalog).items.single().menu)
        }
    }
    @Test fun soldOutCanonicalItemCannotBecomeAnAvailableAliasOrder() {
        val catalog=listOf(MenuDocument("바닐라라떼",aliases=listOf("라떼")),MenuDocument("라떼",soldOut=true))
        assertTrue(MenuMatcher(catalog).exact("라떼").isEmpty())
        val e=assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse("라떼 한 잔",catalog) }
        assertTrue(e.message!!.contains("품절"))
        val corrected=MenuSpeechIndex(NativeMenuDocuments.canonicalAliases(catalog)).correct("라떼 한 잔")
        assertTrue(corrected.ambiguities.isEmpty()); assertEquals("라떼 한 잔",corrected.text)
    }
    @Test fun ambiguousNonCanonicalAliasesStillRequireSelection() {
        val catalog=listOf(MenuDocument("카페라떼",aliases=listOf("우유커피")),MenuDocument("바닐라라떼",aliases=listOf("우유커피")))
        assertThrows(IllegalArgumentException::class.java) { NativeOrderParser.parse("우유커피 한 잔",catalog) }
        assertEquals(2,MenuSpeechIndex(NativeMenuDocuments.canonicalAliases(catalog)).correct("우유커피 한 잔").ambiguities.single().candidates.size)
    }
    @Test fun soldOutKnowledgeCandidatesNeverGetADraft() {
        val sold=MenuDocument("에스프레소",soldOut=true,relatedTerms=listOf("진한 커피"))
        assertTrue(NativeMenuRecommendations.suggest("진한 커피",listOf(sold),null).isEmpty())
        assertNull(NativeMenuRecommendations.draft(MenuMatch(sold,.97,"store_knowledge"),listOf(sold),screen(sold.name)))
    }
}
