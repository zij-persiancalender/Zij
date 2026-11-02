package ir.zij.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        StatusBarHelper.updateStatusBarDayNumber(context)
        ZijWidgetUpdater.updateAllWidgets(context)
        ZijSchedulerAndNotifications.scheduleAllAlarms(context)

        if (intent?.action == ZijSchedulerAndNotifications.ACTION_DAILY_REMINDER ||
            intent?.action == Intent.ACTION_DATE_CHANGED
        ) {
            ZijSchedulerAndNotifications.evaluateAndSendDailyReminders(context, forceTest = false)
        }
    }
}
