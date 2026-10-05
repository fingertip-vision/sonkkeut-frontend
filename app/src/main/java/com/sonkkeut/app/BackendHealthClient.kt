package com.sonkkeut.app

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed interface HealthResult {
    data object Ready : HealthResult
    data class NotReady(val status: String) : HealthResult
    data class HttpError(val code: Int) : HealthResult
    data object InvalidResponse : HealthResult
    data object Timeout : HealthResult
    data object ConnectionFailed : HealthResult
}

class BackendHealthClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
    .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()) {
    suspend fun check(baseUrl: String): HealthResult = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("$baseUrl/actuator/health").header("Accept", "application/json").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(if (e is InterruptedIOException) HealthResult.Timeout else HealthResult.ConnectionFailed)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    try {
                        val body = it.body ?: return@use HealthResult.InvalidResponse
                        val text = body.charStream().use { reader ->
                            val buffer = CharArray(16385)
                            var size = 0
                            while (size < buffer.size) {
                                val count = reader.read(buffer, size, buffer.size - size)
                                if (count < 0) break
                                size += count
                            }
                            if (size > 16384) null else String(buffer, 0, size)
                        }
                        if (text == null) return@use HealthResult.InvalidResponse
                        val status = try { JSONObject(text).opt("status") as? String } catch (_: Exception) { null }
                        when {
                            it.code == 503 && status in setOf("DOWN", "OUT_OF_SERVICE") -> HealthResult.NotReady(requireNotNull(status))
                            !it.isSuccessful -> HealthResult.HttpError(it.code)
                            status == "UP" -> HealthResult.Ready
                            status in setOf("DOWN", "OUT_OF_SERVICE", "UNKNOWN") -> HealthResult.NotReady(requireNotNull(status))
                            else -> HealthResult.InvalidResponse
                        }
                    } catch (e: IOException) {
                        if (e is InterruptedIOException) HealthResult.Timeout else HealthResult.ConnectionFailed
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }
}
