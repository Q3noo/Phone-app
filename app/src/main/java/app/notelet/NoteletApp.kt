package app.notelet

import android.app.Application
import android.content.res.Configuration

class NoteletApp : Application() {

    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun onCreate() {
        super.onCreate()
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        Notifications.ensureChannel(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Light/dark switch: redraw the widget straight away.
        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMode != nightMode) {
            nightMode = newNightMode
            NoteWidgetProvider.refreshAll(this)
        }
    }
}
