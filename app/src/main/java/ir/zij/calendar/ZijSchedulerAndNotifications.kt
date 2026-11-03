package ir.zij.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import java.util.Calendar

object ZijSchedulerAndNotifications {

    const val ACTION_MIDNIGHT_REFRESH = "ir.zij.calendar.ACTION_MIDNIGHT_REFRESH"
    const val ACTION_DAILY_REMINDER = "ir.zij.calendar.ACTION_DAILY_REMINDER"

    private const val REMINDER_CHANNEL_ID = "zij_reminders_and_festivals"
    private const val REQ_MIDNIGHT = 3001
    private const val REQ_REMINDER = 3002

    fun scheduleAllAlarms(context: Context) {
        scheduleMidnightRefresh(context)
        scheduleDailyReminder(context)
    }

    private fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = ACTION_MIDNIGHT_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 2)
            set(Calendar.MILLISECOND, 0)
        }
        setSafeAlarm(alarmManager, cal.timeInMillis, pi)
    }

    fun scheduleDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQ_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hour = ZijPreferences.getReminderHour(context)
        val minute = ZijPreferences.getReminderMinute(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        setSafeAlarm(alarmManager, target.timeInMillis, pi)
    }

    private fun setSafeAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun evaluateAndSendDailyReminders(context: Context, forceTest: Boolean = false) {
        val today = PersianDateEngine.today()
        val todayKey = "${today.imperialYear}-${today.month}-${today.day}"

        if (!forceTest && ZijPreferences.getLastNotifiedDateKey(context) == todayKey) {
            return
        }

        ensureReminderChannel(context)
        var notifIndex = 4100

        val records = PersonalRecordsRepository.loadAll(context)
        for (rec in records) {
            val daysLeft = PersianDateEngine.daysUntilNextAnnualDate(today, rec.month, rec.day)
            when {
                daysLeft == 7 && rec.remindWeekBefore -> {
                    val title = if (rec.isBirthday) "یادآور زادروز (یک هفته مانده)" else "یادآور رخداد (یک هفته مانده)"
                    val body = if (rec.isBirthday) {
                        "یک هفته تا زادروز «${rec.title}» مانده است؛ زمان خوبی برای تهیه پیشکش است!"
                    } else {
                        "یک هفته تا رخداد «${rec.title}» (${rec.day.toPersianDigits()} ${PersianDateEngine.monthNames[rec.month - 1]}) مانده است."
                    }
                    sendAlertNotification(context, notifIndex++, title, body)
                }
                daysLeft == 1 && rec.remindDayBefore -> {
                    val title = if (rec.isBirthday) "فردا زادروز است!" else "یادآور رخداد فردا"
                    val body = if (rec.isBirthday) {
                        "فردا زادروز «${rec.title}» است؛ شادباش یادت نره!"
                    } else {
                        "فردا زمان رخداد «${rec.title}» است."
                    }
                    sendAlertNotification(context, notifIndex++, title, body)
                }
                daysLeft == 0 && rec.remindOnDay -> {
                    val title = if (rec.isBirthday) "شادباش زادروز!" else "رخداد امروز در زیج"
                    val ageNote = if (rec.isBirthday && rec.birthImperialYear != null && today.imperialYear > rec.birthImperialYear) {
                        " (${(today.imperialYear - rec.birthImperialYear).toPersianDigits()} سالگی)"
                    } else ""
                    val body = if (rec.isBirthday) {
                        "امروز زادروز «${rec.title}»$ageNote است؛ شادباش یادت نره!"
                    } else {
                        "امروز: ${rec.title}" + if (rec.note.isNotBlank()) " — ${rec.note}" else ""
                    }
                    sendAlertNotification(context, notifIndex++, title, body)
                }
            }
        }

        if (ZijPreferences.isFestivalNotifyEnabled(context)) {
            val events = PersianDateEngine.loadEventsOfflineFirst(context)
            val todayEvent = events[today.month to today.day]
            if (todayEvent != null && (todayEvent.category == EventCategory.JOY || todayEvent.category == EventCategory.DUAL)) {
                sendAlertNotification(
                    context,
                    notifIndex++,
                    "Zij | زیج • ${today.day.toPersianDigits()} ${PersianDateEngine.monthNames[today.month - 1]}",
                    "فرخنده باد: ${todayEvent.title}"
                )
            }
        }

        if (forceTest && notifIndex == 4100) {
            sendAlertNotification(
                context,
                4099,
                "آزمایش یادآور زیج",
                "درود! سامانه اعلان زادروزها و جشن‌های زیج به درستی کار می‌کند."
            )
        }

        if (!forceTest) {
            ZijPreferences.setLastNotifiedDateKey(context, todayKey)
        }
    }

    private fun ensureReminderChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "یادآور زادروزها و جشن‌های ملی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان هوشمند زادروزها، رخدادهای شخصی و جشن‌های ایرانی"
        }
        nm.createNotificationChannel(channel)
    }

    private fun sendAlertNotification(context: Context, id: Int, title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zij_tile_default)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        try {
            nm.notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
