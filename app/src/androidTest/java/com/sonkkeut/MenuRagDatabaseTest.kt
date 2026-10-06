package com.sonkkeut

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kr.sonkkeut.android.MenuRagDatabase
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenuRagDatabaseTest {
    private val first = """[{"name":"아메리카노","aliases":["아아"],"sold_out":false}]"""
    private val second = """[{"name":"카페라떼","aliases":["라떼"],"sold_out":true}]"""
    @Test fun catalogsAreScopedAndSurviveDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MenuRagDatabase(context).use { db ->
            assertEquals("아메리카노", JSONArray(db.catalog("test/A/v1", first)).getJSONObject(0).getString("name"))
            db.catalog("test/B/v1", second)
        }
        MenuRagDatabase(context).use { db ->
            assertEquals("아메리카노", JSONArray(db.catalog("test/A/v1", first)).getJSONObject(0).getString("name"))
            val changed = JSONArray(db.catalog("test/A/v1", second))
            assertEquals(1, changed.length()); assertTrue(changed.getJSONObject(0).getBoolean("sold_out"))
        }
    }
    @Test fun catalogStoresMenuFieldsOnlyAndRejectsMalformedDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MenuRagDatabase(context).use { db ->
            val raw = """[{"name":"메뉴","aliases":[],"sold_out":false,"transcript":"private utterance"}]"""
            assertFalse(db.catalog("test/privacy/v1", raw).contains("transcript"))
            try { db.catalog("test/privacy/v1", """[{"name":"","aliases":[],"sold_out":false}]"""); fail("blank menu accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals("메뉴", JSONArray(db.catalog("test/privacy/v1", raw)).getJSONObject(0).getString("name"))
        }
    }
}
