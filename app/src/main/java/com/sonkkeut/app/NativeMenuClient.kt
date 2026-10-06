package com.sonkkeut.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.sonkkeut.android.MenuDocument
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class NativeMenu(val store: String, val version: Int, val items: List<MenuDocument>, val offline: Boolean = false)
class NativeMenuClient(context: Context) {
    private val storage = context.getSharedPreferences("native_menu", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS)
        .callTimeout(10,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    suspend fun load(server: String, code: String): NativeMenu = withContext(Dispatchers.IO) {
        require(Regex("[A-Z0-9]{6}").matches(code)) { "매장 코드는 영문·숫자 6자리입니다." }
        val key = "$server|$code"
        val cache = storage.getString(key,null)
        try {
            client.newCall(Request.Builder().url("$server/api/stores/$code/menu").header("Accept","application/json").build()).execute().use { response ->
                if (!response.isSuccessful && response.code in 400..499) throw IllegalArgumentException("매장 메뉴를 확인하지 못했습니다 (${response.code}).")
                check(response.isSuccessful) { "서버가 메뉴를 반환하지 못했습니다." }
                val bytes = requireNotNull(response.body).byteStream().use { input ->
                    val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val count = input.read(buffer); if (count < 0) break; require(result.size() + count <= 1000000); result.write(buffer,0,count) }; result.toByteArray()
                }
                val text = bytes.toString(Charsets.UTF_8)
                val menu = parse(text,code,server)
                storage.edit().putString(key,text).apply(); menu
            }
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { if (cache != null) parse(cache,code,server).copy(offline=true) else throw e }
    }
    private fun parse(text: String, code: String, server: String): NativeMenu {
        val json = JSONObject(text)
        require(json.getString("store_code") == code && json.getInt("menu_version") >= 0)
        val array = json.getJSONArray("items"); require(array.length() <= 1000)
        val seen = mutableSetOf<String>()
        val items = (0 until array.length()).map { i -> val e = array.getJSONObject(i)
            val name = e.getString("name"); require(name.isNotBlank() && name.length <= 80 && seen.add(name))
            val a = e.getJSONArray("aliases"); require(a.length() <= 100)
            val aliases = (0 until a.length()).map { a.getString(it).also { alias -> require(alias.length in 1..80) } }
            val price = if (e.has("price") && !e.isNull("price")) e.getInt("price").also { require(it in 0..10000000) } else null
            val terms=e.optJSONArray("search_terms")
            require(terms==null || terms.length()<=30)
            val related=if(terms==null) emptyList() else (0 until terms.length()).map { terms.getString(it).also { term -> require(term.length in 1..80) } }
            val description=if(e.isNull("description")) "" else e.optString("description").also { require(it.length<=500) }
            // Namespace backend IDs by server and store; old API payloads fall back to the
            // canonical name, never list position or an index that changes after refresh.
            val identity=JSONArray(listOf(server,code,if(e.has("id") && !e.isNull("id")) "id:"+e.get("id").toString() else "name:"+name)).toString()
            MenuDocument(name,aliases,e.getBoolean("sold_out"),e.optString("category"),price,related,description,identity)
        }
        return NativeMenu(json.getString("store_name"),json.getInt("menu_version"),items)
    }
}
