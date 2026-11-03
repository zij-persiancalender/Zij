package ir.zij.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import ir.zij.calendar.PersianDateEngine.toPersianDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object StoryCardShareHelper {

    suspend fun shareOccasionStoryCard(
        context: Context,
        jalaliYear: Int,
        month: Int,
        day: Int,
        event: ZijEvent
    ) {
        val outFile = withContext(Dispatchers.IO) {
            try {
                buildStoryCardImageFile(context, jalaliYear, month, day, event)
            } catch (_: Exception) {
                null
            }
        }

        if (outFile == null) {
            Toast.makeText(context, "خطا در آماده‌سازی کارت استوری", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val monthName = PersianDateEngine.monthNames[month - 1]
            val impYear = (jalaliYear + 1180).toPersianDigits()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "${event.title} — ${day.toPersianDigits()} $monthName $impYear شاهنشاهی
Zij | زیج")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "هم‌رسانی کارت استوری زیج"))
        } catch (_: Exception) {
            Toast.makeText(context, "خطا در باز کردن بخش هم‌رسانی", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildStoryCardImageFile(
        context: Context,
        jalaliYear: Int,
        month: Int,
        day: Int,
        event: ZijEvent
    ): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ۱. تلاش برای دریافت عکس اختصاصی مناسبت از گیت‌هاب (با ذخیره در حافظه پنهان)
        val onlineBg = fetchOrLoadCachedOccasionBitmap(context, month, day, event.category)

        if (onlineBg != null) {
            drawHarmonizedPhotoBackground(canvas, onlineBg, width, height, event.category)
        } else {
            // اگر برای این روز هنوز عکسی روی گیت‌هاب بارگذاری نشده یا گوشی آفلاین است:
            // ترسیم نگاره هنری موضوعی هماهنگ با پالت رنگی برنامه زیج
            drawThematicPersianArtwork(canvas, width, height, month, day, event)
        }

        // ۲. لایه گرادیان سینمایی برای خوانایی کامل متن و یکپارچگی تمام تصاویر با تم زیج
        val overlayColors = when (event.category) {
            EventCategory.SORROW -> intArrayOf(
                Color.parseColor("#B8090E1F"),
                Color.parseColor("#883C0C16"),
                Color.parseColor("#E6080D1C")
            )
            EventCategory.BLACK -> intArrayOf(
                Color.parseColor("#D9000000"),
                Color.parseColor("#A6090C12"),
                Color.parseColor("#F2000000")
            )
            EventCategory.DUAL -> intArrayOf(
                Color.parseColor("#BF0B132B"),
                Color.parseColor("#8C2A1829"),
                Color.parseColor("#EB0B132B")
            )
            EventCategory.JOY -> intArrayOf(
                Color.parseColor("#B50B132B"),
                Color.parseColor("#75122349"),
                Color.parseColor("#EB081024")
            )
        }
        val harmonizeOverlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                overlayColors,
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), harmonizeOverlay)

        // ۳. قاب دوبل ظریف ایرانی (زرین و فیروزه‌ای)
        val accentHex = when (event.category) {
            EventCategory.SORROW -> "#D95B72"
            EventCategory.BLACK -> "#CBD5E1"
            else -> "#E2B93B"
        }

        val outerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#99D4AF37")
        }
        canvas.drawRoundRect(RectF(52f, 52f, width - 52f, height - 52f), 44f, 44f, outerFramePaint)

        val innerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#6600A896")
        }
        canvas.drawRoundRect(RectF(72f, 72f, width - 72f, height - 72f), 34f, 34f, innerFramePaint)

        // ۴. نشان بالای استوری
        val topPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#880B132B")
        }
        canvas.drawRoundRect(RectF(width / 2f - 240f, 128f, width / 2f + 240f, 204f), 38f, 38f, topPillPaint)
        val topPillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#8800A896")
        }
        canvas.drawRoundRect(RectF(width / 2f - 240f, 128f, width / 2f + 240f, 204f), 38f, 38f, topPillStroke)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00D1BA")
            textAlign = Paint.Align.CENTER
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("گاه‌شمار ملی ایرانی", width / 2f, 178f, badgePaint)

        // ۵. عدد روز بزرگ و تاریخ شاهنشاهی + خورشیدی + میلادی
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentHex)
            textAlign = Paint.Align.CENTER
            textSize = 186f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(22f, 0f, 8f, Color.parseColor("#AA000000"))
        }
        canvas.drawText(day.toPersianDigits(), width / 2f, 420f, dayNumPaint)

        val monthYearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(14f, 0f, 4f, Color.parseColor("#AA000000"))
        }
        val monthName = PersianDateEngine.monthNames[month - 1]
        val impYear = (jalaliYear + 1180).toPersianDigits()
        canvas.drawText("$monthName $impYear شاهنشاهی", width / 2f, 512f, monthYearPaint)

        val subDatesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8E2F0")
            textAlign = Paint.Align.CENTER
            textSize = 31f
        }
        val solarStr = "${day.toPersianDigits()} $monthName ${jalaliYear.toPersianDigits()} خورشیدی"
        val gregStr = "${PersianDateEngine.formatGregorianPersian(jalaliYear, month, day)} میلادی"
        canvas.drawText("$solarStr  •  $gregStr", width / 2f, 575f, subDatesPaint)

        // ۶. کارت شیشه‌ای نیمه‌شفاف روی پس‌زمینه برای عنوان و توضیحات
        val cardRect = RectF(104f, 650f, width - 104f, 1605f)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (event.category == EventCategory.BLACK) {
                Color.parseColor("#CC0A0D14")
            } else {
                Color.parseColor("#BF101B38")
            }
        }
        canvas.drawRoundRect(cardRect, 42f, 42f, cardBgPaint)

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor(accentHex)
        }
        canvas.drawRoundRect(cardRect, 42f, 42f, cardBorderPaint)

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
        canvas.translate(cardRect.left + 60f, cardRect.top + 68f)
        titleLayout.draw(canvas)
        canvas.restore()

        val dividerY = cardRect.top + 92f + titleLayout.height + 24f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55D4AF37")
            strokeWidth = 2f
        }
        canvas.drawLine(cardRect.left + 110f, dividerY, cardRect.right - 110f, dividerY, divPaint)

        val descTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 39f
        }
        val descLayout = StaticLayout.Builder
            .obtain(event.description, 0, event.description.length, descTextPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(14f, 1.26f)
            .build()

        canvas.save()
        canvas.translate(cardRect.left + 60f, dividerY + 42f)
        descLayout.draw(canvas)
        canvas.restore()

        // ۷. امضای دقیق پایین کارت استوری طبق خواسته شما: «Zij | زیج»
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2B93B")
            textAlign = Paint.Align.CENTER
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(12f, 0f, 4f, Color.parseColor("#AA000000"))
        }
        canvas.drawText("Zij | زیج", width / 2f, 1755f, footerPaint)

        val dir = File(context.cacheDir, "story_cards")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, "zij_story_${month}_${day}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return outFile
    }

    private fun fetchOrLoadCachedOccasionBitmap(
        context: Context,
        month: Int,
        day: Int,
        category: EventCategory
    ): Bitmap? {
        val bgCacheDir = File(context.cacheDir, "story_bg_cache")
        if (!bgCacheDir.exists()) bgCacheDir.mkdirs()

        val specificCacheFile = File(bgCacheDir, "bg_${month}_${day}.img")
        // اگر کمتر از ۳ روز از ذخیره فایل در حافظه پنهان گذشته باشد، بی‌درنگ بارگذاری کن
        if (specificCacheFile.exists() && specificCacheFile.length() > 1024 &&
            (System.currentTimeMillis() - specificCacheFile.lastModified() < 3L * 24 * 3600 * 1000)
        ) {
            BitmapFactory.decodeFile(specificCacheFile.absolutePath)?.let { return it }
        }

        val rawBase = PersianDateEngine.getGithubRawBaseUrl(context)
        if (rawBase.contains("YOUR_USERNAME")) return null

        val catKey = category.name.lowercase()
        val candidateUrls = listOf(
            "$rawBase/story_images/${month}_${day}.jpg",
            "$rawBase/story_images/${month}_${day}.png",
            "$rawBase/story_images/theme_${catKey}.jpg",
            "$rawBase/story_images/theme_${catKey}.png"
        )

        for ((idx, urlStr) in candidateUrls.withIndex()) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 1024) {
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (decoded != null) {
                            val saveTarget = if (idx <= 1) specificCacheFile else File(bgCacheDir, "bg_theme_${catKey}.img")
                            FileOutputStream(saveTarget).use { it.write(bytes) }
                            return decoded
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * برش مرکزی (Center-Crop) و تنظیم کنتراست و رنگ عکس تا کاملاً هم‌خانواده با پالت رنگی زیج دیده شود
     */
    private fun drawHarmonizedPhotoBackground(
        canvas: Canvas,
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        category: EventCategory
    ) {
        val scale = max(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
        val dx = (targetW - src.width * scale) / 2f
        val dy = (targetH - src.height * scale) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }

        val cm = ColorMatrix().apply {
            setSaturation(if (category == EventCategory.BLACK) 0.12f else 0.78f)
        }
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, matrix, photoPaint)
    }

    /**
     * نگارگر برداری پشتیبان با الگوهای اصیل ایرانی (شمسه، ستون تخت‌جمشید، شعله سده، سرو و آسمان پرستاره)
     * برای زمانی که کاربر اینترنت ندارد یا هنوز تصویر اختصاصی آن روز در گیت‌هاب قرار نگرفته است
     */
    private fun drawThematicPersianArtwork(
        canvas: Canvas,
        width: Int,
        height: Int,
        month: Int,
        day: Int,
        event: ZijEvent
    ) {
        val baseColors = when (event.category) {
            EventCategory.SORROW -> intArrayOf(
                Color.parseColor("#120710"),
                Color.parseColor("#3B0D18"),
                Color.parseColor("#0B132B")
            )
            EventCategory.BLACK -> intArrayOf(
                Color.parseColor("#000000"),
                Color.parseColor("#111622"),
                Color.parseColor("#000000")
            )
            EventCategory.DUAL -> intArrayOf(
                Color.parseColor("#3B0D18"),
                Color.parseColor("#0B132B"),
                Color.parseColor("#2A220E")
            )
            EventCategory.JOY -> intArrayOf(
                Color.parseColor("#070F24"),
                Color.parseColor("#10234A"),
                Color.parseColor("#092B34")
            )
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                baseColors,
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // ستارگان ظریف رصدخانه زیج در آسمان پس‌زمینه
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44F7F3E8")
        }
        for (i in 1..42) {
            val sx = ((i * 197 + month * 53 + day * 29) % (width - 160)) + 80f
            val sy = ((i * 311 + day * 71) % (height - 200)) + 100f
            val sr = if (i % 5 == 0) 4.2f else 2.2f
            canvas.drawCircle(sx, sy, sr, starPaint)
        }

        // شمسه هندسی بزرگ ایرانی و مدارهای اسطرلاب در پشت عدد روز
        val sunCenterX = width / 2f
        val sunCenterY = 415f
        val glowColor = when (event.category) {
            EventCategory.SORROW -> Color.parseColor("#556E1423")
            EventCategory.BLACK -> Color.parseColor("#3394A3B8")
            else -> Color.parseColor("#45D4AF37")
        }
        val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sunCenterX, sunCenterY, 460f,
                intArrayOf(glowColor, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(sunCenterX, sunCenterY, 460f, radialPaint)

        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = if (event.category == EventCategory.BLACK) Color.parseColor("#30CBD5E1") else Color.parseColor("#36D4AF37")
        }
        canvas.drawCircle(sunCenterX, sunCenterY, 260f, ringStroke)
        canvas.drawCircle(sunCenterX, sunCenterY, 350f, ringStroke)

        // پرتوهای ۲۴گانه شمسه خورشیدی
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#2800A896")
        }
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = sunCenterX + (260f * cos(rad)).toFloat()
            val y1 = sunCenterY + (260f * sin(rad)).toFloat()
            val x2 = sunCenterX + (390f * cos(rad)).toFloat()
            val y2 = sunCenterY + (390f * sin(rad)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }

        // افق پلکانی الهام‌گرفته از پاسارگاد و تخت‌جمشید در پایین تصویر
        val silhouettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val horizonPath = Path().apply {
            moveTo(90f, 1680f)
            lineTo(260f, 1680f)
            lineTo(260f, 1650f)
            lineTo(390f, 1650f)
            lineTo(390f, 1625f)
            lineTo(width - 390f, 1625f)
            lineTo(width - 390f, 1650f)
            lineTo(width - 260f, 1650f)
            lineTo(width - 260f, 1680f)
            lineTo(width - 90f, 1680f)
        }
        canvas.drawPath(horizonPath, silhouettePaint)
    }
}
