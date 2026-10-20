package com.flex.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val pkg: String, val label: String, val icon: Bitmap)

class Palette(val name: String, val bg: Long, val card: Long, val accent: Long, val text: Long)

val Palettes = listOf(
    Palette("AMOLED", 0xFF000000, 0xFF121212, 0xFF00E5FF, 0xFFFFFFFF),
    Palette("Midnight", 0xFF0B1020, 0xFF161D33, 0xFF7C9CFF, 0xFFE8ECFF),
    Palette("Graphite", 0xFF1C1C1E, 0xFF2C2C2E, 0xFF0A84FF, 0xFFF2F2F7),
    Palette("Cyberpunk", 0xFF0D0221, 0xFF1F0A3D, 0xFFFF2A6D, 0xFFD1F7FF),
    Palette("Aurora", 0xFF071A2B, 0xFF0E2F44, 0xFF4DFFB4, 0xFFE6FFF5),
    Palette("Ocean", 0xFF06202B, 0xFF0B3A4A, 0xFF2EC4F1, 0xFFE3F6FF),
    Palette("Emerald", 0xFF04140E, 0xFF0B2A1D, 0xFF2ECC71, 0xFFE8FFF1),
    Palette("Sunset", 0xFF2B0F1A, 0xFF4A1B2C, 0xFFFF8A5B, 0xFFFFF0E8),
    Palette("Rose", 0xFF1F0B14, 0xFF3A1424, 0xFFFF5C8A, 0xFFFFE8EF),
    Palette("Warm", 0xFF2A1E14, 0xFF3E2C1C, 0xFFF5B041, 0xFFFFF4E3),
    Palette("Pastel", 0xFFF6EEF8, 0xFFFFFFFF, 0xFFB69CFF, 0xFF3B3552),
    Palette("Clean Light", 0xFFFAFAFA, 0xFFFFFFFF, 0xFF2563EB, 0xFF111827),
)

private val Hexagon = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

val IconShapes: List<Pair<String, Shape>> = listOf(
    "Circle" to CircleShape,
    "Rounded" to RoundedCornerShape(22),
    "Square" to RoundedCornerShape(4),
    "Squircle" to RoundedCornerShape(36),
    "Teardrop" to RoundedCornerShape(50, 50, 50, 10),
    "Hexagon" to Hexagon,
)

val Fonts: List<Pair<String, FontFamily>> = listOf(
    "System" to FontFamily.Default,
    "Serif" to FontFamily.Serif,
    "Mono" to FontFamily.Monospace,
    "Cursive" to FontFamily.Cursive,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("flex", Context.MODE_PRIVATE)
    var theme by mutableIntStateOf(sp.getInt("theme", 0).coerceIn(0, Palettes.lastIndex))
    var shape by mutableIntStateOf(sp.getInt("shape", 0).coerceIn(0, IconShapes.lastIndex))
    var font by mutableIntStateOf(sp.getInt("font", 0).coerceIn(0, Fonts.lastIndex))
    var cols by mutableIntStateOf(sp.getInt("cols", 4).coerceIn(3, 6))
    var recentCount by mutableIntStateOf(sp.getInt("recentCount", 8).coerceIn(4, 12))
    var showRecents by mutableStateOf(sp.getBoolean("showRecents", true))

    fun save() {
        sp.edit().putInt("theme", theme).putInt("shape", shape).putInt("font", font)
            .putInt("cols", cols).putInt("recentCount", recentCount)
            .putBoolean("showRecents", showRecents).apply()
    }

    fun resetAppearance() {
        theme = 0; shape = 0; font = 0; cols = 4
        save()
    }

    fun record(pkg: String) {
        sp.edit().putLong("h_$pkg", System.currentTimeMillis()).apply()
    }

    fun history(): List<String> =
        sp.all.entries.filter { it.key.startsWith("h_") && it.value is Long }
            .sortedByDescending { it.value as Long }
            .map { it.key.removePrefix("h_") }

    fun clearHistory() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("h_") }.forEach { e.remove(it) }
        e.apply()
    }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppInfo(
                it.activityInfo.packageName,
                it.loadLabel(pm).toString(),
                it.loadIcon(pm).toBitmap(128, 128),
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = ops.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun usageRecents(ctx: Context): List<String> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 24 * 3600 * 1000, now)
        .orEmpty()
        .filter { it.lastTimeUsed > 0 }
        .sortedByDescending { it.lastTimeUsed }
        .map { it.packageName }
        .distinct()
}
