package app.freedombrowser.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the cross-platform conformance vectors also used by the Windows C implementation. */
class BlockListVectorsTest {
    @Test fun vectors() {
        val text = File("testdata/blocklist-vectors.txt").readText()
        val list = text.substringAfter("[list]").substringBefore("[queries]")
        val blockList = BlockList.parse(list)
        assertEquals(4, blockList.size)
        var count = 0
        text.substringAfter("[queries]").lineSequence().map { it.trim() }.filter { it.startsWith("Q ") }.forEach { line ->
            val p = line.split(' ')
            val exceptions = p.getOrNull(6)?.split(',')?.toSet() ?: emptySet()
            val got = blockList.shouldBlock(p[1], p[2].takeIf { it != "-" }, p[3] == "main", p[4] == "1", exceptions)
            assertEquals(p[5] == "1", got, line)
            count++
        }
        assertTrue(count >= 15)
    }

    @Test fun idnRoundTrip() {
        assertEquals("https://xn--bcher-kva.de/", (UrlResolver.resolve("bücher.de", SearchEngine.GOOGLE) as ResolvedInput.Navigate).url)
        assertEquals("bücher.de", DisplayUrl.format("https://xn--bcher-kva.de/"))
        // Mixed Latin + Cyrillic look-alike stays punycode in the UI.
        val spoof = idnToAscii("аpple.com")!!
        assertEquals(spoof, DisplayUrl.format("https://$spoof/"))
    }
}
