package id.web.izs.nettools

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.ui.AppTheme
import id.web.izs.nettools.ui.AboutScreen
import id.web.izs.nettools.ui.ColorsScreen
import id.web.izs.nettools.ui.HomeScreen
import id.web.izs.nettools.ui.ManageHostsScreen
import id.web.izs.nettools.ui.NetToolsViewModel
import id.web.izs.nettools.ui.SettingsScreen
import id.web.izs.nettools.ui.baseScheme
import id.web.izs.nettools.ui.withOverrides

class MainActivity : ComponentActivity() {

    private val vm: NetToolsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val uiState by vm.state.collectAsStateWithLifecycle()
            val scheme = baseScheme(uiState.settings.theme)
                .withOverrides(uiState.settings.customColors)
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !AppTheme.isDark(uiState.settings.theme)
            }
            // App-wide long-press: 350 ms instead of the platform 500 ms
            // (top-bar quick switcher, grid tool cells, anything added later).
            val viewConfig = LocalViewConfiguration.current
            CompositionLocalProvider(
                LocalViewConfiguration provides object : ViewConfiguration by viewConfig {
                    override val longPressTimeoutMillis: Long = 350L
                }
            ) {
            MaterialTheme(colorScheme = scheme) {
                // Don't draw real content until DataStore has delivered the saved
                // settings — otherwise the first frames flash the default theme.
                if (!uiState.settingsLoaded) {
                    Box(modifier = Modifier.fillMaxSize().background(scheme.background))
                } else {
                var screen by remember { mutableStateOf("home") }
                var lastBack by remember { mutableLongStateOf(0L) }
                BackHandler {
                    if (screen != "home") {
                        screen = "home"
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastBack < 2000) {
                            (context as? Activity)?.finish()
                        } else {
                            lastBack = now
                            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                when (screen) {
                    "settings" -> SettingsScreen(
                        vm,
                        onBack = { screen = "home" },
                        onOpenColors = { screen = "colors" },
                        onOpenAbout = { screen = "about" }
                    )
                    "about" -> AboutScreen(onBack = { screen = "settings" })
                    "colors" -> ColorsScreen(vm, onBack = { screen = "settings" })
                    "hosts" -> ManageHostsScreen(vm, onBack = { screen = "home" }) { host ->
                        vm.pickTarget(host)
                        screen = "home"
                    }
                    else -> HomeScreen(vm, onOpenSettings = { screen = "settings" }, onOpenHosts = { screen = "hosts" })
                }
                }
            }
            }
        }
    }
}
