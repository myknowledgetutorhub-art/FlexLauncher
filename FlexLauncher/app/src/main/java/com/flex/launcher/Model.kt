package com.flex.launcher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap, val adaptive: Boolean)

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

private fun tf(name: String) = FontFamily(Typeface.create(name, Typeface.NORMAL))

val Fonts: List<Pair<String, FontFamily>> = listOf(
    "System" to FontFamily.Default,
    "Light" to tf("sans-serif-light"),
    "Medium" to tf("sans-serif-medium"),
    "Condensed" to tf("sans-serif-condensed"),
    "Black" to tf("sans-serif-black"),
    "Serif" to FontFamily.Serif,
    "Mono" to FontFamily.Monospace,
    "Casual" to tf("casual"),
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
    var dim by mutableFloatStateOf(sp.getFloat("dim", 0.3f).coerceIn(0f, 0.8f))
    var blur by mutableStateOf(sp.getBoolean("blur", true))
    var wallpaper by mutableIntStateOf(sp.getInt("wallpaper", 0).coerceIn(0, 1))
    var iconSize by mutableIntStateOf(sp.getInt("iconSize", 56).coerceIn(44, 72))
    var seeded by mutableStateOf(sp.getBoolean("seeded", false))
    var dock by mutableStateOf(sp.getString("dock", "").orEmpty().split(",").filter { it.isNotBlank() })
    var home by mutableStateOf(sp.getString("home", "").orEmpty().split(",").filter { it.isNotBlank() })
    var hidden by mutableStateOf(sp.getString("hidden", "").orEmpty().split(",").filter { it.isNotBlank() })
    var accentHex by mutableStateOf(sp.getString("accentHex", "").orEmpty())
    var bgHex by mutableStateOf(sp.getString("bgHex", "").orEmpty())
    var motion by mutableIntStateOf(sp.getInt("motion", 2).coerceIn(0, 3))
    var speed by mutableFloatStateOf(sp.getFloat("speed", 1f).coerceIn(0.5f, 2f))

    fun save() {
        sp.edit().putInt("theme", theme).putInt("shape", shape).putInt("font", font)
            .putInt("cols", cols).putInt("recentCount", recentCount)
            .putBoolean("showRecents", showRecents).putFloat("dim", dim).putBoolean("blur", blur)
            .putInt("wallpaper", wallpaper).putInt("iconSize", iconSize).putBoolean("seeded", seeded)
            .putString("dock", dock.joinToString(",")).putString("home", home.joinToString(",")).putString("hidden", hidden.joinToString(","))
            .putString("accentHex", accentHex).putString("bgHex", bgHex).putInt("motion", motion)
            .putFloat("speed", speed).apply()
    }

    fun resetAppearance() {
        theme = 0; shape = 0; font = 0; cols = 4; dim = 0.3f; wallpaper = 0; iconSize = 56; blur = true; accentHex = ""; bgHex = ""; motion = 2; speed = 1f
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
            val (bmp, adaptive) = renderIcon(it.loadIcon(pm), 160)
            AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), bmp.asImageBitmap(), adaptive)
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

fun renderIcon(d: Drawable, size: Int): Pair<Bitmap, Boolean> {
    if (d is AdaptiveIconDrawable) {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val o = size / 4
        for (layer in listOfNotNull(d.background, d.foreground)) {
            layer.setBounds(-o, -o, size + o, size + o)
            layer.draw(c)
        }
        return bmp to true
    }
    return d.toBitmap(size, size) to false
}

val Popular = listOf(
    "com.android.chrome", "com.google.android.apps.maps", "com.google.android.youtube",
    "com.google.android.gm", "com.google.android.apps.photos", "com.android.vending",
    "com.whatsapp", "com.instagram.android", "com.google.android.calendar",
    "com.google.android.deskclock", "com.android.settings",
)

fun defaultDock(ctx: Context): List<String> {
    val pm = ctx.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        Intent("android.media.action.IMAGE_CAPTURE"),
    )
    return intents.mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .filter { it != "android" }.distinct()
}

fun seed(ctx: Context, prefs: Prefs, apps: List<AppInfo>) {
    val have = apps.map { it.pkg }.toSet()
    val dock = defaultDock(ctx).filter { it in have }.take(4)
    prefs.dock = dock
    prefs.home = (Popular.filter { it in have && it !in dock } + apps.map { it.pkg }.filter { it !in dock })
        .distinct().take(8)
    prefs.seeded = true
    prefs.save()
}

fun parseHex(s: String): Long? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return 0xFF000000L or v
}

fun effectivePalette(prefs: Prefs): Palette {
    val b = Palettes[prefs.theme]
    val a = parseHex(prefs.accentHex)
    val g = parseHex(prefs.bgHex)
    if (a == null && g == null) return b
    if (g == null) return Palette(b.name, b.bg, b.card, a ?: b.accent, b.text)
    val r = ((g shr 16) and 0xFF).toInt()
    val gr = ((g shr 8) and 0xFF).toInt()
    val bl = (g and 0xFF).toInt()
    val light = (0.299 * r + 0.587 * gr + 0.114 * bl) / 255.0 > 0.5
    return Palette(
        b.name, g, if (light) 0xFFFFFFFF else 0xFF1A1A1A, a ?: b.accent,
        if (light) 0xFF111827 else 0xFFFFFFFF,
    )
}
