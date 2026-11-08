package dev.chesire.reblender.platform.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OutgoingRequestLoggerTest {
    private val logger = LoggerFactory.getLogger("reblender.http.out") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun detach() {
        logger.detachAppender(appender)
    }

    private fun logged() = appender.list.joinToString("\n") { it.formattedMessage }

    private fun call(
        uri: String,
        response: MockClientHttpResponse,
        body: String = "",
    ) = OutgoingRequestLogger().intercept(
        MockClientHttpRequest(HttpMethod.POST, URI(uri)).apply {
            headers.setBearerAuth("BQDaccesstoken")
        },
        body.toByteArray(),
    ) { _, _ -> response }

    @Test
    fun `success logs method, url and status but no headers or bodies`() {
        val response = MockClientHttpResponse("""{"access_token":"BQDnew","refresh_token":"AQCnew"}""".toByteArray(), HttpStatus.OK)
        call("https://accounts.spotify.com/api/token", response, body = "grant_type=refresh_token&refresh_token=AQCold")

        val text = logged()
        assertContains(text, "OUT POST https://accounts.spotify.com/api/token -> 200")
        listOf("BQDaccesstoken", "BQDnew", "AQCnew", "AQCold").forEach { assertFalse(text.contains(it), "leaked $it") }
    }

    @Test
    fun `errors log a masked body and the caller can still read it`() {
        val error = """{"error":{"status":403,"message":"Insufficient client scope"},"access_token":"BQDleak"}"""
        val result =
            call(
                "https://api.spotify.com/v1/me/library?uris=spotify:playlist:x",
                MockClientHttpResponse(error.toByteArray(), HttpStatus.FORBIDDEN),
            )

        val text = logged()
        assertContains(text, "-> 403")
        assertContains(text, "Insufficient client scope")
        assertContains(text, "uris=spotify:playlist:x")
        assertFalse(text.contains("BQDleak"))
        assertEquals(error, result.body.readAllBytes().toString(Charsets.UTF_8))
    }
}
