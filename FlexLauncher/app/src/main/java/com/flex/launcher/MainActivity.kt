package com.flex.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val homeTick = mutableIntStateOf(0)
    private val pkgTick = mutableIntStateOf(0)
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            pkgTick.intValue = pkgTick.intValue + 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val prefs = Prefs(this)
        val blurOk = Build.VERSION.SDK_INT >= 31 &&
            getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        val hooks = Hooks(
            launch = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) } },
            info = { pkg ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            },
            uninstall = { pkg -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) },
            usage = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            wallpaper = {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            },
            setBlur = { r ->
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching { window.setBackgroundBlurRadius(r) }
                }
            },
            blurOk = blurOk,
        )
        setContent {
            LauncherApp(prefs, resumeTick.intValue, homeTick.intValue, pkgTick.intValue, hooks)
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(pkgReceiver) }
        super.onDestroy()
    }
}
