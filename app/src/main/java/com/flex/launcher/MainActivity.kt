package com.flex.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val homeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        setContent {
            LauncherApp(
                prefs = prefs,
                resumeTick = resumeTick.intValue,
                homeTick = homeTick.intValue,
                onLaunch = { pkg ->
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                },
                onUsageSettings = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue = resumeTick.intValue + 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick.intValue = homeTick.intValue + 1
    }
}
