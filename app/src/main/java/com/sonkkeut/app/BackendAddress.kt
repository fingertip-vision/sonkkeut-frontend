package com.sonkkeut.app

import java.net.URI

object BackendAddress {
    fun normalize(raw: String, allowLocalHttp: Boolean): String {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("서버 주소 형식을 확인해 주세요.") }
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "http:// 또는 https://로 시작하는 서버 주소를 입력해 주세요." }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "주소에 로그인 정보, 쿼리 또는 #을 넣지 마세요." }
        require(uri.port == -1 || uri.port in 1..65535) { "서버 포트를 확인해 주세요." }
        val localHttp = allowLocalHttp && uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost")
        require(uri.scheme == "https" || localHttp) { "HTTPS 주소를 사용해 주세요. 개발 APK의 HTTP는 에뮬레이터 호스트와 localhost만 허용합니다." }
        return uri.toASCIIString().trimEnd('/')
    }
}
