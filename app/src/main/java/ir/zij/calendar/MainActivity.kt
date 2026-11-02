package ir.zij.calendar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.zij.calendar.PersianDateEngine.toPersianDigits

// پالت رنگی اصیل ایرانی زیج
val LapisDarkBg = Color(0xFF0B132B)
val LapisCardDark = Color(0xFF162244)
val PersepolisLightBg = Color(0xFFF7F3E8)
val PersepolisCardLight = Color(0xFFFFFFFF)

val JoyGold = Color(0xFFC89D28)         // شادمانی: طلایی هخامنشی
val SorrowCrimson = Color(0xFF6E1423)   // اندوه تاریخی: عنابی سوخته (خون سیاوشان)
val TurquoiseAccent = Color(0xFF00A896) // فیروزه‌ای ایرانی
val PureMourningBlack = Color(0xFF000000)

private const val DONOFA_URL = "https://donofa.com/zij"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarHelper.updateStatusBarDayNumber(this)
        ZijSchedulerAndNotifications.scheduleAllAlarms(this)
        ZijWidgetUpdater.updateAllWidgets(this)

        setContent {
            var isDarkMode by remember { mutableStateOf(ZijPreferences.isDarkMode(this)) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ZijRootApp(
                    isDark = isDarkMode,
                    onDarkChanged = { newDark ->
                        isDarkMode = newDark
                        ZijPreferences.setDarkMode(this, newDark)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZijRootApp(isDark: Boolean, onDarkChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val today = remember { PersianDateEngine.today() }

    var currentTab by remember { mutableIntStateOf(0) } // 0: گاه‌شمار، 1: زادروز و یادآور، 2: ساماندهی
    var displayedYear by remember { mutableIntStateOf(today.jalaliYear) }
    var displayedMonth by remember { mutableIntStateOf(today.month) }
    var selectedDay by remember { mutableIntStateOf(today.day) }

    var eventsMap by remember { mutableStateOf(PersianDateEngine.loadEventsOfflineFirst(context)) }
    var personalRecords by remember { mutableStateOf(PersonalRecordsRepository.loadAll(context)) }

    var showExplanationSheet by remember { mutableStateOf(false) }
    var showAddRecordSheet by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<PersonalRecord?>(null) }
    var prefillMonth by remember { mutableIntStateOf(today.month) }
    var prefillDay by remember { mutableIntStateOf(today.day) }

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
            StatusBarHelper.updateStatusBarDayNumber(context)
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

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            if (currentTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingRecord = null
                        prefillMonth = today.month
                        prefillDay = today.day
                        showAddRecordSheet = true
                    },
                    containerColor = TurquoiseAccent,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("ثبت یادآور نو", fontWeight = FontWeight.Bold) }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = cardColor,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "گاه‌شمار") },
                    label = { Text("گاه‌شمار", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JoyGold,
                        selectedTextColor = JoyGold,
                        indicatorColor = JoyGold.copy(alpha = 0.16f),
                        unselectedIconColor = secondaryText,
                        unselectedTextColor = secondaryText
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "زادروز و یادآور") },
                    label = { Text("زادروز و یادآور", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TurquoiseAccent,
                        selectedTextColor = TurquoiseAccent,
                        indicatorColor = TurquoiseAccent.copy(alpha = 0.16f),
                        unselectedIconColor = secondaryText,
                        unselectedTextColor = secondaryText
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "ساماندهی") },
                    label = { Text("ساماندهی", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JoyGold,
                        selectedTextColor = JoyGold,
                        indicatorColor = JoyGold.copy(alpha = 0.16f),
                        unselectedIconColor = secondaryText,
                        unselectedTextColor = secondaryText
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> CalendarTabScreen(
                    today = today,
                    displayedYear = displayedYear,
                    displayedMonth = displayedMonth,
                    selectedDay = selectedDay,
                    eventsMap = eventsMap,
                    personalRecords = personalRecords,
                    isDark = isDark,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onChangeYearMonth = { y, m ->
                        displayedYear = y
                        displayedMonth = m
                    },
                    onSelectDay = { d -> selectedDay = d },
                    onToggleTheme = { onDarkChanged(!isDark) },
                    onShowExplanation = { showExplanationSheet = true },
                    onAddPersonalForSelectedDay = {
                        editingRecord = null
                        prefillMonth = displayedMonth
                        prefillDay = selectedDay
                        showAddRecordSheet = true
                    }
                )

                1 -> BirthdaysAndRemindersScreen(
                    today = today,
                    records = personalRecords,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onEditRecord = { rec ->
                        editingRecord = rec
                        prefillMonth = rec.month
                        prefillDay = rec.day
                        showAddRecordSheet = true
                    },
                    onDeleteRecord = { rec ->
                        personalRecords = PersonalRecordsRepository.deleteRecord(context, rec.id)
                    }
                )

                2 -> SettingsAndSupportScreen(
                    isDark = isDark,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onDarkChanged = onDarkChanged
                )
            }
        }

        // پنجره بازشونده «توضیحات» رخداد ملی
        val selectedEvent = eventsMap[displayedMonth to selectedDay]
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
                            text = "${selectedDay.toPersianDigits()} ${PersianDateEngine.monthNames[displayedMonth - 1]}",
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
                            lineHeight = 25.sp
                        )
                    }
                }
            }
        }

        // پنجره ثبت یا ویرایش «زادروز» و «رخداد شخصی»
        if (showAddRecordSheet) {
            AddOrEditRecordBottomSheet(
                initialRecord = editingRecord,
                defaultMonth = prefillMonth,
                defaultDay = prefillDay,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onDismiss = { showAddRecordSheet = false },
                onSave = { newRecord ->
                    personalRecords = PersonalRecordsRepository.saveRecord(context, newRecord)
                    ZijSchedulerAndNotifications.scheduleAllAlarms(context)
                    showAddRecordSheet = false
                }
            )
        }
    }
}

@Composable
fun CalendarTabScreen(
    today: PersianDate,
    displayedYear: Int,
    displayedMonth: Int,
    selectedDay: Int,
    eventsMap: Map<Pair<Int, Int>, ZijEvent>,
    personalRecords: List<PersonalRecord>,
    isDark: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onChangeYearMonth: (Int, Int) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    onShowExplanation: () -> Unit,
    onAddPersonalForSelectedDay: () -> Unit
) {
    val daysInMonth = PersianDateEngine.getDaysInMonth(displayedYear, displayedMonth)
    val safeSelectedDay = selectedDay.coerceIn(1, daysInMonth)
    val firstDayWeekIndex = PersianDateEngine.getDayOfWeekIndex(displayedYear, displayedMonth, 1)
    val selectedWeekIndex = PersianDateEngine.getDayOfWeekIndex(displayedYear, displayedMonth, safeSelectedDay)
    val selectedEvent = eventsMap[displayedMonth to safeSelectedDay]
    val dayPersonalList = personalRecords.filter { it.month == displayedMonth && it.day == safeSelectedDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // سربرگ زیج با پیشامدگویی پارسی
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "درود • گاه‌شمار ملی زیج",
                    color = TurquoiseAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()} شاهنشاهی",
                    color = primaryText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = PersianDateEngine.formatGregorianEquivalent(displayedYear, displayedMonth, safeSelectedDay),
                    color = secondaryText,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        onChangeYearMonth(today.jalaliYear, today.month)
                        onSelectDay(today.day)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TurquoiseAccent)
                ) {
                    Text("امروز", fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(cardColor)
                        .clickable { onToggleTheme() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isDark) "☀" else "☾", color = JoyGold, fontSize = 18.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // کارت گاهبرگ ماهانه
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (displayedMonth == 1) onChangeYearMonth(displayedYear - 1, 12)
                    else onChangeYearMonth(displayedYear, displayedMonth - 1)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "ماه پیشین",
                        tint = primaryText
                    )
                }

                Text(
                    text = "${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()}",
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                IconButton(onClick = {
                    if (displayedMonth == 12) onChangeYearMonth(displayedYear + 1, 1)
                    else onChangeYearMonth(displayedYear, displayedMonth + 1)
                }) {
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

            val totalSlots = firstDayWeekIndex + daysInMonth
            val rows = (totalSlots + 6) / 7

            for (r in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - firstDayWeekIndex + 1
                        if (dayNum in 1..daysInMonth) {
                            val ev = eventsMap[displayedMonth to dayNum]
                            val hasPersonal = personalRecords.any { it.month == displayedMonth && it.day == dayNum }
                            val isToday = (displayedYear == today.jalaliYear &&
                                    displayedMonth == today.month &&
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendDot("شادمانی", JoyGold, secondaryText)
                LegendDot("اندوه", SorrowCrimson, secondaryText)
                LegendDualDot("ترکیبی", secondaryText)
                LegendDot("جاویدنام‌ها", PureMourningBlack, secondaryText)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // کارت جزئیات روز برگزیده + دکمه توضیحات + ثبت زادروز/رخداد
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${PersianDateEngine.weekDaysFull[selectedWeekIndex]}، ${safeSelectedDay.toPersianDigits()} ${PersianDateEngine.monthNames[displayedMonth - 1]} ${(displayedYear + 1180).toPersianDigits()}",
                    color = secondaryText,
                    fontSize = 13.sp
                )
                TextButton(
                    onClick = onAddPersonalForSelectedDay,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("+ ثبت یادآور در این روز", color = TurquoiseAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (selectedEvent != null) {
                Text(
                    text = selectedEvent.title,
                    color = primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onShowExplanation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TurquoiseAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("توضیحات", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "برای این روز رخداد باستانی یا ملی ثبت نشده است.",
                    color = secondaryText,
                    fontSize = 14.sp
                )
            }

            if (dayPersonalList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = secondaryText.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))
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
                            Text(
                                text = rec.note,
                                color = secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
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
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        isSelected -> TurquoiseAccent
        isToday -> JoyGold
        event?.category == EventCategory.BLACK && isDark -> Color(0xFF666666)
        else -> Color.Transparent
    }

    val cellModifier = modifier
        .aspectRatio(1f)
        .clip(shape)
        .then(
            when (event?.category) {
                EventCategory.JOY -> Modifier.background(JoyGold)
                EventCategory.SORROW -> Modifier.background(SorrowCrimson)
                EventCategory.BLACK -> Modifier.background(PureMourningBlack)
                EventCategory.DUAL -> {
                    // نیمه چپ فیزیکی = عنابیِ اندوه | نیمه راست فیزیکی = طلاییِ شادمانی
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
                    if (isSelected) TurquoiseAccent.copy(alpha = 0.16f) else Color.Transparent
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
    var filterMode by remember { mutableIntStateOf(0) } // 0: همه، 1: زادروزها، 2: رخدادهای شخصی

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
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .padding(bottom = 72.dp)
    ) {
        Text(
            text = "زادروزها و رخدادهای شخصی",
            color = primaryText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "یادآوری هوشمند یک هفته پیش، روز پیش و در نخستین دقیقه بامداد (۰۰:۰۱)",
            color = secondaryText,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                    "با زدن دکمه «ثبت یادآور نو» در پایین صفحه، زادروز عزیزان یا رخدادهای مهم خود را بیفزایید تا زیج به موقع یادآوری کند.",
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
                                text = if (rec.isBirthday) "زادروز ${rec.title}" else rec.title,
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

                    Spacer(modifier = Modifier.height(8.dp))
                    val activeStages = buildList {
                        if (rec.remindWeekBefore) add("یک هفته پیش")
                        if (rec.remindDayBefore) add("روز پیش")
                        if (rec.remindOnDay) add("بامداد همان روز")
                    }
                    Text(
                        text = if (activeStages.isEmpty()) "یادآور غیرفعال" else "یادآورها: ${activeStages.joinToString(" • ")}",
                        color = TurquoiseAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditRecordBottomSheet(
    initialRecord: PersonalRecord?,
    defaultMonth: Int,
    defaultDay: Int,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onSave: (PersonalRecord) -> Unit
) {
    var isBirthday by remember { mutableStateOf(initialRecord?.isBirthday ?: true) }
    var title by remember { mutableStateOf(initialRecord?.title ?: "") }
    var selectedMonth by remember { mutableIntStateOf(initialRecord?.month ?: defaultMonth) }
    var dayText by remember { mutableStateOf((initialRecord?.day ?: defaultDay).toString()) }
    var yearText by remember { mutableStateOf(initialRecord?.birthImperialYear?.toString() ?: "") }
    var note by remember { mutableStateOf(initialRecord?.note ?: "") }

    var remindWeekBefore by remember { mutableStateOf(initialRecord?.remindWeekBefore ?: true) }
    var remindDayBefore by remember { mutableStateOf(initialRecord?.remindDayBefore ?: true) }
    var remindOnDay by remember { mutableStateOf(initialRecord?.remindOnDay ?: true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = if (initialRecord == null) "ثبت زادروز یا رخداد شخصی" else "ویرایش یادآور",
                    color = primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // انتخاب نوع: زادروز یا رخداد شخصی
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = isBirthday,
                        onClick = {
                            isBirthday = true
                            remindWeekBefore = true
                            remindDayBefore = true
                        },
                        label = { Text("🎂 زادروز") }
                    )
                    FilterChip(
                        selected = !isBirthday,
                        onClick = { isBirthday = false },
                        label = { Text("📌 رخداد یا یادداشت شخصی") }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(if (isBirthday) "نام فرد (برای نمونه: سینا)" else "عنوان رخداد یا یادداشت")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("انتخاب ماه:", color = secondaryText, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PersianDateEngine.monthNames.forEachIndexed { index, mName ->
                        val mNum = index + 1
                        FilterChip(
                            selected = selectedMonth == mNum,
                            onClick = { selectedMonth = mNum },
                            label = { Text(mName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.filter { ch -> ch.isDigit() }.take(2) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("روز ماه (۱ تا ۳۱)") }
                    )

                    if (isBirthday) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { ch -> ch.isDigit() }.take(4) },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("سال زایش (اختیاری)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("یادداشت کوتاه یا ایده پیشکش (اختیاری)") }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("زمان‌های فرستادن اعلان یادآور:", color = primaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("یک هفته مانده به تاریخ", color = secondaryText, fontSize = 13.sp)
                    Switch(checked = remindWeekBefore, onCheckedChange = { remindWeekBefore = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("یک روز پیش از تاریخ", color = secondaryText, fontSize = 13.sp)
                    Switch(checked = remindDayBefore, onCheckedChange = { remindDayBefore = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("در نخستین دقیقه همان روز (۰۰:۰۱)", color = secondaryText, fontSize = 13.sp)
                    Switch(checked = remindOnDay, onCheckedChange = { remindOnDay = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        val maxDays = if (selectedMonth <= 6) 31 else 30
                        val parsedDay = (dayText.toIntOrNull() ?: 1).coerceIn(1, maxDays)
                        val rawYear = yearText.toIntOrNull()
                        // تبدیل هوشمند: اگر کاربر سال خورشیدی مانند ۱۳۸۷ وارد کند خودکار به ۲۵۶۷ شاهنشاهی تبدیل می‌شود
                        val normalizedImperialYear = when {
                            rawYear == null -> null
                            rawYear in 1200..1500 -> rawYear + 1180
                            rawYear in 2400..2700 -> rawYear
                            else -> null
                        }

                        onSave(
                            PersonalRecord(
                                id = initialRecord?.id ?: java.util.UUID.randomUUID().toString(),
                                title = title.trim(),
                                isBirthday = isBirthday,
                                month = selectedMonth,
                                day = parsedDay,
                                birthImperialYear = if (isBirthday) normalizedImperialYear else null,
                                note = note.trim(),
                                remindWeekBefore = remindWeekBefore,
                                remindDayBefore = remindDayBefore,
                                remindOnDay = remindOnDay
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TurquoiseAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("ذخیره در گاه‌شمار زیج", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsAndSupportScreen(
    isDark: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDarkChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var statusBarEnabled by remember { mutableStateOf(ZijPreferences.isStatusBarEnabled(context)) }
    var festivalNotifyEnabled by remember { mutableStateOf(ZijPreferences.isFestivalNotifyEnabled(context)) }
    var reminderHour by remember { mutableIntStateOf(ZijPreferences.getReminderHour(context)) }
    var reminderMinute by remember { mutableIntStateOf(ZijPreferences.getReminderMinute(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "ساماندهی و همیاری",
            color = primaryText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        // کارت ساماندهی ظاهر و اعلان‌ها
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
                    Text("خاموش کردن این کلید، نمای روشن استخوانی را فعال می‌کند", color = secondaryText, fontSize = 12.sp)
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
                    Text("نمایش عدد روز در نوار بالای گوشی", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("نمایش همیشگی عدد روز ماه در بخش اعلان بالای صفحه", color = secondaryText, fontSize = 12.sp)
                }
                Switch(
                    checked = statusBarEnabled,
                    onCheckedChange = { enabled ->
                        statusBarEnabled = enabled
                        ZijPreferences.setStatusBarEnabled(context, enabled)
                        StatusBarHelper.updateStatusBarDayNumber(context)
                    }
                )
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

            // تنظیم ساعت یادآور (پیش‌فرض ۰۰:۰۱ بامداد)
            Text("زمان فرستادن یادآورهای روزانه", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val formattedTime = "${reminderHour.toString().padStart(2, '0').toPersianDigits()}:${reminderMinute.toString().padStart(2, '0').toPersianDigits()}"
            Text(
                text = "زمان کنونی: ساعت $formattedTime بامداد (پیش‌فرض: نخستین دقیقه روز ۰۰:۰۱)",
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

        Spacer(modifier = Modifier.height(14.dp))

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
                text = "برای افزودن گاه‌شمار و ساعت زنده به صفحه اصلی گوشی، انگشت خود را روی فضای خالی صفحه اصلی نگه دارید، گزینه «ابزارک‌ها» (Widgets) را بزنید و یکی از دو طرح «زیج — گاه‌شمار تیره» یا «روشن» را برگزینید.",
                color = secondaryText,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // کارت ویژه همیاری با زیج (درگاه دونفا)
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
                Text("همیاری و پشتیبانی از زیج", color = JoyGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "گاه‌شمار ملی «زیج» به صورت رایگان و مستقل برای پاسداشت فرهنگ و جشن‌های ایران‌زمین ساخته شده است. اگر این برنامه برایتان سودمند است، می‌توانید از راه پیوند زیر با ما همیاری کنید:",
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
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(DONOFA_URL))
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "مرورگر یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(containerColor = JoyGold, contentColor = Color(0xFF141824)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("همیاری در دونفا", fontWeight = FontWeight.ExtraBold)
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

        Spacer(modifier = Modifier.height(20.dp))
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
