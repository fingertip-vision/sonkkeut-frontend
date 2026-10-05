package com.sonkkeut.app

import androidx.test.platform.app.InstrumentationRegistry
import kr.sonkkeut.android.*
import org.junit.Assert.*
import org.junit.Test

class MenuKnowledgeDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun localKnowledgePersistsAndIsIsolatedByServerAndStore() {
        val server="https://fixture-${java.util.UUID.randomUUID()}.invalid"; val name="까르보나라"
        MenuKnowledgeStore(context).use { it.save(server,"STORE1",name,listOf("크림 파스타"),"개인 검색 설명") }
        MenuKnowledgeStore(context).use { db ->
            try {
                assertEquals(listOf("크림 파스타"),db.get(server,"STORE1",name)!!.terms)
                assertNull(db.get(server,"STORE2",name)); assertNull(db.get("$server/other","STORE1",name))
                val enriched=db.enrich(server,"STORE1",listOf(MenuDocument(name)))
                assertTrue(MenuMatcher(enriched).exact("크림 파스타").isEmpty())
                assertEquals(name,MenuMatcher(enriched).recommend("크림 파스타").single().menu.name)
            } finally { db.remove(server,"STORE1",name) }
            assertNull(db.get(server,"STORE1",name))
        }
    }
    @Test fun notesDoNotCreateMissingItemsOrOverrideSoldOutPrices() {
        val server="https://fixture-${java.util.UUID.randomUUID()}.invalid"
        MenuKnowledgeStore(context).use { db ->
            try {
                db.save(server,"STORE1","라떼",listOf("부드러운 음료"),"")
                assertTrue(db.enrich(server,"STORE1",emptyList()).isEmpty())
                val result=db.enrich(server,"STORE1",listOf(MenuDocument("라떼",soldOut=true,price=4000))).single()
                assertTrue(result.soldOut); assertEquals(4000,result.price)
                assertTrue(MenuMatcher(listOf(result)).recommend("부드러운 음료").isEmpty())
            } finally { db.remove(server,"STORE1","라떼") }
        }
    }
}
