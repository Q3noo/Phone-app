package app.notelet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Gives every screen the app's own day/night colour palette (see NoteletPalette in themes.xml). */
abstract class NoteletActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(R.style.NoteletPalette, true)
        super.onCreate(savedInstanceState)
    }
}
