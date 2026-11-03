package ir.zij.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.zij.calendar.PersianDateEngine.toAsciiDigits
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import java.util.UUID

/**
 * پنجره تمام‌صفحه، فوق‌روان و بدون پرش کیبورد برای ثبت/ویرایش زادروز و رخداد شخصی
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmoothAddOrEditRecordDialog(
    initialRecord: PersonalRecord?,
    defaultMonth: Int,
    defaultDay: Int,
    bgColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onSave: (PersonalRecord) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isBirthday by remember { mutableStateOf(initialRecord?.isBirthday ?: true) }
    var title by remember { mutableStateOf(initialRecord?.title ?: "") }
    var selectedMonth by remember { mutableIntStateOf(initialRecord?.month ?: defaultMonth) }
    var selectedDay by remember { mutableIntStateOf(initialRecord?.day ?: defaultDay) }
    var yearText by remember { mutableStateOf(initialRecord?.birthImperialYear?.toString() ?: "") }
    var note by remember { mutableStateOf(initialRecord?.note ?: "") }
    var titleError by remember { mutableStateOf(false) }

    var remindWeekBefore by remember { mutableStateOf(initialRecord?.remindWeekBefore ?: true) }
    var remindDayBefore by remember { mutableStateOf(initialRecord?.remindDayBefore ?: true) }
    var remindOnDay by remember { mutableStateOf(initialRecord?.remindOnDay ?: true) }

    val maxDays = if (selectedMonth <= 6) 31 else 30
    if (selectedDay > maxDays) selectedDay = maxDays

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
                color = bgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // نوار بالای پنجره
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initialRecord == null) "ثبت زادروز یا رخداد نو" else "ویرایش یادآور",
                            color = primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "بستن", tint = primaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // انتخاب نوع با دو دکمه لمسی بزرگ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardColor)
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(true to "🎂 زادروز", false to "📌 رخداد شخصی").forEach { (bType, label) ->
                            val active = isBirthday == bType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) JoyGold else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isBirthday = bType
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color(0xFF141824) else secondaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // کادر نام یا عنوان با کنتراست کامل در شب و روز
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardColor)
                            .padding(14.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                if (it.isNotBlank()) titleError = false
                            },
                            isError = titleError,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = {
                                Text(if (isBirthday) "نام فرد (برای نمونه: سینا یا پریا)" else "عنوان رخداد یا یادداشت")
                            }
                        )
                        if (titleError) {
                            Text(
                                text = "لطفاً نام یا عنوان را بنویسید",
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("یادداشت کوتاه یا ایده پیشکش (اختیاری)") }
                        )

                        if (isBirthday) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = yearText.toPersianDigits(),
                                onValueChange = { raw ->
                                    yearText = raw.toAsciiDigits().filter { it.isDigit() }.take(4)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("سال زایش به خورشیدی یا شاهنشاهی (اختیاری، مانند ۱۳۸۷)") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // انتخاب لمسی ماه و روز بدون نیاز به کیبورد!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardColor)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "تاریخ برگزیده: ${selectedDay.toPersianDigits()} ${PersianDateEngine.monthNames[selectedMonth - 1]}",
                            color = TurquoiseAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("ماه را لمس کنید:", color = secondaryText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        for (r in 0 until 3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0 until 4) {
                                    val mNum = r * 4 + c + 1
                                    val sel = selectedMonth == mNum
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) JoyGold else primaryText.copy(alpha = 0.06f))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedMonth = mNum
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = PersianDateEngine.monthNames[mNum - 1],
                                            color = if (sel) Color(0xFF141824) else primaryText,
                                            fontSize = 12.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("روز ماه را لمس کنید:", color = secondaryText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        val dayRows = (maxDays + 6) / 7
                        for (r in 0 until dayRows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (c in 0 until 7) {
                                    val dNum = r * 7 + c + 1
                                    if (dNum <= maxDays) {
                                        val sel = selectedDay == dNum
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1.15f)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (sel) TurquoiseAccent else primaryText.copy(alpha = 0.05f))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedDay = dNum
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dNum.toPersianDigits(),
                                                color = if (sel) Color.White else primaryText,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // کلیدهای زمان‌بندی یادآور
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardColor)
                            .padding(14.dp)
                    ) {
                        Text("زمان‌های فرستادن اعلان یادآور:", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))

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
                            Text("نخستین دقیقه همان روز (۰۰:۰۱)", color = secondaryText, fontSize = 13.sp)
                            Switch(checked = remindOnDay, onCheckedChange = { remindOnDay = it })
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            val rawYear = yearText.toAsciiDigits().toIntOrNull()
                            val normalizedImperialYear = when {
                                rawYear == null -> null
                                rawYear in 1200..1500 -> rawYear + 1180
                                rawYear in 2400..2700 -> rawYear
                                else -> null
                            }

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSave(
                                PersonalRecord(
                                    id = initialRecord?.id ?: UUID.randomUUID().toString(),
                                    title = title.trim(),
                                    isBirthday = isBirthday,
                                    month = selectedMonth,
                                    day = selectedDay,
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
                        Text(
                            text = "ذخیره در گاه‌شمار زیج",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * پنجره پرش سریع به هر ماه و سال با لمس عنوان بالای تقویم
 */
@Composable
fun QuickMonthYearJumpDialog(
    currentJalaliYear: Int,
    currentMonth: Int,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onConfirm: (jalaliYear: Int, month: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var tempYear by remember { mutableIntStateOf(currentJalaliYear) }
    var tempMonth by remember { mutableIntStateOf(currentMonth) }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardColor)
                    .border(1.dp, JoyGold.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "پرش سریع به ماه و سال",
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = {
                        tempYear -= 1
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }) {
                        Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(tempYear + 1180).toPersianDigits()} شاهنشاهی",
                            color = JoyGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "برابر با ${tempYear.toPersianDigits()} خورشیدی",
                            color = secondaryText,
                            fontSize = 12.sp
                        )
                    }

                    FilledTonalIconButton(onClick = {
                        tempYear += 1
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                for (r in 0 until 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (c in 0 until 3) {
                            val mNum = r * 3 + c + 1
                            val sel = tempMonth == mNum
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) TurquoiseAccent else primaryText.copy(alpha = 0.06f))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tempMonth = mNum
                                        onConfirm(tempYear, mNum)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = PersianDateEngine.monthNames[mNum - 1],
                                    color = if (sel) Color.White else primaryText,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("بستن", color = secondaryText)
                    }
                    Button(
                        onClick = { onConfirm(tempYear, tempMonth) },
                        colors = ButtonDefaults.buttonColors(containerColor = JoyGold, contentColor = Color(0xFF141824))
                    ) {
                        Text("برو به این ماه", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * پنجره جستجوی آنی جشن‌ها، شخصیت‌ها و زادروزهای شخصی
 */
@Composable
fun InstantSearchDialog(
    eventsMap: Map<Pair<Int, Int>, ZijEvent>,
    personalRecords: List<PersonalRecord>,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onSelectMonthDay: (month: Int, day: Int) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val results = remember(query, eventsMap, personalRecords) {
        val q = query.trim()
        val list = mutableListOf<Triple<Int, Int, String>>()
        personalRecords.forEach { rec ->
            val label = (if (rec.isBirthday) "🎂 زادروز " else "📌 ") + rec.title
            if (q.isEmpty() || label.contains(q) || rec.note.contains(q)) {
                list.add(Triple(rec.month, rec.day, label))
            }
        }
        eventsMap.values.sortedWith(compareBy({ it.month }, { it.day })).forEach { ev ->
            if (q.isEmpty() || ev.title.contains(q) || ev.description.contains(q)) {
                list.add(Triple(ev.month, ev.day, ev.title))
            }
        }
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardColor)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("جستجو در گاه‌شمار زیج", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = primaryText)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TurquoiseAccent) },
                    label = { Text("نام جشن، شخصیت یا زادروز (مانند مهرگان، کوروش...)") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = "موردی با این کلیدواژه یافت نشد.",
                            color = secondaryText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 18.dp)
                        )
                    } else {
                        results.forEach { (m, d, titleText) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primaryText.copy(alpha = 0.05f))
                                    .clickable { onSelectMonthDay(m, d) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = titleText,
                                    color = primaryText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${d.toPersianDigits()} ${PersianDateEngine.monthNames[m - 1]}",
                                    color = TurquoiseAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
