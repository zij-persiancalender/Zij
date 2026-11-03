package ir.zij.calendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ir.zij.calendar.PersianDateEngine.toPersianDigits

object ZijWidgetUpdater {

    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val darkIds = manager.getAppWidgetIds(ComponentName(context, ZijDarkWidgetProvider::class.java))
        for (id in darkIds) {
            updateSingleWidget(context, manager, id, isDark = true)
        }

        val lightIds = manager.getAppWidgetIds(ComponentName(context, ZijLightWidgetProvider::class.java))
        for (id in lightIds) {
            updateSingleWidget(context, manager, id, isDark = false)
        }
    }

    fun updateSingleWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        isDark: Boolean
    ) {
        val layoutRes = if (isDark) R.layout.widget_zij_dark else R.layout.widget_zij_light
        val views = RemoteViews(context.packageName, layoutRes)

        val today = PersianDateEngine.today()
        val weekDayName = PersianDateEngine.weekDaysFull[
            PersianDateEngine.getDayOfWeekIndex(today.jalaliYear, today.month, today.day)
        ]
        val persianFull = "${today.day.toPersianDigits()} ${PersianDateEngine.monthNames[today.month - 1]} ${today.imperialYear.toPersianDigits()} شاهنشاهی"
        val gregorianFull = "${PersianDateEngine.formatGregorianPersian(today.jalaliYear, today.month, today.day)} میلادی"

        val eventsMap = PersianDateEngine.loadEventsOfflineFirst(context)
        val nationalEvent = eventsMap[today.month to today.day]
        val personalToday = PersonalRecordsRepository.loadAll(context)
            .filter { it.month == today.month && it.day == today.day }

        val occasionText = when {
            personalToday.isNotEmpty() && nationalEvent != null -> {
                val p = personalToday.first()
                val prefix = if (p.isBirthday) "زادروز ${p.title}" else p.title
                "$prefix • ${nationalEvent.title}"
            }
            personalToday.isNotEmpty() -> {
                val p = personalToday.first()
                if (p.isBirthday) "امروز: زادروز ${p.title}" else "امروز: ${p.title}"
            }
            nationalEvent != null -> nationalEvent.title
            else -> "روز خوش • گاه‌شمار ملی زیج"
        }

        views.setTextViewText(R.id.widget_weekday, weekDayName)
        views.setTextViewText(R.id.widget_persian_date, persianFull)
        views.setTextViewText(R.id.widget_gregorian_date, gregorianFull)
        views.setTextViewText(R.id.widget_occasion, occasionText)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            appWidgetId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        manager.updateAppWidget(appWidgetId, views)
    }
}

class ZijDarkWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            ZijWidgetUpdater.updateSingleWidget(context, appWidgetManager, id, isDark = true)
        }
    }
}

class ZijLightWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            ZijWidgetUpdater.updateSingleWidget(context, appWidgetManager, id, isDark = false)
        }
    }
}
