package ir.zij.calendar

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ZijQuickTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        refreshTileState()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                2585,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(openIntent)
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val today = PersianDateEngine.today()
        val localizedDay = PersianDateEngine.formatDayForDeviceLocale(today.day)

        tile.icon = Icon.createWithBitmap(createDayTileBitmap(localizedDay))
        // دقیقاً مطابق دستور کاربر: فقط خودِ عدد روز، سازگار با زبان گوشی
        tile.label = localizedDay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = ""
        }
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        private const val LEGACY_NOTIFICATION_ID = 2585

        /**
         * حذف اعلان پایدار نسخه‌های پیشین و به‌روزرسانی کاشی تنظیمات سریع
         */
        fun syncQuickTileAndClearLegacyNotification(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(LEGACY_NOTIFICATION_ID)
            } catch (_: Exception) {
            }

            try {
                requestListeningState(
                    context,
                    ComponentName(context, ZijQuickTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        /**
         * افزودن تک‌لمسی کاشی عدد روز به پنل تنظیمات سریع در اندروید ۱۳ به بالا
         */
        fun promptAddQuickTile(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sbm = context.getSystemService(StatusBarManager::class.java)
                if (sbm != null) {
                    val today = PersianDateEngine.today()
                    val dayStr = PersianDateEngine.formatDayForDeviceLocale(today.day)
                    val icon = Icon.createWithBitmap(createDayTileBitmap(dayStr))
                    sbm.requestAddTileService(
                        ComponentName(context, ZijQuickTileService::class.java),
                        dayStr,
                        icon,
                        context.mainExecutor
                    ) { result ->
                        when (result) {
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                                Toast.makeText(context, "کاشی عدد روز به نوار تنظیمات سریع افزوده شد", Toast.LENGTH_SHORT).show()
                            }
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                                Toast.makeText(context, "کاشی عدد روز هم‌اکنون در تنظیمات سریع شما فعال است", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                syncQuickTileAndClearLegacyNotification(context)
                            }
                        }
                    }
                    return
                }
            }
            Toast.makeText(
                context,
                "نوار بالای گوشی را پایین بکشید، دکمه مداد (ویرایش) را بزنید و کاشی عدد روز را به بالا بکشید.",
                Toast.LENGTH_LONG
            ).show()
        }

        fun createDayTileBitmap(dayString: String): Bitmap {
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // قاب ظریف گاهبرگ دور عدد برای جلوه تمیز در کنار آیکون حالت پرواز و وای‌فای
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 7f
            }
            canvas.drawRoundRect(RectF(12f, 16f, size - 12f, size - 12f), 24f, 24f, borderPaint)

            // دو حلقه کوچک بالای گاهبرگ
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(36f, 6f, 48f, 24f), 6f, 6f, ringPaint)
            canvas.drawRoundRect(RectF(size - 48f, 6f, size - 36f, 24f), 6f, 6f, ringPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = if (dayString.length == 1) 70f else 56f
            }

            val centerY = (size / 2f) + 6f - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(dayString, size / 2f, centerY, textPaint)
            return bitmap
        }
    }
}
