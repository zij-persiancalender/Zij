package ir.zij.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.zij.calendar.PersianDateEngine.toAsciiDigits
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import java.time.LocalDate

enum class CalendarSystemType(val title: String) {
    IMPERIAL("شاهنشاهی"),
    SOLAR("خورشیدی"),
    GREGORIAN("میلادی")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterAndDiffScreen(
    today: PersianDate,
    eventsMap: Map<Pair<Int, Int>, ZijEvent>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onJumpToCalendarDate: (jalaliYear: Int, month: Int, day: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var modeTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardColor)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("برگردان تاریخ", "فاصله دو تاریخ و سن").forEachIndexed { idx, label ->
                val selected = modeTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) TurquoiseAccent else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            modeTab = idx
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else secondaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(targetState = modeTab, label = "converter_mode_anim") { currentMode ->
            if (currentMode == 0) {
                DateConverterSection(
                    today = today,
                    eventsMap = eventsMap,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onJumpToCalendarDate = onJumpToCalendarDate
                )
            } else {
                ExactAgeAndDiffSection(
                    today = today,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateConverterSection(
    today: PersianDate,
    eventsMap: Map<Pair<Int, Int>, ZijEvent>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onJumpToCalendarDate: (Int, Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val todayGreg = remember(today) {
        PersianDateEngine.jalaliToGregorian(today.jalaliYear, today.month, today.day)
    }

    var sourceSystem by remember { mutableStateOf(CalendarSystemType.SOLAR) }
    var yearInput by remember { mutableStateOf(today.jalaliYear.toString()) }
    var selectedMonth by remember { mutableIntStateOf(today.month) }
    var selectedDay by remember { mutableIntStateOf(today.day) }

    fun switchSystem(newSystem: CalendarSystemType) {
        if (newSystem == sourceSystem) return
        val currentGreg = resolveToGregorian(sourceSystem, yearInput, selectedMonth, selectedDay, todayGreg)
        val currentPersian = PersianDateEngine.gregorianToJalali(currentGreg.year, currentGreg.monthValue, currentGreg.dayOfMonth)
        sourceSystem = newSystem
        when (newSystem) {
            CalendarSystemType.IMPERIAL -> {
                yearInput = currentPersian.imperialYear.toString()
                selectedMonth = currentPersian.month
                selectedDay = currentPersian.day
            }
            CalendarSystemType.SOLAR -> {
                yearInput = currentPersian.jalaliYear.toString()
                selectedMonth = currentPersian.month
                selectedDay = currentPersian.day
            }
            CalendarSystemType.GREGORIAN -> {
                yearInput = currentGreg.year.toString()
                selectedMonth = currentGreg.monthValue
                selectedDay = currentGreg.dayOfMonth
            }
        }
    }

    val resolvedGreg = resolveToGregorian(sourceSystem, yearInput, selectedMonth, selectedDay, todayGreg)
    val resolvedPersian = PersianDateEngine.gregorianToJalali(
        resolvedGreg.year,
        resolvedGreg.monthValue,
        resolvedGreg.dayOfMonth
    )
    val weekDayIndex = PersianDateEngine.getDayOfWeekIndex(
        resolvedPersian.jalaliYear,
        resolvedPersian.month,
        resolvedPersian.day
    )
    val maxDaysInCurrent = when (sourceSystem) {
        CalendarSystemType.GREGORIAN -> {
            val gy = yearInput.toAsciiDigits().toIntOrNull()?.coerceIn(1, 9999) ?: todayGreg.year
            PersianDateEngine.getDaysInGregorianMonth(gy, selectedMonth)
        }
        else -> {
            val rawY = yearInput.toAsciiDigits().toIntOrNull() ?: today.jalaliYear
            val jy = if (sourceSystem == CalendarSystemType.IMPERIAL) rawY - 1180 else rawY
            PersianDateEngine.getDaysInMonth(jy.coerceIn(1, 3000), selectedMonth)
        }
    }
    if (selectedDay > maxDaysInCurrent) selectedDay = maxDaysInCurrent

    val matchedEvent = eventsMap[resolvedPersian.month to resolvedPersian.day]

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Text(
                text = "گاه‌شمار مبدا را برگزینید:",
                color = secondaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalendarSystemType.entries.forEach { sys ->
                    FilterChip(
                        selected = sourceSystem == sys,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            switchSystem(sys)
                        },
                        label = { Text(sys.title, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(onClick = {
                    val cur = yearInput.toAsciiDigits().toIntOrNull() ?: 1405
                    yearInput = (cur - 1).toString()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = yearInput.toPersianDigits(),
                    onValueChange = { raw ->
                        val digits = raw.toAsciiDigits().filter { it.isDigit() }.take(4)
                        yearInput = digits
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("سال (${sourceSystem.title})") }
                )

                FilledTonalIconButton(onClick = {
                    val cur = yearInput.toAsciiDigits().toIntOrNull() ?: 1405
                    yearInput = (cur + 1).toString()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("ماه:", color = secondaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val monthList = if (sourceSystem == CalendarSystemType.GREGORIAN) {
                PersianDateEngine.gregorianMonthNamesPersian
            } else {
                PersianDateEngine.monthNames
            }

            for (rowIdx in 0 until 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (colIdx in 0 until 4) {
                        val mNum = rowIdx * 4 + colIdx + 1
                        val isSel = selectedMonth == mNum
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) JoyGold else primaryText.copy(alpha = 0.06f))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedMonth = mNum
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = monthList[mNum - 1],
                                color = if (isSel) Color(0xFF141824) else primaryText,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("روز ماه:", color = secondaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val dayRows = (maxDaysInCurrent + 6) / 7
            for (r in 0 until dayRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until 7) {
                        val dNum = r * 7 + c + 1
                        if (dNum <= maxDaysInCurrent) {
                            val isSel = selectedDay == dNum
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.15f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSel) TurquoiseAccent else primaryText.copy(alpha = 0.05f))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDay = dNum
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dNum.toPersianDigits(),
                                    color = if (isSel) Color.White else primaryText,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, JoyGold.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "برابرهای دقیق تاریخ (${PersianDateEngine.weekDaysFull[weekDayIndex]})",
                color = TurquoiseAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            EquivalentRowItem(
                label = "گاه‌شمار شاهنشاهی",
                value = "${resolvedPersian.day.toPersianDigits()} ${PersianDateEngine.monthNames[resolvedPersian.month - 1]} ${resolvedPersian.imperialYear.toPersianDigits()}",
                accent = JoyGold,
                primaryText = primaryText
            )

            EquivalentRowItem(
                label = "گاه‌شمار خورشیدی",
                value = "${resolvedPersian.day.toPersianDigits()} ${PersianDateEngine.monthNames[resolvedPersian.month - 1]} ${resolvedPersian.jalaliYear.toPersianDigits()}",
                accent = TurquoiseAccent,
                primaryText = primaryText
            )

            EquivalentRowItem(
                label = "گاه‌شمار میلادی",
                value = "${PersianDateEngine.formatGregorianPersian(resolvedPersian.jalaliYear, resolvedPersian.month, resolvedPersian.day)} (${PersianDateEngine.formatGregorianEnglish(resolvedPersian.jalaliYear, resolvedPersian.month, resolvedPersian.day)})",
                accent = secondaryText,
                primaryText = primaryText
            )

            if (matchedEvent != null) {
                HorizontalDivider(color = secondaryText.copy(alpha = 0.2f))
                Text(
                    text = "رخداد ملی این روز: ${matchedEvent.title}",
                    color = JoyGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = {
                    onJumpToCalendarDate(
                        resolvedPersian.jalaliYear,
                        resolvedPersian.month,
                        resolvedPersian.day
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TurquoiseAccent, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("نمایش این روز در گاه‌شمار اصلی", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EquivalentRowItem(
    label: String,
    value: String,
    accent: Color,
    primaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(primaryText.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExactAgeAndDiffSection(
    today: PersianDate,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    var startSolarYear by remember { mutableStateOf("1386") }
    var startMonth by remember { mutableIntStateOf(1) }
    var startDay by remember { mutableStateOf("1") }

    var endSolarYear by remember { mutableStateOf(today.jalaliYear.toString()) }
    var endMonth by remember { mutableIntStateOf(today.month) }
    var endDay by remember { mutableStateOf(today.day.toString()) }

    val startGreg = remember(startSolarYear, startMonth, startDay) {
        val rawY = startSolarYear.toAsciiDigits().toIntOrNull() ?: 1386
        val jy = if (rawY >= 2200) rawY - 1180 else rawY
        val safeY = jy.coerceIn(1, 3000)
        val maxD = PersianDateEngine.getDaysInMonth(safeY, startMonth)
        val d = (startDay.toAsciiDigits().toIntOrNull() ?: 1).coerceIn(1, maxD)
        PersianDateEngine.jalaliToGregorian(safeY, startMonth, d)
    }

    val endGreg = remember(endSolarYear, endMonth, endDay) {
        val rawY = endSolarYear.toAsciiDigits().toIntOrNull() ?: today.jalaliYear
        val jy = if (rawY >= 2200) rawY - 1180 else rawY
        val safeY = jy.coerceIn(1, 3000)
        val maxD = PersianDateEngine.getDaysInMonth(safeY, endMonth)
        val d = (endDay.toAsciiDigits().toIntOrNull() ?: 1).coerceIn(1, maxD)
        PersianDateEngine.jalaliToGregorian(safeY, endMonth, d)
    }

    val span = remember(startGreg, endGreg) {
        PersianDateEngine.calculateExactSpan(startGreg, endGreg)
    }

    val animYears by animateIntAsState(
        targetValue = span.years,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "span_y"
    )
    val animMonths by animateIntAsState(
        targetValue = span.months,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "span_m"
    )
    val animDays by animateIntAsState(
        targetValue = span.days,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "span_d"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CompactPersianDateInputCard(
            heading = "تاریخ آغاز (برای نمونه: زادروز به خورشیدی یا شاهنشاهی)",
            yearText = startSolarYear,
            selectedMonth = startMonth,
            dayText = startDay,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onYearChange = { startSolarYear = it },
            onMonthChange = { startMonth = it },
            onDayChange = { startDay = it },
            onSetToday = null
        )

        CompactPersianDateInputCard(
            heading = "تاریخ پایان (پیش‌فرض: امروز)",
            yearText = endSolarYear,
            selectedMonth = endMonth,
            dayText = endDay,
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onYearChange = { endSolarYear = it },
            onMonthChange = { endMonth = it },
            onDayChange = { endDay = it },
            onSetToday = {
                endSolarYear = today.jalaliYear.toString()
                endMonth = today.month
                endDay = today.day.toString()
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .border(1.dp, TurquoiseAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "فاصله دقیق زمانی / سن سپری‌شده",
                color = TurquoiseAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpanCounterBox(value = animYears.toPersianDigits(), unit = "سال", primaryText = primaryText, accent = JoyGold)
                SpanCounterBox(value = animMonths.toPersianDigits(), unit = "ماه", primaryText = primaryText, accent = TurquoiseAccent)
                SpanCounterBox(value = animDays.toPersianDigits(), unit = "روز", primaryText = primaryText, accent = JoyGold)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = secondaryText.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "مجموع کل روزها: ${span.totalDays.toPersianDigits()} روز",
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun SpanCounterBox(value: String, unit: String, primaryText: Color, accent: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(primaryText.copy(alpha = 0.06f))
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = unit, color = primaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CompactPersianDateInputCard(
    heading: String,
    yearText: String,
    selectedMonth: Int,
    dayText: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onYearChange: (String) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (String) -> Unit,
    onSetToday: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = heading, color = secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (onSetToday != null) {
                TextButton(onClick = onSetToday) {
                    Text("امروز", color = TurquoiseAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dayText.toPersianDigits(),
                onValueChange = { onDayChange(it.toAsciiDigits().filter { ch -> ch.isDigit() }.take(2)) },
                modifier = Modifier.weight(0.8f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("روز") }
            )
            OutlinedTextField(
                value = yearText.toPersianDigits(),
                onValueChange = { onYearChange(it.toAsciiDigits().filter { ch -> ch.isDigit() }.take(4)) },
                modifier = Modifier.weight(1.2f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("سال (۱۳۸۷ یا ۲۵۶۷)") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        for (r in 0 until 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (c in 0 until 6) {
                    val m = r * 6 + c + 1
                    val sel = selectedMonth == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) TurquoiseAccent else primaryText.copy(alpha = 0.05f))
                            .clickable { onMonthChange(m) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = PersianDateEngine.monthNames[m - 1],
                            color = if (sel) Color.White else primaryText,
                            fontSize = 10.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun resolveToGregorian(
    system: CalendarSystemType,
    yearInput: String,
    month: Int,
    day: Int,
    fallbackGreg: LocalDate
): LocalDate {
    val parsedYear = yearInput.toAsciiDigits().toIntOrNull() ?: return fallbackGreg
    return try {
        when (system) {
            CalendarSystemType.IMPERIAL -> {
                val jy = (parsedYear - 1180).coerceIn(1, 3000)
                val safeM = month.coerceIn(1, 12)
                val safeD = day.coerceIn(1, PersianDateEngine.getDaysInMonth(jy, safeM))
                PersianDateEngine.jalaliToGregorian(jy, safeM, safeD)
            }
            CalendarSystemType.SOLAR -> {
                val jy = parsedYear.coerceIn(1, 3000)
                val safeM = month.coerceIn(1, 12)
                val safeD = day.coerceIn(1, PersianDateEngine.getDaysInMonth(jy, safeM))
                PersianDateEngine.jalaliToGregorian(jy, safeM, safeD)
            }
            CalendarSystemType.GREGORIAN -> {
                val gy = parsedYear.coerceIn(1, 9999)
                val safeM = month.coerceIn(1, 12)
                val safeD = day.coerceIn(1, PersianDateEngine.getDaysInGregorianMonth(gy, safeM))
                LocalDate.of(gy, safeM, safeD)
            }
        }
    } catch (_: Exception) {
        fallbackGreg
    }
}
