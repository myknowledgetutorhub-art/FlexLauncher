package com.flex.launcher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Screen { HOME, DRAWER, SETTINGS }

class Hooks(
    val launch: (String) -> Unit, val info: (String) -> Unit, val uninstall: (String) -> Unit,
    val usage: () -> Unit, val wallpaper: () -> Unit, val setBlur: (Int) -> Unit, val blurOk: Boolean,
)

class SheetSpec(val title: String, val icon: ImageBitmap?, val items: List<Pair<String, () -> Unit>>)

private fun item(label: String, action: () -> Unit): Pair<String, () -> Unit> = label to action

fun motionSpec(prefs: Prefs): AnimationSpec<Float> = when (prefs.motion) {
    0 -> snap()
    1 -> tween((450 / prefs.speed).toInt())
    3 -> tween((200 / prefs.speed).toInt())
    else -> spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow * prefs.speed)
}

fun Modifier.glass(radius: Dp, tint: Color): Modifier {
    val s = RoundedCornerShape(radius)
    return this.clip(s).background(tint).border(1.dp, Color.White.copy(alpha = 0.14f), s)
}

@Composable
fun LauncherApp(prefs: Prefs, resumeTick: Int, homeTick: Int, pkgTick: Int, hooks: Hooks) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var recentPkgs by remember { mutableStateOf(emptyList<String>()) }
    var usage by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var sheet by remember { mutableStateOf<SheetSpec?>(null) }
    var historyTick by remember { mutableIntStateOf(0) }
    var focusSearch by remember { mutableStateOf(false) }
    var drawerVisible by remember { mutableStateOf(false) }
    val drawerP = remember { Animatable(0f) }
    val open = screen == Screen.DRAWER

    LaunchedEffect(pkgTick) {
        val list = withContext(Dispatchers.Default) { loadApps(ctx) }
        apps = list
        if (!prefs.seeded && list.isNotEmpty()) seed(ctx, prefs, list)
    }
    LaunchedEffect(resumeTick) {
        val (u, r) = withContext(Dispatchers.Default) {
            val granted = hasUsageAccess(ctx)
            granted to (if (granted) usageRecents(ctx) else emptyList())
        }
        usage = u
        recentPkgs = r
    }
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            screen = Screen.HOME
            sheet = null
        }
    }
    LaunchedEffect(open) {
        if (open) drawerVisible = true
        drawerP.animateTo(if (open) 1f else 0f, motionSpec(prefs))
        if (!open) {
            drawerVisible = false
            focusSearch = false
        }
    }
    LaunchedEffect(prefs.blur) {
        snapshotFlow { drawerP.value }.collect { v ->
            hooks.setBlur(if (prefs.blur && hooks.blurOk) (v * 7).roundToInt() * 9 else 0)
        }
    }
    BackHandler(enabled = sheet != null || screen != Screen.HOME) {
        if (sheet != null) sheet = null else screen = Screen.HOME
    }

    val p = effectivePalette(prefs)
    val font = Fonts[prefs.font].second
    val shape = IconShapes[prefs.shape].second
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    val visible = remember(apps, prefs.hidden) { apps.filter { it.pkg !in prefs.hidden } }
    val recents = remember(byPkg, recentPkgs, historyTick, resumeTick, prefs.recentCount, prefs.hidden) {
        (recentPkgs + prefs.history()).distinct().mapNotNull { byPkg[it] }
            .filter { it.pkg !in prefs.hidden }.take(prefs.recentCount)
    }
    val launch: (String) -> Unit = { pkg ->
        prefs.record(pkg)
        hooks.launch(pkg)
        screen = Screen.HOME
    }
    val onLong: (AppInfo) -> Unit = { app ->
        val inHome = app.pkg in prefs.home
        val inDock = app.pkg in prefs.dock
        sheet = SheetSpec(
            app.label, app.icon,
            listOf(
                item(if (inHome) "Remove from home" else "Add to home") {
                    prefs.home = if (inHome) prefs.home - app.pkg else prefs.home + app.pkg
                    prefs.save()
                },
                item(if (inDock) "Remove from dock" else "Add to dock") {
                    if (inDock) prefs.dock = prefs.dock - app.pkg
                    else if (prefs.dock.size < 4) prefs.dock = prefs.dock + app.pkg
                    prefs.save()
                },
                item("Hide from drawer") {
                    prefs.hidden = prefs.hidden + app.pkg
                    prefs.home = prefs.home - app.pkg
                    prefs.dock = prefs.dock - app.pkg
                    prefs.save()
                },
                item("App info") { hooks.info(app.pkg) },
                item("Uninstall") { hooks.uninstall(app.pkg) },
            ),
        )
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val light = Color(p.bg).luminance() > 0.5f
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    Box(Modifier.fillMaxSize()) {
        val base = if (prefs.wallpaper == 1) {
            Modifier.background(
                Brush.verticalGradient(listOf(Color(p.bg), Color(p.accent).copy(alpha = 0.5f), Color(p.bg))),
            )
        } else {
            Modifier.background(Color(p.bg).copy(alpha = prefs.dim))
        }
        Box(Modifier.fillMaxSize().then(base))
        HomeScreen(
            Modifier.graphicsLayer {
                val v = drawerP.value
                alpha = 1f - 0.9f * v
                scaleX = 1f - 0.06f * v
                scaleY = 1f - 0.06f * v
            },
            apps, recents, prefs, p, font, shape, launch, onLong,
            onDrawer = { screen = Screen.DRAWER },
            onSearch = { focusSearch = true; screen = Screen.DRAWER },
            onHomeMenu = {
                sheet = SheetSpec(
                    "Flex Launcher", null,
                    listOf(
                        item("Settings") { screen = Screen.SETTINGS },
                        item("Change wallpaper") { hooks.wallpaper() },
                        item("All apps") { screen = Screen.DRAWER },
                    ),
                )
            },
        )
        if (drawerVisible) {
            DrawerScreen(
                visible, prefs, p, font, shape, { drawerP.value }, focusSearch && open,
                frosted = prefs.blur && hooks.blurOk, onLaunch = launch, onLong = onLong,
                onClose = { screen = Screen.HOME },
            )
        }
        if (screen == Screen.SETTINGS) {
            SettingsScreen(
                prefs, usage, hooks.blurOk, p, font,
                onUsage = hooks.usage,
                onClear = { prefs.clearHistory(); historyTick++ },
                onClose = { screen = Screen.HOME },
            )
        }
        sheet?.let { s -> SheetOverlay(s, p, font) { sheet = null } }
    }
}

@Composable
fun HomeScreen(
    modifier: Modifier, apps: List<AppInfo>, recents: List<AppInfo>, prefs: Prefs,
    p: Palette, font: FontFamily, shape: Shape,
    onLaunch: (String) -> Unit, onLong: (AppInfo) -> Unit,
    onDrawer: () -> Unit, onSearch: () -> Unit, onHomeMenu: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(10_000)
        }
    }
    val byPkg = remember(apps) { apps.associateBy { it.pkg } }
    val pinned = prefs.home.mapNotNull { byPkg[it] }
    val dockApps = prefs.dock.mapNotNull { byPkg[it] }
    val cols = prefs.cols
    val c0 = cols * 3
    val c1 = cols * 4
    val pages = maxOf(2, 1 + (maxOf(0, pinned.size - c0) + c1 - 1) / c1)
    val pager = rememberPagerState { pages }
    val txt = Color(p.text)
    val glassTint = Color(p.card).copy(alpha = 0.55f)
    val icon = prefs.iconSize.dp

    Column(
        modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -30f) onDrawer() } },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val start = if (page == 0) 0 else c0 + (page - 1) * c1
            val cap = if (page == 0) c0 else c1
            val mine = pinned.drop(start).take(cap)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp)
                    .graphicsLayer {
                        val off = if (prefs.motion == 0) 0f else abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceAtMost(1f)
                        alpha = 1f - 0.5f * off
                        scaleX = 1f - 0.06f * off
                        scaleY = 1f - 0.06f * off
                    }
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onHomeMenu() }) },
            ) {
                if (page == 0) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        now.format(DateTimeFormatter.ofPattern("HH:mm")), color = txt, fontFamily = font,
                        fontSize = 76.sp, fontWeight = FontWeight.Light,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 12f)),
                    )
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        color = Color(p.accent), fontFamily = font, fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().glass(24.dp, glassTint).clickable(onClick = onSearch)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("Search apps", color = txt.copy(alpha = 0.65f), fontFamily = font, fontSize = 15.sp)
                    }
                    if (prefs.showRecents) {
                        Spacer(Modifier.height(14.dp))
                        Column(Modifier.fillMaxWidth().glass(24.dp, glassTint).padding(14.dp)) {
                            Text("Recent", color = Color(p.accent), fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            if (recents.isEmpty()) {
                                Text(
                                    "Open apps to fill this row. Grant Usage Access in Settings for system-wide recents.",
                                    color = txt.copy(alpha = 0.6f), fontFamily = font, fontSize = 12.sp,
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(recents, key = { it.pkg }) { a ->
                                        AppIcon(a, shape, p, 48.dp, { onLaunch(a.pkg) }, { onLong(a) })
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                mine.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { a ->
                            Box(Modifier.weight(1f)) {
                                AppTile(a, shape, font, p, icon, { onLaunch(a.pkg) }, { onLong(a) })
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (mine.isEmpty()) {
                    Text(
                        "Long-press an app in All apps and choose Add to home.",
                        color = txt.copy(alpha = 0.6f), fontFamily = font, fontSize = 13.sp,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier.padding(3.dp).size(if (on) 8.dp else 6.dp).clip(CircleShape)
                        .background(txt.copy(alpha = if (on) 0.95f else 0.4f)),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                .glass(32.dp, glassTint).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockApps.forEach { a -> AppIcon(a, shape, p, icon, { onLaunch(a.pkg) }, { onLong(a) }) }
            DrawerButton(p, icon, onDrawer)
        }
    }
}

@Composable
fun DrawerScreen(
    apps: List<AppInfo>, prefs: Prefs, p: Palette, font: FontFamily, shape: Shape,
    progress: () -> Float, focusSearch: Boolean, frosted: Boolean,
    onLaunch: (String) -> Unit, onLong: (AppInfo) -> Unit, onClose: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { fr.requestFocus() } }
    val list = remember(apps, q) {
        if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
    }
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = progress()
                translationY = (1f - progress()) * 160f
            }
            .background(Color(p.bg).copy(alpha = if (frosted) 0.42f else 0.85f))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding().imePadding().padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 30f) onClose() } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).glass(28.dp, Color(p.card).copy(alpha = 0.6f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(p.text), fontSize = 16.sp, fontFamily = font),
                    cursorBrush = SolidColor(Color(p.accent)),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    decorationBox = { inner ->
                        Box {
                            if (q.isEmpty()) {
                                Text("Search apps", color = Color(p.text).copy(alpha = 0.5f), fontFamily = font, fontSize = 16.sp)
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
                AppTile(app, shape, font, p, prefs.iconSize.dp, { onLaunch(app.pkg) }, { onLong(app) })
            }
        }
    }
}

@Composable
fun SheetOverlay(s: SheetSpec, p: Palette, font: FontFamily, onDismiss: () -> Unit) {
    val card = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth()
                .clip(card).background(Color(p.bg).copy(alpha = 0.97f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), card)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                s.icon?.let {
                    Image(it, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Text(s.title, color = Color(p.text), fontFamily = font, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            s.items.forEach { (label, action) ->
                Text(
                    label, color = Color(p.text), fontFamily = font, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: Prefs, usage: Boolean, blurOk: Boolean, p: Palette, font: FontFamily,
    onUsage: () -> Unit, onClear: () -> Unit, onClose: () -> Unit,
) {
    val txt = Color(p.text)
    Column(
        Modifier.fillMaxSize().background(Color(p.bg).copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = txt, fontFamily = font, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pill("Done", p.accent, p.bg, onClose)
        }
        SectionLabel("Theme", p, font)
        ChipRow(Palettes.map { it.name }, prefs.theme, p, font) { prefs.theme = it; prefs.save() }
        SectionLabel("Wallpaper", p, font)
        ChipRow(listOf("System wallpaper", "Gradient"), prefs.wallpaper, p, font) { prefs.wallpaper = it; prefs.save() }
        Tune("Wallpaper dimming: ${(prefs.dim * 100).roundToInt()}%", prefs.dim, 0f..0.8f, 7, p, font,
            { prefs.dim = it }, { prefs.save() })
        if (blurOk) {
            ToggleRow("Blur behind app drawer", prefs.blur, p, font) { prefs.blur = it; prefs.save() }
        } else {
            Text("Blur needs Android 12+ with window blur enabled.", color = txt.copy(alpha = 0.55f), fontFamily = font, fontSize = 12.sp)
        }
        SectionLabel("Icon shape", p, font)
        ChipRow(IconShapes.map { it.first }, prefs.shape, p, font) { prefs.shape = it; prefs.save() }
        Tune("Icon size: ${prefs.iconSize}", prefs.iconSize.toFloat(), 44f..72f, 6, p, font,
            { prefs.iconSize = it.roundToInt() }, { prefs.save() })
        SectionLabel("Font", p, font)
        ChipRow(Fonts.map { it.first }, prefs.font, p, font) { prefs.font = it; prefs.save() }
        SectionLabel("Custom accent color (HEX, e.g. FF4081)", p, font)
        HexField(prefs.accentHex, p, font) { prefs.accentHex = it; prefs.save() }
        SectionLabel("Custom background color (HEX)", p, font)
        HexField(prefs.bgHex, p, font) { prefs.bgHex = it; prefs.save() }
        SectionLabel("Animations", p, font)
        ChipRow(listOf("Off", "Smooth", "Spring", "Snappy"), prefs.motion, p, font) { prefs.motion = it; prefs.save() }
        Tune("Animation speed: ${(prefs.speed * 10).roundToInt() / 10f}x", prefs.speed, 0.5f..2f, 5, p, font,
            { prefs.speed = it }, { prefs.save() })
        Tune("Columns: ${prefs.cols}", prefs.cols.toFloat(), 3f..6f, 2, p, font,
            { prefs.cols = it.roundToInt() }, { prefs.save() })
        ToggleRow("Show recent apps on home", prefs.showRecents, p, font) { prefs.showRecents = it; prefs.save() }
        Tune("Recent apps shown: ${prefs.recentCount}", prefs.recentCount.toFloat(), 4f..12f, 7, p, font,
            { prefs.recentCount = it.roundToInt() }, { prefs.save() })
        if (prefs.hidden.isNotEmpty()) {
            SectionLabel("Hidden apps (tap to unhide)", p, font)
            ChipRow(prefs.hidden, -1, p, font) { prefs.hidden = prefs.hidden - prefs.hidden[it]; prefs.save() }
        }
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
fun Tune(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    p: Palette, font: FontFamily, onChange: (Float) -> Unit, onDone: () -> Unit,
) {
    SectionLabel(label, p, font)
    Slider(
        value = value, onValueChange = onChange, valueRange = range, steps = steps,
        onValueChangeFinished = onDone,
        colors = SliderDefaults.colors(thumbColor = Color(p.accent), activeTrackColor = Color(p.accent)),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, p: Palette, font: FontFamily, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(p.text), fontFamily = font, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(p.bg), checkedTrackColor = Color(p.accent)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(app: AppInfo, shape: Shape, p: Palette, size: Dp, onClick: () -> Unit, onLong: () -> Unit) {
    Box(
        Modifier.size(size).shadow(8.dp, shape).clip(shape)
            .then(if (app.adaptive) Modifier else Modifier.background(Color(p.card)))
            .combinedClickable(onClick = onClick, onLongClick = onLong),
    ) {
        Image(
            app.icon, null,
            if (app.adaptive) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(size / 7),
        )
    }
}

@Composable
fun AppTile(
    app: AppInfo, shape: Shape, font: FontFamily, p: Palette, size: Dp,
    onClick: () -> Unit, onLong: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, shape, p, size, onClick, onLong)
        Text(
            app.label, color = Color(p.text), fontFamily = font, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 6f)),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun DrawerButton(p: Palette, size: Dp, onClick: () -> Unit) {
    Column(
        Modifier.size(size).clip(CircleShape).background(Color(p.accent).copy(alpha = 0.92f)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Row {
                repeat(3) { Box(Modifier.padding(2.dp).size(5.dp).clip(CircleShape).background(Color(p.bg))) }
            }
        }
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

@Composable
fun HexField(value: String, p: Palette, font: FontFamily, onValid: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val ok = text.isBlank() || parseHex(text) != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(parseHex(text)?.let { Color(it) } ?: Color(p.card))
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { t ->
                text = t.take(7)
                if (t.isBlank()) onValid("") else if (parseHex(t) != null) onValid(t.trim())
            },
            singleLine = true,
            textStyle = TextStyle(
                color = if (ok) Color(p.text) else Color(0xFFFF6B6B), fontSize = 16.sp, fontFamily = font,
            ),
            cursorBrush = SolidColor(Color(p.accent)),
            modifier = Modifier.weight(1f).glass(16.dp, Color(p.card).copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
