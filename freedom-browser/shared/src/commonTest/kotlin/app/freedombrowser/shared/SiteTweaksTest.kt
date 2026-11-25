package app.freedombrowser.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteTweaksTest {
    @Test fun detectsDuckDuckGoResultPages() {
        assertTrue(SiteTweaks.isDuckDuckGoResults("https://duckduckgo.com/?q=GitHub&ia=web"))
        assertTrue(SiteTweaks.isDuckDuckGoResults("https://html.duckduckgo.com/html/?q=test"))
        assertFalse(SiteTweaks.isDuckDuckGoResults("https://duckduckgo.com/"))
        assertFalse(SiteTweaks.isDuckDuckGoResults("https://duckduckgo.com/?q="))
        assertFalse(SiteTweaks.isDuckDuckGoResults("http://duckduckgo.com/?q=x"))
        assertFalse(SiteTweaks.isDuckDuckGoResults("https://duckduckgo.com.evil.org/?q=x"))
        assertFalse(SiteTweaks.isDuckDuckGoResults("https://www.google.com/search?q=x"))
    }

    @Test fun scriptIsIdempotentAndScoped() {
        assertTrue(SiteTweaks.DUCKDUCKGO_HIDE_HEADER_JS.contains("__lumoDdgHeader"))
        assertTrue(SiteTweaks.DUCKDUCKGO_HIDE_HEADER_JS.contains("isSerp"))
    }
}
