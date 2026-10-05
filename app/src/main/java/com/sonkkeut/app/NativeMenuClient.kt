package com.sonkkeut.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.sonkkeut.android.MenuDocument
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
                val menu = parse(text,code)
                storage.edit().putString(key,text).apply(); menu
            }
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { if (cache != null) parse(cache,code).copy(offline=true) else throw e }
    }
    private fun parse(text: String, code: String): NativeMenu {
        val json = JSONObject(text)
        require(json.getString("store_code") == code && json.getInt("menu_version") >= 0)
        val array = json.getJSONArray("items"); require(array.length() <= 1000)
        val seen = mutableSetOf<String>()
        val items = (0 until array.length()).map { i -> val e = array.getJSONObject(i)
            val document=NativeMenuDocuments.parse(e)
            require(seen.add(document.name))
            document
        }
        return NativeMenu(json.getString("store_name"),json.getInt("menu_version"),NativeMenuDocuments.canonicalAliases(items))
    }
}
