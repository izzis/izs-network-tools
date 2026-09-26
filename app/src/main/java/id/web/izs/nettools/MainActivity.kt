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
import id.web.izs.nettools.ui.EditUiScreen
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
                // Back stack: top-bar arrows and the Android back button must do
                // the same thing (pop one level), so Edit UI / Colors entered from
                // Settings return to Settings (General tab), from the Home FAB
                // they return to Home.
                var stack by remember { mutableStateOf(listOf("home")) }
                // Settings remembers its last tab: Edit UI / Custom Colors live in
                // General, so coming back must not reset the pager to the first tab.
                var settingsTab by remember { mutableStateOf(0) }
                var lastBack by remember { mutableLongStateOf(0L) }
                fun back() {
                    if (stack.size > 1) stack = stack.dropLast(1)
                }
                fun push(to: String) {
                    stack = stack + to
                }
                BackHandler {
                    if (stack.size > 1) {
                        back()
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
                when (stack.last()) {
                    "settings" -> SettingsScreen(
                        vm,
                        onBack = { back() },
                        onOpenColors = { push("colors") },
                        onOpenAbout = { push("about") },
                        onOpenEditUi = { push("editui") },
                        initialTab = settingsTab,
                        onTabChange = { settingsTab = it }
                    )
                    "about" -> AboutScreen(onBack = { back() })
                    "colors" -> ColorsScreen(vm, onBack = { back() })
                    "editui" -> EditUiScreen(vm, onBack = { back() }, onView = { push("home") })
                    "hosts" -> ManageHostsScreen(vm, onBack = { back() }) { host ->
                        vm.pickTarget(host)
                        back()
                    }
                    else -> HomeScreen(vm, onOpenSettings = { push("settings") }, onOpenHosts = { push("hosts") }, onOpenEditUi = { push("editui") })
                }
                }
            }
            }
        }
    }
}
