package com.sonkkeut.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Opt-in aggregate outcomes only: never audio, text, coordinates, device IDs or menu choices. */
class NativeUsageReporter(context: Context) {
    private val preferences=context.getSharedPreferences("native_usage",0)
    private val mutex=Mutex()
    private val state=Any()
    private val generation=AtomicLong()
    @Volatile private var active: Call?=null
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    val enabled get()=preferences.getBoolean("consent",false)
    fun consent(value: Boolean) = synchronized(state) {
        generation.incrementAndGet(); active?.cancel()
        preferences.edit().putBoolean("consent",value).apply()
        if(!value) preferences.edit().remove("queue").apply()
    }
    suspend fun enqueue(base: String,usage: JSONObject? = null) = withContext(Dispatchers.IO) {
        val token=generation.get()
        mutex.withLock {
            val queue=synchronized(state) {
                if(!enabled || token!=generation.get()) return@withLock
                val items=runCatching { JSONArray(preferences.getString("queue","[]")) }.getOrElse { JSONArray() }
                if(usage!=null) items.put(JSONObject().put("base",base).put("usage",usage))
                val bounded=JSONArray((maxOf(0,items.length()-50) until items.length()).map { items.getJSONObject(it) })
                preferences.edit().putString("queue",bounded.toString()).apply(); bounded
            }
            while(queue.length()>0 && enabled && token==generation.get()) {
                val entry=queue.getJSONObject(0)
                try {
                    val call=client.newCall(Request.Builder().url(entry.getString("base")+"/api/stats/sessions")
                        .post(entry.getJSONObject("usage").toString().toRequestBody("application/json".toMediaType())).build())
                    synchronized(state) { if(!enabled || token!=generation.get()) return@withLock; active=call }
                    val discard=call.execute().use { r -> r.isSuccessful || r.code in 400..499 && r.code !in listOf(408,429) }
                    if(!discard) break
                    queue.remove(0)
                    synchronized(state) { if(enabled && token==generation.get()) preferences.edit().putString("queue",queue.toString()).apply() }
                } catch(_: Exception) { break } finally { active=null }
            }
        }
    }
    fun close() { generation.incrementAndGet(); active?.cancel() }
}
