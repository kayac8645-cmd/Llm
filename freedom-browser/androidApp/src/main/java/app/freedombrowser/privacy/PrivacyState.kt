package app.freedombrowser.privacy

import app.freedombrowser.shared.BlockList

/**
 * Snapshot of privacy settings read from WebView network threads. Fields are replaced
 * atomically (immutable values behind volatile references).
 */
class PrivacyState(private val blockLists: BlockListRepository) {
    val blockList: BlockList get() = blockLists.current
    @Volatile var adBlockEnabled: Boolean = true
    @Volatile var adBlockExceptions: Set<String> = emptySet()
}
