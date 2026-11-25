package app.freedombrowser.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UrlResolverTest {
    private val ddg = SearchEngine.DUCKDUCKGO
    private fun nav(input: String) = (UrlResolver.resolve(input, ddg) as ResolvedInput.Navigate).url
    private fun isSearch(input: String) = assertIs<ResolvedInput.Search>(UrlResolver.resolve(input, ddg), input)

    @Test fun fullUrlsAreKept() {
        assertEquals("https://example.com/path?q=1", nav("https://example.com/path?q=1"))
        assertEquals("http://example.com/", nav("http://example.com"))
        assertEquals("https://example.com/", nav("  HTTPS://Example.COM  "))
    }

    @Test fun bareDomainsGetHttps() {
        assertEquals("https://example.com/", nav("example.com"))
        assertEquals("https://sub.example.co.uk/a/b", nav("sub.example.co.uk/a/b"))
        assertEquals("https://example.com:8080/", nav("example.com:8080"))
    }

    @Test fun localAddressesUseHttp() {
        assertEquals("http://localhost:3000/", nav("localhost:3000"))
        assertEquals("http://192.168.1.1/", nav("192.168.1.1"))
        assertEquals("http://[::1]:8080/", nav("[::1]:8080"))
    }

    @Test fun searchTerms() {
        isSearch("hello world")
        isSearch("kotlin")
        isSearch("what is 2+2")
        isSearch("example.com hello")
        isSearch("999.1.1.1")
        isSearch("foo.123")
        val r = UrlResolver.resolve("çay nasıl demlenir", SearchEngine.GOOGLE) as ResolvedInput.Search
        assertEquals("https://www.google.com/search?q=%C3%A7ay%20nas%C4%B1l%20demlenir", r.url)
    }

    @Test fun dangerousSchemesAreNeverNavigated() {
        isSearch("javascript:alert(1)")
        isSearch("data:text/html,<script>alert(1)</script>")
        isSearch("file:///etc/passwd")
        isSearch("intent://scan/#Intent;scheme=zxing;end")
        isSearch("ftp://example.com")
    }

    @Test fun userInfoTrickIsSearch() {
        isSearch("google.com@evil.com")
    }

    @Test fun unsafeCharactersAreEncoded() {
        assertEquals("https://example.com/a%20b", UrlResolver.normalizeAbsolute("https://example.com/a b"))
        assertEquals("https://example.com/%C3%BC", nav("https://example.com/ü"))
    }

    @Test fun emptyInput() {
        assertEquals(ResolvedInput.Empty, UrlResolver.resolve("   ", ddg))
    }

    @Test fun everyEngineBuildsEncodedUrl() {
        for (e in SearchEngine.entries) {
            val url = e.searchUrl("a&b=c")
            kotlin.test.assertTrue(url.endsWith("a%26b%3Dc"), url)
        }
        assertEquals(SearchEngine.DEFAULT, SearchEngine.fromId("nope"))
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromId("brave"))
    }
}
