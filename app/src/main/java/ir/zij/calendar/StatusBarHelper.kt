package ir.zij.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import ir.zij.calendar.PersianDateEngine.toPersianDigits

object StatusBarHelper {

    private const val CHANNEL_ID = "zij_daily_number_channel"
    private const val NOTIFICATION_ID = 2585

    fun updateStatusBarDayNumber(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!ZijPreferences.isStatusBarEnabled(context)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "نمایش عدد روز در نوار بالا",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نمایش همیشگی و بی‌صدای عدد روز گاه‌شمار زیج"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val today = PersianDateEngine.today()
        val dayText = today.day.toPersianDigits()

        val iconBitmap = createDayNumberBitmap(dayText)
        val smallIcon = IconCompat.createWithBitmap(iconBitmap)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(dayText)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // مجوز اعلان هنوز داده نشده است
        }
    }

    private fun createDayNumberBitmap(dayPersian: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = if (dayPersian.length == 1) 76f else 62f
        }

        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(dayPersian, size / 2f, yPos, paint)
        return bitmap
    }
}
