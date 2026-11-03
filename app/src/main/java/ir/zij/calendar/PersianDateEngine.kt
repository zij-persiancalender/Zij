package ir.zij.calendar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class EventCategory { JOY, SORROW, DUAL, BLACK }

data class ZijEvent(
    val month: Int,
    val day: Int,
    val title: String,
    val category: EventCategory,
    val description: String
)

data class PersianDate(
    val jalaliYear: Int,
    val month: Int,
    val day: Int
) {
    val imperialYear: Int get() = jalaliYear + 1180
}

data class YearStats(
    val diffFromToday: Int,     // مثبت: مانده، منفی: گذشته، صفر: امروز
    val daysPassedInYear: Int,  // روز سپری‌شده از آغاز سال
    val daysLeftInYear: Int,    // روز مانده تا سال نو
    val totalDaysInYear: Int    // ۳۶۵ یا ۳۶۶
)

data class ExactDateSpan(
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val isEndBeforeStart: Boolean
)

object PersianDateEngine {

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "امرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val gregorianMonthNamesPersian = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    val weekDaysShort = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val weekDaysFull = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private const val REMOTE_EVENTS_URL = "https://raw.githubusercontent.com/YOUR_USERNAME/Zij/main/app/src/main/assets/zij_events.json"
    private const val CACHE_PREFS = "zij_events_cache"
    private const val KEY_JSON = "cached_events_json"

    fun Int.toPersianDigits(): String = this.toString().toPersianDigits()
    fun Long.toPersianDigits(): String = this.toString().toPersianDigits()

    fun String.toPersianDigits(): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder(this.length)
        for (ch in this) {
            when (ch) {
                in '0'..'9' -> sb.append(persianDigits[ch - '0'])
                in '٠'..'٩' -> sb.append(persianDigits[ch - '٠'])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun String.toAsciiDigits(): String {
        val sb = StringBuilder(this.length)
        for (ch in this) {
            when (ch) {
                in '۰'..'۹' -> sb.append(('0'.code + (ch - '۰')).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch - '٠')).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun today(): PersianDate {
        val now = LocalDate.now()
        return gregorianToJalali(now.year, now.monthValue, now.dayOfMonth)
    }

    fun getDaysInMonth(jalaliYear: Int, month: Int): Int {
        return when {
            month in 1..6 -> 31
            month in 7..11 -> 30
            else -> {
                val startNextYear = jalaliToGregorian(jalaliYear + 1, 1, 1)
                val startEsfand = jalaliToGregorian(jalaliYear, 12, 1)
                ChronoUnit.DAYS.between(startEsfand, startNextYear).toInt()
            }
        }
    }

    fun getDaysInGregorianMonth(gy: Int, gm: Int): Int {
        val safeYear = gy.coerceIn(1, 9999)
        val safeMonth = gm.coerceIn(1, 12)
        return YearMonth.of(safeYear, safeMonth).lengthOfMonth()
    }

    fun getDayOfWeekIndex(jalaliYear: Int, month: Int, day: Int): Int {
        val safeDay = day.coerceIn(1, getDaysInMonth(jalaliYear, month))
        val gDate = jalaliToGregorian(jalaliYear, month, safeDay)
        return (gDate.dayOfWeek.value + 1) % 7
    }

    fun formatGregorianEnglish(jalaliYear: Int, month: Int, day: Int): String {
        val safeDay = day.coerceIn(1, getDaysInMonth(jalaliYear, month))
        val gDate = jalaliToGregorian(jalaliYear, month, safeDay)
        val mName = gDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return "${gDate.dayOfMonth} $mName ${gDate.year}"
    }

    fun formatGregorianPersian(jalaliYear: Int, month: Int, day: Int): String {
        val safeDay = day.coerceIn(1, getDaysInMonth(jalaliYear, month))
        val gDate = jalaliToGregorian(jalaliYear, month, safeDay)
        val mPersian = gregorianMonthNamesPersian[gDate.monthValue - 1]
        return "${gDate.dayOfMonth.toPersianDigits()} $mPersian ${gDate.year.toPersianDigits()}"
    }

    fun computeYearStats(todayDate: PersianDate, selectedYear: Int, selectedMonth: Int, selectedDay: Int): YearStats {
        val safeDay = selectedDay.coerceIn(1, getDaysInMonth(selectedYear, selectedMonth))
        val todayGreg = jalaliToGregorian(todayDate.jalaliYear, todayDate.month, todayDate.day)
        val selectedGreg = jalaliToGregorian(selectedYear, selectedMonth, safeDay)

        val diffFromToday = ChronoUnit.DAYS.between(todayGreg, selectedGreg).toInt()
        val totalDaysInYear = 336 + getDaysInMonth(selectedYear, 12)
        val dayOfYear = if (selectedMonth <= 6) {
            (selectedMonth - 1) * 31 + safeDay
        } else {
            186 + (selectedMonth - 7) * 30 + safeDay
        }
        val daysLeftInYear = (totalDaysInYear - dayOfYear).coerceAtLeast(0)

        return YearStats(
            diffFromToday = diffFromToday,
            daysPassedInYear = dayOfYear,
            daysLeftInYear = daysLeftInYear,
            totalDaysInYear = totalDaysInYear
        )
    }

    fun calculateExactSpan(start: LocalDate, end: LocalDate): ExactDateSpan {
        val isReversed = end.isBefore(start)
        val d1 = if (isReversed) end else start
        val d2 = if (isReversed) start else end
        val period = Period.between(d1, d2)
        val totalDays = abs(ChronoUnit.DAYS.between(d1, d2))
        return ExactDateSpan(
            years = period.years,
            months = period.months,
            days = period.days,
            totalDays = totalDays,
            isEndBeforeStart = isReversed
        )
    }

    fun daysUntilNextAnnualDate(from: PersianDate, targetMonth: Int, targetDay: Int): Int {
        val todayGreg = jalaliToGregorian(from.jalaliYear, from.month, from.day)
        val safeDayThisYear = targetDay.coerceIn(1, getDaysInMonth(from.jalaliYear, targetMonth))
        val targetThisYear = jalaliToGregorian(from.jalaliYear, targetMonth, safeDayThisYear)
        return if (!targetThisYear.isBefore(todayGreg)) {
            ChronoUnit.DAYS.between(todayGreg, targetThisYear).toInt()
        } else {
            val nextYear = from.jalaliYear + 1
            val safeDayNextYear = targetDay.coerceIn(1, getDaysInMonth(nextYear, targetMonth))
            val targetNextYear = jalaliToGregorian(nextYear, targetMonth, safeDayNextYear)
            ChronoUnit.DAYS.between(todayGreg, targetNextYear).toInt()
        }
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): PersianDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return PersianDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val jy1 = jy + 1595
        var days = -355668 + (365 * jy1) + ((jy1 / 33) * 8) + (((jy1 % 33) + 3) / 4) + jd +
                (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return LocalDate.of(gy.coerceIn(1, 9999), gm.coerceIn(1, 12), gd.coerceIn(1, 31))
    }

    fun loadEventsOfflineFirst(context: Context): Map<Pair<Int, Int>, ZijEvent> {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_JSON, null)
        val rawJson = cachedJson ?: context.assets.open("zij_events.json").bufferedReader().use { it.readText() }
        return parseEventsJson(rawJson)
    }

    suspend fun syncEventsOnlineIfAvailable(context: Context): Map<Pair<Int, Int>, ZijEvent>? {
        return withContext(Dispatchers.IO) {
            try {
                if (REMOTE_EVENTS_URL.contains("YOUR_USERNAME")) return@withContext null
                val conn = (URL(REMOTE_EVENTS_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val freshJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseEventsJson(freshJson)
                    if (parsed.isNotEmpty()) {
                        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_JSON, freshJson).apply()
                        return@withContext parsed
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseEventsJson(jsonStr: String): Map<Pair<Int, Int>, ZijEvent> {
        val map = mutableMapOf<Pair<Int, Int>, ZijEvent>()
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val m = obj.getInt("month")
            val d = obj.getInt("day")
            val title = obj.getString("title")
            val desc = obj.getString("description")
            val cat = when (obj.getString("type")) {
                "sorrow" -> EventCategory.SORROW
                "dual" -> EventCategory.DUAL
                "black" -> EventCategory.BLACK
                else -> EventCategory.JOY
            }
            map[m to d] = ZijEvent(m, d, title, cat, desc)
        }
        return map
    }
}
