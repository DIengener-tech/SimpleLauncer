package com.example.simplelauncher

import android.content.Context
import android.graphics.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/** Малює іконки в стилі iOS 6: заокруглений квадрат, градієнт, глянцевий відблиск, тінь. */
object IconStyler {

    private const val S = 192
    private val RECT = RectF(12f, 8f, 180f, 176f)
    private const val RADIUS = 38f

    fun style(context: Context, src: Drawable): Drawable {
        val icon = toBitmap(src, 216)

        // 1. Вміст іконки (без обрізання)
        val content = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val cc = Canvas(content)

        if (Build.VERSION.SDK_INT >= 26 && src is AdaptiveIconDrawable) {
            // Адаптивна іконка: розтягуємо на весь квадрат
            val half = RECT.width() * 0.75f
            val l = (RECT.centerX() - half).toInt()
            val t = (RECT.centerY() - half).toInt()
            val r = (RECT.centerX() + half).toInt()
            val b = (RECT.centerY() + half).toInt()
            src.background?.let { it.setBounds(l, t, r, b); it.draw(cc) }
            src.foreground?.let { it.setBounds(l, t, r, b); it.draw(cc) }
        } else {
            // Звичайна іконка: градієнтна підкладка + іконка всередині
            val avg = avgColor(icon)
            val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, RECT.top, 0f, RECT.bottom, shade(avg, 1.15f), shade(avg, 0.75f), Shader.TileMode.CLAMP)
            }
            cc.drawRect(RECT, base)
            val inset = 18f
            cc.drawBitmap(
                icon, null,
                RectF(RECT.left + inset, RECT.top + inset, RECT.right - inset, RECT.bottom - inset),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
        }

        // 2. Глянцевий відблиск у верхній половині
        val h = RECT.height()
        val gloss = Path().apply {
            moveTo(RECT.left, RECT.top)
            lineTo(RECT.right, RECT.top)
            lineTo(RECT.right, RECT.top + h * 0.50f)
            quadTo(RECT.centerX(), RECT.top + h * 0.32f, RECT.left, RECT.top + h * 0.50f)
            close()
        }
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, RECT.top, 0f, RECT.top + h * 0.5f, 0xB3FFFFFF.toInt(), 0x33FFFFFF, Shader.TileMode.CLAMP)
        }
        cc.drawPath(gloss, glossPaint)

        // 3. Складання: тінь, вміст у заокругленій формі, обводка
        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(8f, 0f, 5f, 0xAA000000.toInt())
        }
        c.drawRoundRect(RECT, RADIUS, RADIUS, shadow)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RECT, RADIUS, RADIUS, fill)

        // Світла внутрішня лінія зверху
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(0f, RECT.top, 0f, RECT.bottom, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(RECT.left + 2f, RECT.top + 2f, RECT.right - 2f, RECT.bottom - 2f), RADIUS - 2f, RADIUS - 2f, inner)

        // Тонка темна обводка
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x66000000
        }
        c.drawRoundRect(RECT, RADIUS, RADIUS, border)

        return BitmapDrawable(context.resources, out)
    }

    /** Текстура «льон» як у iOS 6 (для пошуку та фону). */
    fun linen(context: Context, base: Int): Drawable {
        val t = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val c = Canvas(t)
        c.drawColor(base)
        val p = Paint()
        p.color = 0x26FFFFFF
        for (i in 0 until 8 step 2) c.drawRect(0f, i.toFloat(), 8f, i + 1f, p)
        p.color = 0x1A000000
        for (i in 1 until 8 step 2) c.drawRect(i.toFloat(), 0f, i + 1f, 8f, p)
        return BitmapDrawable(context.resources, t).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(b))
        return b
    }

    private fun avgColor(b: Bitmap): Int {
        val sm = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = sm.getPixel(x, y)
            if (Color.alpha(p) > 128) {
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++
            }
        }
        if (n == 0L) return Color.GRAY
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun shade(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )
}
