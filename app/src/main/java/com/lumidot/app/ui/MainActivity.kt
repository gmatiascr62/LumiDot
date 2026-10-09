package com.lumidot.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumidot.app.data.LedSettings
import com.lumidot.app.lumiSettings
import com.lumidot.app.ui.screens.AppsScreen
import com.lumidot.app.ui.screens.HomeScreen
import com.lumidot.app.ui.screens.LedScreen
import com.lumidot.app.ui.screens.SettingsScreen
import com.lumidot.app.ui.theme.LumiDotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { LumiDotTheme { LumiDotRoot() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    LED("LED", Icons.Filled.Star),
    APPS("Apps", Icons.AutoMirrored.Filled.List),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

@Composable
private fun LumiDotRoot() {
    val context = LocalContext.current
    val repo = context.lumiSettings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val onUpdate: ((LedSettings) -> LedSettings) -> Unit = { transform ->
        scope.launch { repo.update(transform) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val s = settings
            if (s == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(s, onUpdate)
                    Tab.LED -> LedScreen(s, onUpdate)
                    Tab.APPS -> AppsScreen(s, onUpdate)
                    Tab.SETTINGS -> SettingsScreen(s, onUpdate)
                }
            }
        }
    }
}
