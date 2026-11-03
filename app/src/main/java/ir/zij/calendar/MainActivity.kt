package ir.zij.calendar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

// پالت رنگی اصیل ایرانی زیج
val LapisDarkBg = Color(0xFF0B132B)
val LapisCardDark = Color(0xFF162244)
val PersepolisLightBg = Color(0xFFF7F3E8)
val PersepolisCardLight = Color(0xFFFFFFFF)

val JoyGold = Color(0xFFC89D28)         // شادمانی: طلایی هخامنشی
val SorrowCrimson = Color(0xFF6E1423)   // اندوه تاریخی: عنابی سوخته
val TurquoiseAccent = Color(0xFF00A896) // فیروزه‌ای ایرانی
val PureMourningBlack = Color(0xFF000000)

private val ZijDarkColorScheme = darkColorScheme(
    primary = TurquoiseAccent,
    onPrimary = Color.White,
    secondary = JoyGold,
    onSecondary = Color(0xFF141824),
    background = LapisDarkBg,
    onBackground = Color(0xFFF4F6FB),
    surface = LapisCardDark,
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF1E2D56),
    onSurfaceVariant = Color(0xFFB8C4D9),
    outline = Color(0xFF4A5B82)
)

private val ZijLightColorScheme = lightColorScheme(
    primary = TurquoiseAccent,
    onPrimary = Color.White,
    secondary = JoyGold,
    onSecondary = Color(0xFF141824),
    background = PersepolisLightBg,
    onBackground = Color(0xFF1A2138),
    surface = PersepolisCardLight,
    onSurface = Color(0xFF1A2138),
    surfaceVariant = Color(0xFFEBE4D3),
    onSurfaceVariant = Color(0xFF5A657D),
    outline = Color(0xFFB5AC99)
)

private const val TELEGRAM_URL = "https://t.me/zij_Calendar"
private const val DONOFA_URL = "https://donofa.com/zij"

enum class ZijSection(val title: String, val subtitle: String) {
    CALENDAR("گاه‌شمار ملی", "جشن‌ها، مناسبت‌ها و روزشمار سال"),
    CONVERTER("برگردان و فاصله تاریخ", "شاهنشاهی، خورشیدی، میلادی و سن دقیق"),
    BIRTHDAYS("زادروزها و یادآورها", "فهرست زادروزها و رخدادهای شخصی"),
    SETTINGS("ساماندهی", "کاشی عدد روز، زمان یادآور و نمای برنامه"),
    ABOUT("درباره ما", "معرفی زیج، کانال تلگرام و درگاه همیاری")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // حذف خودکار اعلان قدیمی نسخه‌های پیشین و همگام‌سازی کاشی تنظیمات سریع
        ZijQuickTileService.syncQuickTileAndClearLegacyNotification(this)
        ZijSchedulerAndNotifications.scheduleAllAlarms(this)
        ZijWidgetUpdater.updateAllWidgets(this)

        setContent {
            val rootView = LocalView.current
            var isDarkMode by remember { mutableStateOf(ZijPreferences.isDarkMode(this)) }

            var themeSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
            var revealCenter by remember { mutableStateOf(Offset.Zero) }
            val revealAnim = remember { Animatable(1f) }
            val animScope = rememberCoroutineScope()

            fun triggerCircularThemeToggle(origin: Offset) {
                if (revealAnim.isRunning) return
                val captured = captureViewSnapshot(rootView)
                val nextDark = !isDarkMode
                ZijPreferences.setDarkMode(this@MainActivity, nextDark)

                if (captured != null) {
                    revealCenter = origin
                    themeSnapshot = captured
                    isDarkMode = nextDark
                    animScope.launch {
                        revealAnim.snapTo(0f)
                        revealAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 580, easing = FastOutSlowInEasing)
                        )
                        themeSnapshot = null
                    }
                } else {
                    isDarkMode = nextDark
                }
            }

            MaterialTheme(
                colorScheme = if (isDarkMode) ZijDarkColorScheme else ZijLightColorScheme
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ZijMainShell(
                            isDark = isDarkMode,
                            onToggleThemeFromOrigin = { centerOffset ->
                                triggerCircularThemeToggle(centerOffset)
                            }
                        )
                    }

                    val activeSnap = themeSnapshot
                    if (activeSnap != null && revealAnim.value < 1f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val maxRadius = hypot(size.width, size.height)
                            val radius = maxRadius * revealAnim.value

                            val fullRectPath = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                            }
                            val circleHolePath = Path().apply {
                                addOval(Rect(center = revealCenter, radius = radius))
                            }
                            val diffMask = Path().apply {
                                op(fullRectPath, circleHolePath, PathOperation.Difference)
                            }

                            clipPath(diffMask) {
                                drawImage(
                                    image = activeSnap,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )
                            }

                            drawCircle(
                                color = JoyGold.copy(alpha = (1f - revealAnim.value) * 0.85f),
                                radius = radius,
                                center = revealCenter,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun captureViewSnapshot(view: View): ImageBitmap? {
    return try {
        if (view.width <= 0 || view.height <= 0) return null
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(bmp)
        view.draw(androidCanvas)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZijMainShell(
    isDark: Boolean,
    onToggleThemeFromOrigin: (Offset) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val today = remember { PersianDateEngine.today() }
    var currentSection by remember { mutableStateOf(ZijSection.CALENDAR) }

    var displayedYear by remember { mutableIntStateOf(today.jalaliYear) }
    var displayedMonth by remember { mutableIntStateOf(today.month) }
    var selectedDay by remember { mutableIntStateOf(today.day) }
    var monthSlideDirection by remember { mutableIntStateOf(1) }

    var eventsMap by remember { mutableStateOf(PersianDateEngine.loadEventsOfflineFirst(context)) }
    var personalRecords by remember { mutableStateOf(PersonalRecordsRepository.loadAll(context)) }

    var showExplanationSheet by remember { mutableStateOf(false) }
    var showAddRecordDialog by remember { mutableStateOf(false) }
    var showQuickJumpDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var isBuildingStoryCard by remember { mutableStateOf(false) }

    var editingRecord by remember { mutableStateOf<PersonalRecord?>(null) }
    var prefillMonth by remember { mutableIntStateOf(today.month) }
    var prefillDay by remember { mutableIntStateOf(today.day) }
    var themeBtnCenter by remember { mutableStateOf(Offset(200f, 120f)) }

    fun launchStoryCardShare(ev: ZijEvent, jy: Int, m: Int, d: Int) {
        if (isBuildingStoryCard) return
        scope.launch {
            isBuildingStoryCard = true
            StoryCardShareHelper.shareOccasionStoryCard(
                context = context,
                jalaliYear = jy,
                month = m,
                day = d,
                event = ev
            )
            isBuildingStoryCard = false
        }
    }

    BackHandler(enabled = drawerState.isOpen || currentSection != ZijSection.CALENDAR) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentSection = ZijSection.CALENDAR
        }
    }

    LaunchedEffect(Unit) {
        PersianDateEngine.syncEventsOnlineIfAvailable(context)?.let { updated ->
            eventsMap = updated
            ZijWidgetUpdater.updateAllWidgets(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ZijSchedulerAndNotifications.scheduleAllAlarms(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val bgColor = if (isDark) LapisDarkBg else PersepolisLightBg
    val cardColor = if (isDark) LapisCardDark else PersepolisCardLight
    val primaryText = if (isDark) Color(0xFFF4F6FB) else Color(0xFF1A2138)
    val secondaryText = if (isDark) Color(0xFFA0AEC0) else Color(0xFF5A657D)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = cardColor,
                modifier = Modifier.width(305.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .border(1.dp, JoyGold.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Zij | زیج",
                            color = JoyGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "امروز: ${today.day.toPersianDigits()} ${PersianDateEngine.monthNames[today.month - 1]} ${today.imperialYear.toPersianDigits()} شاهنشاهی",
                            color = primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ZijSection.entries.forEach { sec ->
                        val active = currentSection == sec
                        val iconVec = when (sec) {
                            ZijSection.CALENDAR -> Icons.Default.DateRange
                            ZijSection.CONVERTER -> Icons.Default.Refresh
                            ZijSection.BIRTHDAYS -> Icons.Default.Notifications
                            ZijSection.SETTINGS -> Icons.Default.Settings
                            ZijSection.ABOUT -> Icons.Default.Info
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) TurquoiseAccent.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentSection = sec
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = sec.title,
                                tint = if (active) TurquoiseAccent else secondaryText
                            )
                            Column {
                                Text(
                                    text = sec.title,
                                    color = if (active) TurquoiseAccent else primaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = sec.subtitle,
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "نسخه ۳.۰ • Zij | زیج",
                        color = secondaryText.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = bgColor,
            floatingActionButton = {
                if (currentSection == ZijSection.BIRTHDAYS) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingRecord = null
                            prefillMonth = today.month
                            prefillDay = today.day
                            showAddRecordDialog = true
                        },
                        containerColor = TurquoiseAccent,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("ثبت یادآور نو", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(cardColor)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { drawerState.open() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "فهرست بخش‌ها",
                                tint = primaryText
                            )
                        }

                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = "درود • Zij",
                                color = TurquoiseAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (currentSection == ZijSection.CALENDAR) {
                                    "${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()}"
                                } else {
                                    currentSection.title
                                },
                                color = primaryText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (currentSection == ZijSection.CALENDAR) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    monthSlideDirection = if (today.jalaliYear > displayedYear || (today.jalaliYear == displayedYear && today.month >= displayedMonth)) 1 else -1
                                    displayedYear = today.jalaliYear
                                    displayedMonth = today.month
                                    selectedDay = today.day
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = TurquoiseAccent)
                            ) {
                                Text("امروز", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            TextButton(
                                onClick = { currentSection = ZijSection.CALENDAR },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("گاه‌شمار", color = TurquoiseAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardColor)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showSearchDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "جستجو",
                                tint = primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardColor)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    themeBtnCenter = Offset(
                                        x = pos.x + coords.size.width / 2f,
                                        y = pos.y + coords.size.height / 2f
                                    )
                                }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleThemeFromOrigin(themeBtnCenter)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isDark) "☀" else "☾",
                                color = JoyGold,
                                fontSize = 17.sp
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = currentSection,
                    transitionSpec = {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                    },
                    label = "section_switch"
                ) { activeSec ->
                    when (activeSec) {
                        ZijSection.CALENDAR -> CalendarMainScreen(
                            today = today,
                            displayedYear = displayedYear,
                            displayedMonth = displayedMonth,
                            selectedDay = selectedDay,
                            monthSlideDirection = monthSlideDirection,
                            eventsMap = eventsMap,
                            personalRecords = personalRecords,
                            isDark = isDark,
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onStepMonth = { step ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                monthSlideDirection = step
                                if (step > 0) {
                                    if (displayedMonth == 12) {
                                        displayedMonth = 1
                                        displayedYear += 1
                                    } else {
                                        displayedMonth += 1
                                    }
                                } else {
                                    if (displayedMonth == 1) {
                                        displayedMonth = 12
                                        displayedYear -= 1
                                    } else {
                                        displayedMonth -= 1
                                    }
                                }
                            },
                            onOpenQuickJump = { showQuickJumpDialog = true },
                            onSelectDay = { d ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDay = d
                            },
                            onShowExplanation = { showExplanationSheet = true },
                            onShareStory = { ev ->
                                val dSafe = selectedDay.coerceIn(1, PersianDateEngine.getDaysInMonth(displayedYear, displayedMonth))
                                launchStoryCardShare(ev, displayedYear, displayedMonth, dSafe)
                            },
                            onAddPersonalForSelectedDay = {
                                editingRecord = null
                                prefillMonth = displayedMonth
                                prefillDay = selectedDay.coerceIn(1, PersianDateEngine.getDaysInMonth(displayedYear, displayedMonth))
                                showAddRecordDialog = true
                            }
                        )

                        ZijSection.CONVERTER -> ConverterAndDiffScreen(
                            today = today,
                            eventsMap = eventsMap,
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onJumpToCalendarDate = { jy, m, d ->
                                displayedYear = jy
                                displayedMonth = m
                                selectedDay = d
                                currentSection = ZijSection.CALENDAR
                            }
                        )

                        ZijSection.BIRTHDAYS -> BirthdaysAndRemindersScreen(
                            today = today,
                            records = personalRecords,
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onEditRecord = { rec ->
                                editingRecord = rec
                                prefillMonth = rec.month
                                prefillDay = rec.day
                                showAddRecordDialog = true
                            },
                            onDeleteRecord = { rec ->
                                personalRecords = PersonalRecordsRepository.deleteRecord(context, rec.id)
                            }
                        )

                        ZijSection.SETTINGS -> SettingsCleanScreen(
                            isDark = isDark,
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onDarkChanged = { onToggleThemeFromOrigin(themeBtnCenter) }
                        )

                        ZijSection.ABOUT -> AboutUsFormalScreen(
                            cardColor = cardColor,
                            primaryText = primaryText,
                            secondaryText = secondaryText
                        )
                    }
                }
            }
        }
    }

    // پنجره کوچک در حال آماده‌سازی کارت استوری (دریافت تصویر پس‌زمینه از گیت‌هاب)
    if (isBuildingStoryCard) {
        Dialog(onDismissRequest = {}) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardColor)
                        .border(1.dp, JoyGold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = TurquoiseAccent,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "در حال آماده‌سازی کارت استوری...",
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    val safeDay = selectedDay.coerceIn(1, PersianDateEngine.getDaysInMonth(displayedYear, displayedMonth))
    val selectedEvent = eventsMap[displayedMonth to safeDay]
    if (showExplanationSheet && selectedEvent != null) {
        ModalBottomSheet(
            onDismissRequest = { showExplanationSheet = false },
            containerColor = cardColor
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = 28.dp)
                ) {
                    Text(
                        text = "${safeDay.toPersianDigits()} ${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()} شاهنشاهی",
                        color = TurquoiseAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedEvent.title,
                        color = primaryText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = selectedEvent.description,
                        color = primaryText.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            showExplanationSheet = false
                            launchStoryCardShare(selectedEvent, displayedYear, displayedMonth, safeDay)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = JoyGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ساخت و هم‌رسانی کارت استوری این جشن", color = JoyGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddRecordDialog) {
        SmoothAddOrEditRecordDialog(
            initialRecord = editingRecord,
            defaultMonth = prefillMonth,
            defaultDay = prefillDay,
            bgColor = bgColor,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = { showAddRecordDialog = false },
            onSave = { newRecord ->
                personalRecords = PersonalRecordsRepository.saveRecord(context, newRecord)
                ZijSchedulerAndNotifications.scheduleAllAlarms(context)
                showAddRecordDialog = false
            }
        )
    }

    if (showQuickJumpDialog) {
        QuickMonthYearJumpDialog(
            currentJalaliYear = displayedYear,
            currentMonth = displayedMonth,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = { showQuickJumpDialog = false },
            onConfirm = { jy, m ->
                monthSlideDirection = if (jy > displayedYear || (jy == displayedYear && m >= displayedMonth)) 1 else -1
                displayedYear = jy
                displayedMonth = m
                showQuickJumpDialog = false
            }
        )
    }

    if (showSearchDialog) {
        InstantSearchDialog(
            eventsMap = eventsMap,
            personalRecords = personalRecords,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = { showSearchDialog = false },
            onSelectMonthDay = { m, d ->
                displayedMonth = m
                selectedDay = d
                currentSection = ZijSection.CALENDAR
                showSearchDialog = false
            }
        )
    }
}

@Composable
fun CalendarMainScreen(
    today: PersianDate,
    displayedYear: Int,
    displayedMonth: Int,
    selectedDay: Int,
    monthSlideDirection: Int,
    eventsMap: Map<Pair<Int, Int>, ZijEvent>,
    personalRecords: List<PersonalRecord>,
    isDark: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onStepMonth: (Int) -> Unit,
    onOpenQuickJump: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onShowExplanation: () -> Unit,
    onShareStory: (ZijEvent) -> Unit,
    onAddPersonalForSelectedDay: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showLegendGuide by remember { mutableStateOf(false) }

    val daysInMonth = PersianDateEngine.getDaysInMonth(displayedYear, displayedMonth)
    val safeSelectedDay = selectedDay.coerceIn(1, daysInMonth)
    val selectedWeekIndex = PersianDateEngine.getDayOfWeekIndex(displayedYear, displayedMonth, safeSelectedDay)
    val selectedEvent = eventsMap[displayedMonth to safeSelectedDay]
    val dayPersonalList = personalRecords.filter { it.month == displayedMonth && it.day == safeSelectedDay }

    val yearStats = remember(today, displayedYear, displayedMonth, safeSelectedDay) {
        PersianDateEngine.computeYearStats(today, displayedYear, displayedMonth, safeSelectedDay)
    }

    val animatedAbsDiff by animateIntAsState(
        targetValue = abs(yearStats.diffFromToday),
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "diff_days_anim"
    )
    val animatedPassedDays by animateIntAsState(
        targetValue = yearStats.daysPassedInYear,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "passed_days_anim"
    )
    val animatedLeftDays by animateIntAsState(
        targetValue = yearStats.daysLeftInYear,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "left_days_anim"
    )
    val animatedYearProgress by animateFloatAsState(
        targetValue = (yearStats.daysPassedInYear.toFloat() / yearStats.totalDaysInYear.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "year_progress_bar"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(cardColor)
                .pointerInput(displayedYear, displayedMonth) {
                    var dragSumX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragSumX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragSumX += dragAmount
                        },
                        onDragEnd = {
                            if (dragSumX > 85f) {
                                onStepMonth(1)
                            } else if (dragSumX < -85f) {
                                onStepMonth(-1)
                            }
                        }
                    )
                }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onStepMonth(-1) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "ماه پیشین",
                        tint = primaryText
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryText.copy(alpha = 0.05f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenQuickJump()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()} شاهنشاهی",
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "انتخاب ماه",
                        tint = JoyGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = { onStepMonth(1) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "ماه پسین",
                        tint = primaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PersianDateEngine.weekDaysShort.forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = if (idx == 6) TurquoiseAccent else secondaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = Pair(displayedYear, displayedMonth),
                transitionSpec = {
                    val dir = monthSlideDirection
                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { fullW ->
                        if (dir > 0) -fullW / 2 else fullW / 2
                    } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { fullW ->
                                if (dir > 0) fullW / 2 else -fullW / 2
                            } + fadeOut(tween(180)))
                },
                label = "month_grid_slide"
            ) { (animYear, animMonth) ->
                val mDays = PersianDateEngine.getDaysInMonth(animYear, animMonth)
                val firstWeekIdx = PersianDateEngine.getDayOfWeekIndex(animYear, animMonth, 1)
                val totalSlots = firstWeekIdx + mDays
                val rows = (totalSlots + 6) / 7

                Column(modifier = Modifier.fillMaxWidth()) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNum = cellIndex - firstWeekIdx + 1
                                if (dayNum in 1..mDays) {
                                    val ev = eventsMap[animMonth to dayNum]
                                    val hasPersonal = personalRecords.any { it.month == animMonth && it.day == dayNum }
                                    val isToday = (animYear == today.jalaliYear &&
                                            animMonth == today.month &&
                                            dayNum == today.day)
                                    val isSelected = (dayNum == safeSelectedDay)

                                    CalendarDayCell(
                                        modifier = Modifier.weight(1f),
                                        dayNumber = dayNum,
                                        event = ev,
                                        hasPersonalRecord = hasPersonal,
                                        isToday = isToday,
                                        isSelected = isSelected,
                                        isFriday = (c == 6),
                                        isDark = isDark,
                                        defaultTextColor = primaryText,
                                        onClick = { onSelectDay(dayNum) }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .align(AbsoluteAlignment.CenterLeft)
                        .clip(RoundedCornerShape(10.dp))
                        .background(primaryText.copy(alpha = 0.06f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showLegendGuide = !showLegendGuide
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TurquoiseAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (showLegendGuide) "بستن راهنما" else "راهنما",
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = showLegendGuide,
                enter = expandVertically(tween(240)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryText.copy(alpha = 0.04f))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendDot("شادمانی", JoyGold, secondaryText)
                    LegendDot("اندوه", SorrowCrimson, secondaryText)
                    LegendDualDot("ترکیبی", secondaryText)
                    LegendDot("جاویدنام‌ها", PureMourningBlack, secondaryText)
                }
            }
        }

        // پنجره نخست (مجزا): رخدادهای روز
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "رخدادهای ${PersianDateEngine.weekDaysFull[selectedWeekIndex]} ${safeSelectedDay.toPersianDigits()} ${PersianDateEngine.monthNames[displayedMonth - 1]}",
                    color = TurquoiseAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onAddPersonalForSelectedDay,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+ ثبت یادآور", color = JoyGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedContent(
                targetState = selectedEvent,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "event_card_anim"
            ) { ev ->
                if (ev != null) {
                    Column {
                        Text(
                            text = ev.title,
                            color = primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onShowExplanation,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TurquoiseAccent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("توضیحات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { onShareStory(ev) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = JoyGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("کارت استوری", color = primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "برای این روز رخداد باستانی یا ملی ثبت نشده است.",
                        color = secondaryText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            if (dayPersonalList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = secondaryText.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                dayPersonalList.forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (rec.isBirthday) "🎂 زادروز: ${rec.title}" else "📌 رخداد شخصی: ${rec.title}",
                            color = JoyGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (rec.note.isNotBlank()) {
                            Text(text = rec.note, color = secondaryText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // پنجره دوم (مجزا): روزشمار سال و برابرهای تاریخ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, TurquoiseAccent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val diffStatusText = when {
                yearStats.diffFromToday == 0 -> "تاریخ برگزیده: امروز"
                yearStats.diffFromToday > 0 -> "${animatedAbsDiff.toPersianDigits()} روز مانده تا این تاریخ"
                else -> "${animatedAbsDiff.toPersianDigits()} روز از این تاریخ گذشته است"
            }
            val diffBadgeColor = when {
                yearStats.diffFromToday == 0 -> JoyGold
                yearStats.diffFromToday > 0 -> TurquoiseAccent
                else -> secondaryText
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(diffBadgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diffStatusText,
                    color = if (yearStats.diffFromToday < 0) primaryText else diffBadgeColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primaryText.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${animatedPassedDays.toPersianDigits()} روز",
                        color = JoyGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "گذشته از آغاز سال",
                        color = secondaryText,
                        fontSize = 11.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primaryText.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${animatedLeftDays.toPersianDigits()} روز",
                        color = TurquoiseAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "مانده تا سال نو",
                        color = secondaryText,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(primaryText.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedYearProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(TurquoiseAccent)
                )
            }

            HorizontalDivider(color = secondaryText.copy(alpha = 0.18f))

            Text(
                text = "برابرهای این روز به تفکیک:",
                color = secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            val mName = PersianDateEngine.monthNames[displayedMonth - 1]
            SeparateDateBox(
                calendarLabel = "شاهنشاهی",
                dateValue = "${safeSelectedDay.toPersianDigits()} $mName ${(displayedYear + 1180).toPersianDigits()}",
                accent = JoyGold,
                primaryText = primaryText
            )
            SeparateDateBox(
                calendarLabel = "خورشیدی",
                dateValue = "${safeSelectedDay.toPersianDigits()} $mName ${displayedYear.toPersianDigits()}",
                accent = TurquoiseAccent,
                primaryText = primaryText
            )
            SeparateDateBox(
                calendarLabel = "میلادی",
                dateValue = PersianDateEngine.formatGregorianPersian(displayedYear, displayedMonth, safeSelectedDay),
                accent = secondaryText,
                primaryText = primaryText
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun SeparateDateBox(
    calendarLabel: String,
    dateValue: String,
    accent: Color,
    primaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(primaryText.copy(alpha = 0.045f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = calendarLabel, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(text = dateValue, color = primaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun CalendarDayCell(
    modifier: Modifier,
    dayNumber: Int,
    event: ZijEvent?,
    hasPersonalRecord: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isFriday: Boolean,
    isDark: Boolean,
    defaultTextColor: Color,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 1.07f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cell_spring"
    )

    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        isSelected -> TurquoiseAccent
        isToday -> JoyGold
        event?.category == EventCategory.BLACK && isDark -> Color(0xFF666666)
        else -> Color.Transparent
    }

    val cellModifier = modifier
        .scale(scaleAnim)
        .aspectRatio(1f)
        .clip(shape)
        .then(
            when (event?.category) {
                EventCategory.JOY -> Modifier.background(JoyGold)
                EventCategory.SORROW -> Modifier.background(SorrowCrimson)
                EventCategory.BLACK -> Modifier.background(PureMourningBlack)
                EventCategory.DUAL -> {
                    Modifier.drawBehind {
                        val halfW = size.width / 2f
                        drawRect(
                            color = SorrowCrimson,
                            topLeft = Offset(0f, 0f),
                            size = Size(halfW, size.height)
                        )
                        drawRect(
                            color = JoyGold,
                            topLeft = Offset(halfW, 0f),
                            size = Size(halfW, size.height)
                        )
                    }
                }
                null -> Modifier.background(
                    if (isSelected) TurquoiseAccent.copy(alpha = 0.18f) else Color.Transparent
                )
            }
        )
        .border(
            width = if (isSelected || isToday) 2.dp else if (event?.category == EventCategory.BLACK) 1.dp else 0.dp,
            color = borderColor,
            shape = shape
        )
        .clickable { onClick() }

    val textColor = when (event?.category) {
        EventCategory.JOY -> Color(0xFF141824)
        EventCategory.SORROW -> Color.White
        EventCategory.BLACK -> Color(0xFFE2E8F0)
        EventCategory.DUAL -> Color.White
        null -> if (isFriday) TurquoiseAccent else defaultTextColor
    }

    val textStyle = if (event?.category == EventCategory.DUAL) {
        TextStyle(
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            shadow = Shadow(
                color = Color(0xB3000000),
                offset = Offset(0f, 1f),
                blurRadius = 4f
            )
        )
    } else {
        TextStyle(
            color = textColor,
            fontWeight = if (event != null || isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp
        )
    }

    Box(
        modifier = cellModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayNumber.toPersianDigits(),
            style = textStyle
        )
        if (hasPersonalRecord) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (event?.category == EventCategory.JOY) Color(0xFF0B132B) else TurquoiseAccent)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysAndRemindersScreen(
    today: PersianDate,
    records: List<PersonalRecord>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onEditRecord: (PersonalRecord) -> Unit,
    onDeleteRecord: (PersonalRecord) -> Unit
) {
    var filterMode by remember { mutableIntStateOf(0) }

    val filteredSorted = remember(records, filterMode, today) {
        records
            .filter {
                when (filterMode) {
                    1 -> it.isBirthday
                    2 -> !it.isBirthday
                    else -> true
                }
            }
            .sortedBy { PersianDateEngine.daysUntilNextAnnualDate(today, it.month, it.day) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(bottom = 80.dp)
    ) {
        Text(
            text = "یادآوری هوشمند یک هفته پیش، روز پیش و در نخستین دقیقه بامداد (۰۰:۰۱)",
            color = secondaryText,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text("همه (${records.size.toPersianDigits()})") }
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text("زادروزها") }
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text("رخدادهای شخصی") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSorted.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardColor)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("هنوز زادروز یا رخداد شخصی ثبت نکرده‌اید.", color = primaryText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "با زدن دکمه «ثبت یادآور نو» در پایین صفحه، زادروز عزیزان یا رخدادهای مهم خود را بیفزایید.",
                    color = secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            filteredSorted.forEach { rec ->
                val daysLeft = PersianDateEngine.daysUntilNextAnnualDate(today, rec.month, rec.day)
                val countdownLabel = when (daysLeft) {
                    0 -> "امروز!"
                    1 -> "فردا"
                    else -> "${daysLeft.toPersianDigits()} روز مانده"
                }
                val accent = if (rec.isBirthday) JoyGold else TurquoiseAccent

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardColor)
                        .border(1.dp, accent.copy(alpha = if (daysLeft <= 7) 0.6f else 0.15f), RoundedCornerShape(18.dp))
                        .clickable { onEditRecord(rec) }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (rec.isBirthday) "🎂 زادروز ${rec.title}" else "📌 ${rec.title}",
                                color = primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val ageText = if (rec.isBirthday && rec.birthImperialYear != null && today.imperialYear >= rec.birthImperialYear) {
                                val turningAge = (today.imperialYear - rec.birthImperialYear) + (if (daysLeft > 0 && (rec.month < today.month || (rec.month == today.month && rec.day < today.day))) 1 else 0)
                                " • آستانه ${turningAge.toPersianDigits()} سالگی"
                            } else ""
                            Text(
                                text = "${rec.day.toPersianDigits()} ${PersianDateEngine.monthNames[rec.month - 1]}$ageText",
                                color = secondaryText,
                                fontSize = 13.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = countdownLabel,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { onDeleteRecord(rec) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = secondaryText)
                            }
                        }
                    }

                    if (rec.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = rec.note, color = secondaryText, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * صفحه «ساماندهی» خلوت و ویژه تنظیمات فنی (کارت همیاری به بخش «درباره ما» منتقل شد)
 */
@Composable
fun SettingsCleanScreen(
    isDark: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDarkChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var festivalNotifyEnabled by remember { mutableStateOf(ZijPreferences.isFestivalNotifyEnabled(context)) }
    var reminderHour by remember { mutableIntStateOf(ZijPreferences.getReminderHour(context)) }
    var reminderMinute by remember { mutableIntStateOf(ZijPreferences.getReminderMinute(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // کارت ویژه کاشی تنظیمات سریع (کنار نور صفحه و حالت پرواز)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, TurquoiseAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "کاشی عدد روز در نوار تنظیمات سریع",
                color = TurquoiseAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "نمایش عدد روز ماه در بخش ابزارهای بالای گوشی (کنار تنظیم نور صفحه، وای‌فای و حالت پرواز). ارقام این کاشی به‌صورت خودکار با زبان گوشی شما (فارسی یا انگلیسی) سازگار می‌شود.",
                color = secondaryText,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { ZijQuickTileService.promptAddQuickTile(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TurquoiseAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("افزودن کاشی عدد روز به نوار بالای گوشی", fontWeight = FontWeight.Bold)
            }
        }

        // کارت ساماندهی نما و اعلان‌ها
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("نمای شب لاجوردی", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("تغییر نرم میان حالت شب و نمای روشن استخوانی", color = secondaryText, fontSize = 12.sp)
                }
                Switch(checked = isDark, onCheckedChange = onDarkChanged)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = secondaryText.copy(alpha = 0.16f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("شادباش جشن‌های ملی و باستانی", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("فرستادن اعلان در روز جشن‌های خجسته ایران‌زمین", color = secondaryText, fontSize = 12.sp)
                }
                Switch(
                    checked = festivalNotifyEnabled,
                    onCheckedChange = { enabled ->
                        festivalNotifyEnabled = enabled
                        ZijPreferences.setFestivalNotifyEnabled(context, enabled)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = secondaryText.copy(alpha = 0.16f))
            Spacer(modifier = Modifier.height(12.dp))

            Text("زمان فرستادن یادآورهای روزانه", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val formattedTime = "${reminderHour.toString().padStart(2, '0').toPersianDigits()}:${reminderMinute.toString().padStart(2, '0').toPersianDigits()}"
            Text(
                text = "زمان کنونی: ساعت $formattedTime بامداد",
                color = TurquoiseAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        reminderHour = 0
                        reminderMinute = 1
                        ZijPreferences.setReminderTime(context, 0, 1)
                        ZijSchedulerAndNotifications.scheduleDailyReminder(context)
                    }
                ) {
                    Text("۰۰:۰۱ بامداد", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        reminderHour = 9
                        reminderMinute = 0
                        ZijPreferences.setReminderTime(context, 9, 0)
                        ZijSchedulerAndNotifications.scheduleDailyReminder(context)
                    }
                ) {
                    Text("۰۹:۰۰ بامداد", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        ZijSchedulerAndNotifications.evaluateAndSendDailyReminders(context, forceTest = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseAccent, contentColor = Color.White)
                ) {
                    Text("آزمایش اعلان", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // کارت راهنمای ابزارک ۴×۲ صفحه اصلی
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Text("ابزارک صفحه اصلی (تیره و روشن ۴×۲)", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "انگشت خود را روی فضای خالی صفحه اصلی گوشی نگه دارید، گزینه «ابزارک‌ها» (Widgets) را بزنید و یکی از دو طرح تیره یا روشن Zij را برگزینید.",
                color = secondaryText,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * صفحه رسمی «درباره ما» شامل معرفی زیج، کانال تلگرام رسمی و شیوه همیاری بی‌آگهی
 */
@Composable
fun AboutUsFormalScreen(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // کارت معرفی رسمی گاه‌شمار زیج
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, JoyGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "Zij | زیج",
                color = JoyGold,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "گاه‌شمار ملی «زیج» سامانه‌ای مستقل برای پاسداشت گاه‌شماری ایرانی، بازنمایی دقیق جشن‌های باستانی و بزرگداشت رخدادهای تاریخی ایران‌زمین است. واژه «زیج» برگرفته از دانش ستاره‌شناسی و گاه‌نگاری کهن ایرانی است.",
                color = primaryText.copy(alpha = 0.92f),
                fontSize = 14.sp,
                lineHeight = 24.sp
            )
        }

        // کارت راه ارتباطی رسمی (تلگرام)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, TurquoiseAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = TurquoiseAccent)
                Text(
                    text = "راه ارتباطی رسمی: تلگرام",
                    color = TurquoiseAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "جهت دریافت تازه‌ترین نسخه‌های برنامه، اطلاع از به‌روزرسانی‌ها و در میان گذاشتن دیدگاه‌ها و پیشنهادهای خود، به کانال رسمی زیج در تلگرام بپیوندید:",
                color = primaryText.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "برنامه تلگرام یا مرورگر یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ورود به کانال تلگرام", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("کانال تلگرام زیج", TELEGRAM_URL))
                        Toast.makeText(context, "نشانی تلگرام رونوشت شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("رونوشت نشانی", color = primaryText, fontSize = 12.sp)
                }
            }
        }

        // کارت شیوه درآمد و پشتیبانی رسمی (بدون تبلیغات - درگاه دونفا)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.2.dp, JoyGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = JoyGold)
                Text(
                    text = "شیوه پشتیبانی و پایداری برنامه",
                    color = JoyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "این برنامه عاری از هرگونه آگهی بازرگانی است و به‌صورت کاملاً مستقل و رایگان در اختیار هم‌میهنان قرار گرفته است. همراهان گرامی که خواهان پشتیبانی از پایداری، نگهداری و گسترش گاه‌شمار «زیج» هستند، می‌توانند از راه درگاه همیاری زیر اقدام نمایند:",
                color = primaryText.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 23.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONOFA_URL)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "مرورگر یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = JoyGold, contentColor = Color(0xFF141824)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("درگاه همیاری (دونفا)", fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("همیاری زیج", DONOFA_URL))
                        Toast.makeText(context, "پیوند همیاری رونوشت شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("رونوشت پیوند", color = primaryText, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LegendDot(label: String, color: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
        )
        Text(text = label, color = textColor, fontSize = 11.sp)
    }
}

@Composable
fun LegendDualDot(label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .drawBehind {
                    val hw = size.width / 2f
                    drawRect(SorrowCrimson, Offset(0f, 0f), Size(hw, size.height))
                    drawRect(JoyGold, Offset(hw, 0f), Size(hw, size.height))
                }
        )
        Text(text = label, color = textColor, fontSize = 11.sp)
    }
}
