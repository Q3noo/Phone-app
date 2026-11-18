package app.notelet

import android.app.Application
import com.google.android.material.color.DynamicColors

class NoteletApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Match the phone's wallpaper colours on Android 12+.
        DynamicColors.applyToActivitiesIfAvailable(this)
        Notifications.ensureChannel(this)
    }
}
