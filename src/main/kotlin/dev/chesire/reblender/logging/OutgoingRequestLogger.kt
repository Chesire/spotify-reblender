package dev.chesire.reblender.logging

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Logs every call the app makes to Spotify (Web API, token endpoint, user info) and its result:
 * `OUT GET https://api.spotify.com/v1/me/top/tracks?time_range=short_term&limit=50 -> 200 (231 ms, 48213 bytes)`
 *
 * Never logged: request headers (the Authorization header carries the access token or client credentials),
 * request bodies (token requests carry the authorization code, PKCE verifier and refresh token) and successful
 * response bodies (token responses carry the tokens). For error responses only, a short body is logged after
 * masking anything token-like, because Spotify's error messages are what make failures diagnosable.
 */
class OutgoingRequestLogger : ClientHttpRequestInterceptor {
    private val log = LoggerFactory.getLogger("reblender.http.out")

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        val start = System.nanoTime()
        val uri = request.uri
        val target = "${uri.scheme}://${uri.authority}${LogRedaction.target(uri.rawPath ?: "", uri.rawQuery)}"
        val response = try {
            execution.execute(request, body)
        } catch (e: IOException) {
            log.warn("OUT {} {} -> failed: {} ({} ms)", request.method, target, e.javaClass.simpleName, elapsedMs(start))
            throw e
        }
        val status = response.statusCode
        if (!status.isError) {
            val size = response.headers.contentLength.takeIf { it >= 0 }?.let { ", $it bytes" } ?: ""
            log.info("OUT {} {} -> {} ({} ms{})", request.method, target, status.value(), elapsedMs(start), size)
            return response
        }
        // Buffer the (small) error body so it can be logged and still read by the caller.
        val bytes = response.body.use { it.readAllBytes() }
        log.warn(
            "OUT {} {} -> {} ({} ms): {}",
            request.method, target, status.value(), elapsedMs(start), LogRedaction.errorBody(bytes.toString(Charsets.UTF_8)),
        )
        return BufferedResponse(response, bytes)
    }

    private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000

    private class BufferedResponse(private val delegate: ClientHttpResponse, private val bytes: ByteArray) : ClientHttpResponse {
        override fun getStatusCode(): HttpStatusCode = delegate.statusCode
        override fun getStatusText(): String = delegate.statusText
        override fun getHeaders(): HttpHeaders = delegate.headers
        override fun getBody(): InputStream = ByteArrayInputStream(bytes)
        override fun close() = delegate.close()
    }
}
