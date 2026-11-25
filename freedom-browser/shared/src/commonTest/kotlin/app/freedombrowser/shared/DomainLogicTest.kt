package app.freedombrowser.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainLogicTest {
    @Test fun webUrlParsing() {
        val u = WebUrl.parse("https://user:pw@Example.com:8443/p/a?x=1#frag")!!
        assertEquals("https", u.scheme)
        assertEquals("example.com", u.host)
        assertEquals(8443, u.port)
        assertEquals("/p/a", u.path)
        assertEquals("x=1", u.query)
        assertEquals("frag", u.fragment)
        assertNull(WebUrl.parse("mailto:a@b.c"))
        assertNull(WebUrl.parse("https://host:99999/"))
        assertEquals("news.com", WebUrl.parse("https://www.news.com/")!!.siteKey)
    }

    @Test fun redactionRemovesSecrets() {
        assertEquals("https://example.com/login", DisplayUrl.redact("https://u:p@example.com/login?token=abc#x"))
    }

    @Test fun displayAndSecurity() {
        assertEquals("example.com", DisplayUrl.format("https://example.com/"))
        assertEquals("example.com/a b?x=1", DisplayUrl.format("https://example.com/a%20b?x=1"))
        assertEquals(SecurityLevel.SECURE, DisplayUrl.securityLevel("https://a.com"))
        assertEquals(SecurityLevel.INSECURE, DisplayUrl.securityLevel("http://a.com"))
        assertEquals(SecurityLevel.CERT_ERROR, DisplayUrl.securityLevel("https://a.com", true))
        assertEquals(SecurityLevel.NONE, DisplayUrl.securityLevel("about:blank"))
    }

    @Test fun fileNames() {
        assertEquals("report.pdf", FileNames.unique("report.pdf") { false })
        val existing = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", FileNames.unique("report.pdf") { it in existing })
        assertEquals("README (1)", FileNames.unique("README") { it == "README" })
        assertEquals("a_b_c.txt", FileNames.sanitize("a/b\\c.txt"))
        assertEquals("_CON.txt", FileNames.sanitize("CON.txt"))
        assertEquals("download", FileNames.sanitize("..."))
        assertTrue(FileNames.sanitize("x".repeat(400) + ".pdf").let { it.length <= 120 && it.endsWith(".pdf") })
        assertTrue(FileNames.isPotentiallyDangerous("setup.EXE"))
        assertTrue(FileNames.isPotentiallyDangerous("app.apk"))
        assertTrue(FileNames.isPotentiallyDangerous("file", "application/vnd.android.package-archive"))
        assertFalse(FileNames.isPotentiallyDangerous("photo.jpg", "image/jpeg"))
    }

    @Test fun contentDisposition() {
        assertEquals("rapor 2024.pdf", FileNames.guess("https://x.com/dl?id=1", "attachment; filename*=UTF-8''rapor%202024.pdf", null))
        assertEquals("a b.txt", FileNames.guess("https://x.com/", "attachment; filename=\"a b.txt\"", null))
        assertEquals("plain.zip", FileNames.guess("https://x.com/", "attachment; filename=plain.zip", null))
        assertEquals("file.pdf", FileNames.guess("https://x.com/docs/file.pdf", null, "application/pdf"))
        assertEquals("doc.pdf", FileNames.guess("https://x.com/docs/doc", null, "application/pdf"))
        assertEquals("download", FileNames.guess("https://x.com/", null, null))
    }

    @Test fun formatters() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.5 KB", Formatters.bytes(1536))
        assertEquals("1 MB", Formatters.bytes(1024 * 1024))
        assertEquals("—", Formatters.bytes(-1))
        assertEquals("2 MB/s", Formatters.speed(2L * 1024 * 1024))
        assertEquals("", Formatters.speed(0))
        assertEquals("2 min", Formatters.eta(120_000, 1000))
        assertEquals(50, Formatters.percent(5, 10))
        assertEquals(-1, Formatters.percent(5, -1))
        val meter = SpeedMeter()
        meter.update(0, 0)
        assertEquals(1000, meter.update(1000, 1000))
    }

    @Test fun historyRanges() {
        assertEquals(0, HistoryRange.ALL_TIME.cutoff(1_000_000))
        assertEquals(1_000_000 - 3_600_000L, HistoryRange.LAST_HOUR.cutoff(1_000_000))
        assertEquals(HistoryRange.LAST_7_DAYS, HistoryRange.fromId("week"))
        assertTrue(TextSearch.matches("kot docs", "Kotlin Docs", "https://kotlinlang.org"))
        assertFalse(TextSearch.matches("rust", "Kotlin Docs", "https://kotlinlang.org"))
    }

    @Test fun bookmarkCycles() {
        // 1 (folder) -> 2 (folder) -> 3 (folder)
        val parents = mapOf(1L to 0L, 2L to 1L, 3L to 2L)
        assertTrue(BookmarkTree.wouldCreateCycle(1, 3) { parents[it] })
        assertTrue(BookmarkTree.wouldCreateCycle(2, 2) { parents[it] })
        assertFalse(BookmarkTree.wouldCreateCycle(3, 1) { parents[it] })
        assertFalse(BookmarkTree.wouldCreateCycle(3, null) { parents[it] })
        assertEquals("https://example.com/", BookmarkTree.validateUrl("example.com"))
        assertNull(BookmarkTree.validateUrl("javascript:alert(1)"))
    }

    @Test fun pageErrors() {
        assertEquals(PageErrorKind.DNS, PageErrors.fromChromiumNetError(-105))
        assertEquals(PageErrorKind.OFFLINE, PageErrors.fromChromiumNetError(-106))
        assertEquals(PageErrorKind.CERTIFICATE, PageErrors.fromChromiumNetError(-202))
        assertEquals(PageErrorKind.TIMEOUT, PageErrors.fromChromiumNetError(-118))
        assertEquals(PageErrorKind.OFFLINE, PageErrors.fromAndroidWebViewError(-2, isOnline = false))
        assertEquals(PageErrorKind.DNS, PageErrors.fromAndroidWebViewError(-2, isOnline = true))
        val d = PageErrors.details(PageErrorKind.DNS, -105, "ERR_NAME_NOT_RESOLVED", "https://a.com/x?secret=1", "Android", "1.0")
        assertFalse(d.contains("secret"))
        assertTrue(d.contains("https://a.com/x"))
    }

    @Test fun siteSettings() {
        val s = SiteSettings("a.com").with(SitePermission.CAMERA, PermissionDecision.ALLOW)
        assertEquals(PermissionDecision.ALLOW, s.decision(SitePermission.CAMERA))
        assertEquals(PermissionDecision.ASK, s.decision(SitePermission.LOCATION))
        assertTrue(s.with(SitePermission.CAMERA, PermissionDecision.ASK).isDefault)
    }

    @Test fun designTokensCss() {
        val css = DesignTokens.toCss(DesignTokens.Dark)
        assertTrue(css.contains("--accent:#2ec4b6;"))
        assertTrue(css.contains("--bg:#0b0c0e;"))
    }

    @Test fun aiRequest() {
        val body = AiAssist.buildRequestBody("m", AiAssist.Task.SUMMARIZE, "T", "x".repeat(20_000), null)
        assertTrue(body.contains("\"model\":\"m\""))
        assertTrue(body.length < 14_000)
        assertEquals("hi", AiAssist.parseResponse("""{"choices":[{"message":{"role":"assistant","content":"hi"}}]}"""))
        assertNull(AiAssist.parseResponse("{}"))
        assertTrue(AiAssist.isAcceptableEndpoint("https://api.example.com/v1/chat/completions"))
        assertTrue(AiAssist.isAcceptableEndpoint("http://localhost:11434/v1/chat/completions"))
        assertFalse(AiAssist.isAcceptableEndpoint("http://api.example.com/v1"))
    }
}
