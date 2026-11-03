package ir.zij.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import java.io.File
import java.io.FileOutputStream

object StoryCardShareHelper {

    fun shareOccasionStoryCard(
        context: Context,
        jalaliYear: Int,
        month: Int,
        day: Int,
        event: ZijEvent
    ) {
        try {
            val width = 1080
            val height = 1920
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // پس‌زمینه لاجوردی عمیق با گرادیان سلطنتی
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(
                        Color.parseColor("#070D1E"),
                        Color.parseColor("#0B132B"),
                        Color.parseColor("#14244B")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // هاله نوری ظریف در مرکز بالا
            val glowColor = when (event.category) {
                EventCategory.SORROW -> Color.parseColor("#446E1423")
                EventCategory.BLACK -> Color.parseColor("#55000000")
                else -> Color.parseColor("#33D4AF37")
            }
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glowColor
            }
            canvas.drawCircle(width / 2f, 440f, 340f, glowPaint)

            // قاب دوبل طلایی و فیروزه‌ای ایرانی
            val outerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.parseColor("#99D4AF37")
            }
            canvas.drawRoundRect(RectF(56f, 56f, width - 56f, height - 56f), 42f, 42f, outerFramePaint)

            val innerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#6600A896")
            }
            canvas.drawRoundRect(RectF(76f, 76f, width - 76f, height - 76f), 32f, 32f, innerFramePaint)

            // سربرگ بالا
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00A896")
                textAlign = Paint.Align.CENTER
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("گاه‌شمار ملی ایرانی • زیج", width / 2f, 185f, badgePaint)

            // عدد روز بزرگ و تاریخ شاهنشاهی
            val accentHex = when (event.category) {
                EventCategory.SORROW -> "#C94A5E"
                EventCategory.BLACK -> "#CBD5E1"
                else -> "#E2B93B"
            }

            val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentHex)
                textAlign = Paint.Align.CENTER
                textSize = 180f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(day.toPersianDigits(), width / 2f, 410f, dayNumPaint)

            val monthYearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 58f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val monthName = PersianDateEngine.monthNames[month - 1]
            val impYear = (jalaliYear + 1180).toPersianDigits()
            canvas.drawText("$monthName $impYear شاهنشاهی", width / 2f, 505f, monthYearPaint)

            val subDatesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A0AEC0")
                textAlign = Paint.Align.CENTER
                textSize = 32f
            }
            val solarStr = "${day.toPersianDigits()} $monthName ${jalaliYear.toPersianDigits()} خورشیدی"
            val gregStr = "${PersianDateEngine.formatGregorianPersian(jalaliYear, month, day)} میلادی"
            canvas.drawText("$solarStr  •  $gregStr", width / 2f, 570f, subDatesPaint)

            // کارت میانی عنوان و توضیحات رخداد
            val cardRect = RectF(108f, 650f, width - 108f, 1590f)
            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CC162244")
            }
            canvas.drawRoundRect(cardRect, 40f, 40f, cardBgPaint)

            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.parseColor(accentHex)
            }
            canvas.drawRoundRect(cardRect, 40f, 40f, cardBorderPaint)

            // عنوان رخداد (چندخطی خودکار)
            val textWidth = (cardRect.width() - 120f).toInt()
            val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(accentHex)
                textSize = 52f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleLayout = StaticLayout.Builder
                .obtain(event.title, 0, event.title.length, titleTextPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(10f, 1.15f)
                .build()

            canvas.save()
            canvas.translate(cardRect.left + 60f, cardRect.top + 70f)
            titleLayout.draw(canvas)
            canvas.restore()

            val dividerY = cardRect.top + 95f + titleLayout.height + 24f
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#44D4AF37")
                strokeWidth = 2f
            }
            canvas.drawLine(cardRect.left + 120f, dividerY, cardRect.right - 120f, dividerY, divPaint)

            // متن توضیحات
            val descTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F1F5F9")
                textSize = 39f
            }
            val descLayout = StaticLayout.Builder
                .obtain(event.description, 0, event.description.length, descTextPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(14f, 1.25f)
                .build()

            canvas.save()
            canvas.translate(cardRect.left + 60f, dividerY + 42f)
            descLayout.draw(canvas)
            canvas.restore()

            // امضای پایین کارت استوری
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D4AF37")
                textAlign = Paint.Align.CENTER
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("زیج  |  Zij Iranian Calendar", width / 2f, 1740f, footerPaint)

            val subFooterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textAlign = Paint.Align.CENTER
                textSize = 28f
            }
            canvas.drawText("پاسداشت جشن‌ها و تاریخ ایران‌زمین", width / 2f, 1795f, subFooterPaint)

            // ذخیره در حافظه موقت و باز کردن پنجره هم‌رسانی اندروید
            val dir = File(context.cacheDir, "story_cards")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "zij_story_${month}_${day}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "${event.title} — ${day.toPersianDigits()} $monthName $impYear شاهنشاهی")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "هم‌رسانی کارت استوری زیج"))
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در ساخت کارت استوری", Toast.LENGTH_SHORT).show()
        }
    }
}
