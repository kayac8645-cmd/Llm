package app.freedombrowser.ui

import app.freedombrowser.data.DownloadEntity

/** Platform actions the Compose UI delegates to the Activity. */
interface PageActions {
    fun share(url: String, title: String)
    fun copy(text: String)
    fun print(tabId: Long, title: String)
    fun screenshot(tabId: Long)
    fun openExternal(url: String)
    fun clearEngineData(cookies: Boolean, cache: Boolean)
    fun openDownload(entity: DownloadEntity)
    fun shareDownload(entity: DownloadEntity)
    fun pickDownloadFolder()
    fun appVersion(): String
    fun engineVersion(): String
}
