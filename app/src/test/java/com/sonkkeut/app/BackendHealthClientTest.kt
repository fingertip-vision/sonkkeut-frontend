package com.sonkkeut.app

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackendHealthClientTest {
    private fun client() = BackendHealthClient(OkHttpClient.Builder()
        .callTimeout(250, TimeUnit.MILLISECONDS).readTimeout(250, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).build())

    private fun respond(response: MockResponse, expected: HealthResult) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response)
            server.start()
            assertEquals(expected, client().check(server.url("/").toString().trimEnd('/')))
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("/actuator/health", request?.path)
            assertEquals("GET", request?.method)
        }
    }

    @Test fun upIsReady() = respond(MockResponse().setBody("{\"status\":\"UP\"}"), HealthResult.Ready)
    @Test fun down503IsReachableButNotReady() = respond(MockResponse().setResponseCode(503).setBody("{\"status\":\"DOWN\"}"), HealthResult.NotReady("DOWN"))
    @Test fun unauthorizedIsHttpError() = respond(MockResponse().setResponseCode(401), HealthResult.HttpError(401))
    @Test fun malformedJsonIsNotSuccess() = respond(MockResponse().setBody("not-json"), HealthResult.InvalidResponse)
    @Test fun unknownFieldsCannotFakeReady() = respond(MockResponse().setBody("{\"error\":\"UP\"}"), HealthResult.InvalidResponse)
    @Test fun oversizedResponseIsRejected() = respond(MockResponse().setBody(" ".repeat(17000)), HealthResult.InvalidResponse)
    @Test fun redirectsAreNotFollowed() = respond(MockResponse().setResponseCode(302).setHeader("Location", "http://127.0.0.1:1/"), HealthResult.HttpError(302))
    @Test fun unresponsiveServerTimesOut() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            assertEquals(HealthResult.Timeout, client().check(server.url("/").toString().trimEnd('/')))
        }
    }
    @Test fun refusedConnectionIsFailure() = runBlocking {
        val server = MockWebServer()
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        server.shutdown()
        assertEquals(HealthResult.ConnectionFailed, client().check(url))
    }
    @Test fun cancelledRequestDoesNotReturnReady() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val request = launch { client().check(server.url("/").toString().trimEnd('/')); fail("Cancelled request returned") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
        }
    }

    @Test fun addressPolicyProtectsRelease() {
        assertEquals("https://example.com/api", BackendAddress.normalize(" https://example.com/api/ ", false))
        assertEquals("http://10.0.2.2:8080", BackendAddress.normalize("http://10.0.2.2:8080/", true))
        listOf("http://10.0.2.2:8080", "https://user:pass@example.com", "https://example.com?token=secret", "https://example.com#fragment", "ftp://example.com", "https://example.com:70000").forEach { address ->
            try { BackendAddress.normalize(address, false); fail("Accepted invalid address: $address") } catch (_: IllegalArgumentException) { }
        }
        try { BackendAddress.normalize("http://example.com", true); fail("Accepted public HTTP") } catch (_: IllegalArgumentException) { }
    }
}
