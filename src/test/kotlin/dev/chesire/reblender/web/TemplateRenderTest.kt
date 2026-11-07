package dev.chesire.reblender.web

import dev.chesire.reblender.blend.SharedMode
import dev.chesire.reblender.blend.TimeRange
import dev.chesire.reblender.playlists.Blend
import dev.chesire.reblender.playlists.BlendTrack
import dev.chesire.reblender.playlists.Breakdown
import dev.chesire.reblender.users.AppUser
import org.thymeleaf.context.Context
import org.thymeleaf.context.IExpressionContext
import org.thymeleaf.linkbuilder.StandardLinkBuilder
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.File
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Renders the real templates with sample data (no Spring context or HTTP needed), checks the output,
 * and writes the HTML to build/previews/ so CI can show what the pages look like.
 */
class TemplateRenderTest {

    private val engine = SpringTemplateEngine().apply {
        setTemplateResolver(
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
            },
        )
        // Outside a web request, render context-relative links like @{/css/app.css} as-is.
        setLinkBuilder(object : StandardLinkBuilder() {
            override fun computeContextPath(context: IExpressionContext?, base: String?, parameters: MutableMap<String, Any>?): String = ""
        })
    }

    private val blend = Blend(
        playlistId = "p1",
        playlistName = "Chesire, Sam & Alex Morgan - Half-year favourites",
        playlistUrl = "https://open.spotify.com/playlist/p1",
        ownerId = "chesire",
        memberIds = listOf("chesire", "sam", "alex"),
        timeRange = TimeRange.HALF_YEAR,
        size = 12,
        sharedMode = SharedMode.CAPPED,
        trackCount = 12,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val users = listOf(
        AppUser("chesire", "Chesire", SAMPLE_AVATAR),
        AppUser("sam", "Sam"),
        AppUser("alex", "Alex Morgan"),
    )

    private fun track(n: Int, title: String, artist: String, vararg who: String) =
        BlendTrack("t$n", "spotify:track:t$n", title, listOf(artist), who.toList(), imageUrl = if (n == 4) null else cover(n))

    /** A small generated "album cover" so previews show artwork without network access (track 4 shows the placeholder). */
    private fun cover(n: Int): String {
        val hues = listOf(12, 200, 280, 40, 150, 330, 90, 220, 0, 260, 180, 60)
        val h = hues[(n - 1) % hues.size]
        return "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>" +
            "<rect width='64' height='64' fill='hsl($h,55%25,45%25)'/>" +
            "<circle cx='${16 + (n * 7) % 32}' cy='${20 + (n * 11) % 24}' r='${10 + n % 8}' fill='hsl(${(h + 40) % 360},70%25,70%25)' fill-opacity='0.8'/>" +
            "<rect x='0' y='44' width='64' height='20' fill='%23000' fill-opacity='0.25'/></svg>"
    }

    private val tracks = listOf(
        track(1, "Midnight Static", "The Lanterns", "chesire", "sam", "alex"),
        track(2, "Paper Boats", "Juniper Coast", "chesire"),
        track(3, "Low Orbit", "Ana Reyes", "sam"),
        track(4, "Glasshouse", "Northbound", "alex"),
        track(5, "Signal Fires", "The Lanterns", "chesire", "sam"),
        track(6, "Slow Bloom", "Mira Vale", "chesire"),
        track(7, "Copper Sky", "Dune Theory", "sam"),
        track(8, "Ever After Hours", "Kit Halloway", "alex"),
        track(9, "Tidal", "Juniper Coast", "sam", "alex"),
        track(10, "Second Wind", "Pale Harbour", "chesire"),
        track(11, "Kites", "Ana Reyes", "sam"),
        track(12, "A Long Way Round", "Northbound feat. Mira Vale", "alex"),
    )

    @Test
    fun `breakdown page renders members, balance and songs`() {
        val html = render("breakdown", mapOf("b" to Breakdown.of(blend, users, tracks), "updatedLabel" to "2026-11-07 18:30"))
        assertContains(html, "Chesire, Sam &amp; Alex Morgan - Half-year favourites")
        assertContains(html, "Midnight Static")
        assertContains(html, "A Long Way Round")
        assertContains(html, "(you)")
        assertContains(html, "data-name=\"Alex Morgan\"")
        assertContains(html, ">AM<") // initials for a member without a picture
        assertContains(html, "/playlists/p1/update")
        assertContains(html, "class=\"cover\"")
        assertContains(html, "cover placeholder") // track without artwork
        assertContains(html, "https://open.spotify.com/track/t1")
        assertContains(html, "From Chesire, Sam, Alex Morgan")
        assertFalse(html.contains("th:"), "unprocessed Thymeleaf attributes left in the output")
        preview("breakdown", html)
    }

    @Test
    fun `breakdown page with no recorded songs shows the empty state`() {
        val html = render("breakdown", mapOf("b" to Breakdown.of(blend, users, emptyList()), "updatedLabel" to "2026-11-07 18:30"))
        assertContains(html, "No breakdown recorded yet")
    }

    @Test
    fun `home page links playlist names to the breakdown`() {
        val row = HomeController.PlaylistRow(blend, "with Sam and Alex Morgan", "2026-11-07 18:30")
        val html = render(
            "index",
            mapOf(
                "loggedIn" to true,
                "me" to "Chesire",
                "partners" to users.drop(1),
                "maxPartners" to 4,
                "playlists" to listOf(row),
                "timeRanges" to TimeRange.entries,
                "sharedModes" to SharedMode.entries,
                "defaultSize" to 40,
                "tracksPerPerson" to 50,
                "allowBlending" to true,
            ),
        )
        assertContains(html, "href=\"/playlists/p1\"")
        assertContains(html, "Show breakdown")
        assertContains(html, "<dialog")
        assertContains(html, "/account/blending")
        assertContains(html, "data-confirm=")
        assertFalse(html.contains("onsubmit="), "inline handlers would be blocked by the CSP")
        assertFalse(html.contains("th:"), "unprocessed Thymeleaf attributes left in the output")
        preview("index", html)
    }

    @Test
    fun `signed-out home page offers Spotify login`() {
        val html = render("index", mapOf("loggedIn" to false))
        assertContains(html, "/oauth2/authorization/spotify")
        assertFalse(html.contains("session ended"))
        preview("signin", html)
    }

    @Test
    fun `sign-in page explains an ended session`() {
        val html = render("index", mapOf("loggedIn" to false, "expired" to true))
        assertContains(html, "session ended")
    }

    private fun render(template: String, variables: Map<String, Any>): String =
        engine.process(template, Context().apply { setVariables(variables) })

    /** Writes the page next to the stylesheet so it can be opened directly, plus a base64 copy for CI logs. */
    private fun preview(name: String, html: String) {
        val dir = File("build/previews").apply { mkdirs() }
        val css = File("src/main/resources/static/css/app.css").readText()
        val js = File("src/main/resources/static/js/app.js").readText()
        val standalone = html
            .replace("<link rel=\"stylesheet\" href=\"/css/app.css\">", "<style>\n$css\n</style>")
            .replace("<script src=\"/js/app.js\"></script>", "<script>\n$js\n</script>")
        File(dir, "$name.html").writeText(standalone)
        File(dir, "$name.b64").writeText(Base64.getEncoder().encodeToString(standalone.toByteArray()))
    }

    private companion object {
        /** A tiny inline image so the preview shows a "real" profile picture without network access. */
        const val SAMPLE_AVATAR =
            "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'>" +
                "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='%23f6d365'/>" +
                "<stop offset='1' stop-color='%23fda085'/></linearGradient></defs><rect width='64' height='64' fill='url(%23g)'/>" +
                "<circle cx='32' cy='26' r='12' fill='%23fff8'/><rect x='14' y='42' width='36' height='22' rx='11' fill='%23fff8'/></svg>"
    }
}
