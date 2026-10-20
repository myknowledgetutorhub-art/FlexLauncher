package com.flex.launcher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Screen { HOME, DRAWER, SETTINGS }

@Composable
fun LauncherApp(
    prefs: Prefs,
    resumeTick: Int,
    homeTick: Int,
    onLaunch: (String) -> Unit,
    onUsageSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var usage by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var historyTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
        usage = hasUsageAccess(ctx)
    }
    LaunchedEffect(homeTick) { if (homeTick > 0) screen = Screen.HOME }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    val p = Palettes[prefs.theme]
    val font = Fonts[prefs.font].second
    val shape = IconShapes[prefs.shape].second
    val launch: (String) -> Unit = { pkg ->
        prefs.record(pkg)
        onLaunch(pkg)
        screen = Screen.HOME
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val light = Color(p.bg).luminance() > 0.5f
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    Box(Modifier.fillMaxSize().background(Color(p.bg))) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                apps, usage, historyTick, prefs, p, font, shape, launch,
                onDrawer = { screen = Screen.DRAWER },
                onSettings = { screen = Screen.SETTINGS },
            )
            Screen.DRAWER -> DrawerScreen(
                apps, prefs, p, font, shape, launch,
                onClose = { screen = Screen.HOME },
            )
            Screen.SETTINGS -> SettingsScreen(
                prefs, usage, p, font,
                onUsage = onUsageSettings,
                onClear = { prefs.clearHistory(); historyTick++ },
                onClose = { screen = Screen.HOME },
            )
        }
    }
}

@Composable
fun HomeScreen(
    apps: List<AppInfo>, usage: Boolean, historyTick: Int, prefs: Prefs,
    p: Palette, font: FontFamily, shape: Shape,
    onLaunch: (String) -> Unit, onDrawer: () -> Unit, onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(10_000)
        }
    }
    val recents = remember(apps, usage, historyTick, prefs.recentCount) {
        val byPkg = apps.associateBy { it.pkg }
        ((if (usage) usageRecents(ctx) else emptyList()) + prefs.history())
            .distinct().mapNotNull { byPkg[it] }.take(prefs.recentCount)
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dy -> if (dy < -25f) onDrawer() }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color(p.text), fontFamily = font, fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    color = Color(p.accent), fontFamily = font, fontSize = 16.sp,
                )
            }
            Pill("Settings", p.card, p.text, onSettings)
        }
        Column(Modifier.weight(1f).padding(top = 28.dp)) {
            if (prefs.showRecents) {
                Text(
                    "Recent", color = Color(p.accent), fontFamily = font,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (recents.isEmpty()) {
                    Text(
                        "Open an app and it will appear here. Grant Usage Access in Settings for system-wide recents.",
                        color = Color(p.text).copy(alpha = 0.6f), fontFamily = font, fontSize = 13.sp,
                    )
                } else {
                    LazyVerticalGrid(GridCells.Fixed(prefs.cols), Modifier.fillMaxWidth()) {
                        items(recents, key = { it.pkg }) { app ->
                            AppTile(app, shape, font, p) { onLaunch(app.pkg) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
            Pill("All apps  (swipe up)", p.accent, p.bg, onDrawer)
        }
    }
}

@Composable
fun DrawerScreen(
    apps: List<AppInfo>, prefs: Prefs, p: Palette, font: FontFamily, shape: Shape,
    onLaunch: (String) -> Unit, onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) {
        if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color(p.card))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(p.text), fontSize = 16.sp, fontFamily = font),
                    cursorBrush = SolidColor(Color(p.accent)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) {
                                Text(
                                    "Search apps", color = Color(p.text).copy(alpha = 0.5f),
                                    fontFamily = font, fontSize = 16.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            Pill("Close", p.card, p.text, onClose)
        }
        LazyVerticalGrid(GridCells.Fixed(prefs.cols), Modifier.fillMaxSize()) {
            items(list, key = { it.pkg }) { app ->
                AppTile(app, shape, font, p) { onLaunch(app.pkg) }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs, usage: Boolean, p: Palette, font: FontFamily,
    onUsage: () -> Unit, onClear: () -> Unit, onClose: () -> Unit,
) {
    val txt = Color(p.text)
    val accent = Color(p.accent)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Settings", color = txt, fontFamily = font, fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Pill("Done", p.accent, p.bg, onClose)
        }
        SectionLabel("Theme", p, font)
        ChipRow(Palettes.map { it.name }, prefs.theme, p, font) { prefs.theme = it; prefs.save() }
        SectionLabel("Icon shape", p, font)
        ChipRow(IconShapes.map { it.first }, prefs.shape, p, font) { prefs.shape = it; prefs.save() }
        SectionLabel("Font", p, font)
        ChipRow(Fonts.map { it.first }, prefs.font, p, font) { prefs.font = it; prefs.save() }

        SectionLabel("Columns: ${prefs.cols}", p, font)
        Slider(
            value = prefs.cols.toFloat(),
            onValueChange = { prefs.cols = it.roundToInt() },
            valueRange = 3f..6f,
            steps = 2,
            onValueChangeFinished = { prefs.save() },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Show recent apps first", color = txt, fontFamily = font,
                fontSize = 16.sp, modifier = Modifier.weight(1f),
            )
            Switch(
                checked = prefs.showRecents,
                onCheckedChange = { prefs.showRecents = it; prefs.save() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(p.bg), checkedTrackColor = accent,
                ),
            )
        }
        SectionLabel("Recent apps shown: ${prefs.recentCount}", p, font)
        Slider(
            value = prefs.recentCount.toFloat(),
            onValueChange = { prefs.recentCount = it.roundToInt() },
            valueRange = 4f..12f,
            steps = 7,
            onValueChangeFinished = { prefs.save() },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )

        SectionLabel("Usage access", p, font)
        Text(
            if (usage) "Granted. Recents use system-wide app usage."
            else "Not granted. Recents only count apps opened from this launcher.",
            color = txt.copy(alpha = 0.7f), fontFamily = font, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Pill(if (usage) "Open Usage Access settings" else "Grant Usage Access", p.card, p.text, onUsage)
        Spacer(Modifier.height(10.dp))
        Pill("Clear recents", p.card, p.text, onClear)
        Spacer(Modifier.height(10.dp))
        Pill("Reset appearance", p.card, p.text) { prefs.resetAppearance() }
    }
}

@Composable
fun AppTile(app: AppInfo, shape: Shape, font: FontFamily, p: Palette, onClick: () -> Unit) {
    val image = remember(app) { app.icon.asImageBitmap() }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(image, null, Modifier.size(54.dp).clip(shape))
        Text(
            app.label, color = Color(p.text), fontFamily = font, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, p: Palette, font: FontFamily) {
    Text(
        text, color = Color(p.accent), fontFamily = font, fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun ChipRow(items: List<String>, selected: Int, p: Palette, font: FontFamily, onSelect: (Int) -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items) { i, name ->
            val on = i == selected
            Text(
                name, color = Color(if (on) p.bg else p.text), fontFamily = font, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(Color(if (on) p.accent else p.card))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun Pill(text: String, bg: Long, fg: Long, onClick: () -> Unit) {
    Text(
        text, color = Color(fg), fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(bg))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
    )
}
