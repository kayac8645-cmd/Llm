package app.freedombrowser

import android.app.Application
import app.freedombrowser.util.Diagnostics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FreedomApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        container = AppContainer(this)
        container.scope.launch {
            Diagnostics.enabled = container.settings.settings.first().diagnosticsLogging
            container.blockLists.load()
            container.downloads.recoverAfterRestart()
        }
    }
}
