package ir.zij.calendar

import android.content.Context

object ZijPreferences {
    private const val PREFS_NAME = "zij_user_settings"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_FESTIVAL_NOTIFY = "festival_notifications"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"
    private const val KEY_LAST_REMINDER_DATE = "last_reminder_date_key"
    private const val KEY_CUSTOM_REPO_BASE = "custom_repo_base_url"

    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isFestivalNotifyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FESTIVAL_NOTIFY, true)

    fun setFestivalNotifyEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FESTIVAL_NOTIFY, enabled).apply()
    }

    fun getReminderHour(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_REMINDER_HOUR, 0)

    fun getReminderMinute(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_REMINDER_MINUTE, 1)

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REMINDER_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_REMINDER_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun getLastNotifiedDateKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_REMINDER_DATE, null)

    fun setLastNotifiedDateKey(context: Context, dateKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_REMINDER_DATE, dateKey).apply()
    }

    fun getCustomRepoBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_REPO_BASE, "") ?: ""

    fun setCustomRepoBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_REPO_BASE, url.trim()).apply()
    }
}
