package ovh.battistella.sillon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ovh.battistella.sillon.activity.ServerActivityWatch
import ovh.battistella.sillon.data.settings.SettingsStore
import ovh.battistella.sillon.data.settings.ThemePreferences
import javax.inject.Inject
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ovh.battistella.sillon.ui.navigation.SillonRoot
import ovh.battistella.sillon.ui.theme.SillonTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var activityWatch: ServerActivityWatch

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        if (savedInstanceState == null) openFrom(intent)
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle(initialValue = ThemePreferences())
            val dark = theme.isDark(isSystemInDarkTheme())
            // Light or dark can be forced on the theme page: the bar icons follow the app, not the system.
            DisposableEffect(dark) {
                val bars = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            SillonTheme(theme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SillonRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_ACTIVITY) activityWatch.requestOpen()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN = "ovh.battistella.sillon.OPEN"
        const val OPEN_ACTIVITY = "activity"
    }
}
