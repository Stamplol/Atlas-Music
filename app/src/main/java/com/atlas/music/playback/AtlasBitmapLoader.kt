package com.atlas.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.annotation.Nullable
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.atlas.music.util.CoverArtGenerator
import com.atlas.music.util.CoverVariant
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Returns a procedural cover (checksum-seeded, me-when-music) whenever the
 * track's MediaStore album-art URI would fail (no embedded art).
 * Default provider then sets it as the notification large icon automatically.
 */
class AtlasBitmapLoader(private val context: Context) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("audio/")
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return Futures.immediateFuture(proceduralBitmap("decode-" + data.size, "—"))
    }
    override fun loadBitmap(uri: android.net.Uri): ListenableFuture<Bitmap> {
        // No real art — return procedural fallback keyed by URI itself
        return Futures.immediateFuture(proceduralBitmap(uri.toString(), "—"))
    }

    /** Same seed family as the notification large icon — used by the home widget. */
    fun coverForTrack(title: String, artist: String, album: String): Bitmap {
        val key = (title + "|" + artist + "|" + album + "|")
        return proceduralBitmap(key, title)
    }

    @Nullable override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        // If metadata has no artworkUri, procedural; otherwise try procedural seeded by metadata (unique per track).
        // The session's own loader would try Content URI and fail — we replace with a good-looking fallback.
        val key = (metadata.title?.toString().orEmpty() + "|" + metadata.artist?.toString().orEmpty() + "|" + metadata.albumTitle?.toString().orEmpty() + "|" + metadata.artworkUri?.toString().orEmpty())
        return Futures.immediateFuture(proceduralBitmap(key, metadata.title?.toString().orEmpty()))
    }

    fun proceduralBitmap(key: String, title: String): Bitmap {
        val seed = CoverArtGenerator.hashStr(key)
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Premium base: dark observatory gradient (not flat) so art floats.
        val grad = android.graphics.LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            AndroidColor.parseColor("#0B0D12"), AndroidColor.parseColor("#181825"),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { shader = grad })
        // Palette (Catppuccin, matches CoverArt PALETTE)
        val pal = intArrayOf(
            AndroidColor.parseColor("#89B4FA"),
            AndroidColor.parseColor("#94E2D5"),
            AndroidColor.parseColor("#CBA6F7"),
            AndroidColor.parseColor("#A6E3A1"),
            AndroidColor.parseColor("#F9E2AF"),
            AndroidColor.parseColor("#FAB387"),
            AndroidColor.parseColor("#F38BA8"),
        )
        val rnd = CoverArtGenerator.mulberry(seed)
        val variant = CoverArtGenerator.variantForSeed(seed)
        fun col(idx: Int) = pal[((seed + idx) % pal.size + pal.size) % pal.size]

        when (variant) {
            CoverVariant.GEO -> {
                val dot = Paint().apply { color = AndroidColor.parseColor("#313244") }
                var y = 10f
                while (y < size) { var x = 10f; while (x < size) { canvas.drawRect(x, y, x + 1.5f, y + 1.5f, dot); x += 18f }; y += 18f }
                for (i in 0 until 5) {
                    val c = col(i * 2)
                    val kind = (rnd() * 4).toInt()
                    val x = (rnd() * size).toFloat()
                    val yy = (rnd() * size).toFloat()
                    val r = (18 + rnd() * 70).toFloat()
                    val stroke = Paint().apply { color = c; style = Paint.Style.STROKE; strokeWidth = 2.5f }
                    val fill = Paint().apply { color = c; style = Paint.Style.FILL }
                    when (kind) {
                        0 -> canvas.drawCircle(x, yy, r, stroke.apply { alpha = 230 })
                        1 -> canvas.drawCircle(x, yy, r, fill.apply { alpha = 70 })
                        2 -> { canvas.drawLine(x - r, yy + r, x + r, yy - r, stroke.apply { alpha = 230 }); canvas.drawLine(x - r + 8, yy + r, x + r + 8, yy - r, stroke.apply { alpha = 100 }) }
                        3 -> canvas.drawRect(x - r/2, yy - r/2, x + r/2, yy + r/2, stroke.apply { alpha = 220 })
                        else -> for (k in 0 until 4) canvas.drawCircle(x + k * 14 - 20, yy, 3.5f, fill.apply { alpha = 230 })
                    }
                }
            }
            CoverVariant.RINGS -> {
                val cx = (size * (0.3 + rnd() * 0.4)).toFloat()
                val cy = (size * (0.3 + rnd() * 0.35)).toFloat()
                for (i in 6 downTo 1) {
                    val paint = Paint().apply { color = col(i); style = Paint.Style.STROKE; strokeWidth = if (i == 6) 5f else 2f; alpha = if (i == 6) 230 else 140 }
                    canvas.drawCircle(cx, cy, i * size / 7.5f, paint)
                }
                for (i in 0 until 24) {
                    val paint = Paint().apply { color = col(seed % pal.size); alpha = (0.25 + rnd() * 0.6).toInt() * 255 }
                    canvas.drawRect((rnd() * size).toFloat(), (rnd() * size).toFloat(), (rnd() * size).toFloat() + 2.5f, (rnd() * size).toFloat() + 2.5f, paint)
                }
            }
            CoverVariant.GRID -> {
                val n = 7; val s = size / n.toFloat()
                for (gy in 0 until n) for (gx in 0 until n) {
                    val r = rnd()
                    if (r < 0.42) {
                        val paint = Paint().apply { color = pal[(((gx * 3 + gy * 5 + seed) % pal.size) + pal.size) % pal.size]; alpha = (0.35 + rnd() * 0.55).toInt() * 255 }
                        canvas.drawRect(gx * s + 2, gy * s + 2, gx * s + s - 2, gy * s + s - 2, paint)
                    } else if (r < 0.5) {
                        val paint = Paint().apply { color = AndroidColor.parseColor("#6C7086"); style = Paint.Style.STROKE; strokeWidth = 1.5f; alpha = 180 }
                        canvas.drawRect(gx * s + 2, gy * s + 2, gx * s + s - 2, gy * s + s - 2, paint)
                    }
                }
            }
            CoverVariant.DIAG -> {
                var i = -size
                while (i < size * 2) {
                    val paint = Paint().apply { color = col((i / 22) + seed); strokeWidth = 3f; alpha = 140 }
                    canvas.drawLine(i.toFloat(), 0f, (i + size).toFloat(), size.toFloat(), paint)
                    i += 22
                }
                val paint = Paint().apply { color = AndroidColor.parseColor("#CDD6F4"); style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 235 }
                canvas.drawCircle(size * 0.68f, size * 0.34f, size * 0.2f, paint)
            }
            CoverVariant.DUNES -> {
                for (r in 0 until 4) {
                    val paint = Paint().apply { color = col(r * 2); strokeWidth = 2.5f; alpha = 218; style = Paint.Style.STROKE }
                    var first = true
                    val baseY = size * (0.25f + r * 0.16f)
                    val amp = size * 0.05f
                    val phase = r * 1.9 + (seed % 100)
                    var prevX = 0f
                    var prevY = (baseY + kotlin.math.sin(0f * 0.03f + phase) * amp).toFloat()
                    var x = 0f
                    while (x <= size) {
                        val y = (baseY + kotlin.math.sin(x * 0.03f + phase) * amp).toFloat()
                        if (!first) canvas.drawLine(prevX, prevY, x, y, paint)
                        prevX = x; prevY = y; first = false
                        x += 5f
                    }
                }
                val dotPaint = Paint().apply { color = col(3); alpha = 128 }
                canvas.drawCircle((size * (0.2 + rnd() * 0.6)).toFloat(), size * 0.14f, 9f, dotPaint)
            }
            CoverVariant.AURORA -> {
                for (r in 0 until 5) {
                    val paint = Paint().apply { color = col(r); strokeWidth = 8f; alpha = 150; style = Paint.Style.STROKE }
                    var first = true; var prevX = 0f; val baseY = size * (0.28f + r * 0.13f); val amp = size * 0.07f; val phase = r * 1.3f + (seed % 20)
                    var prevY = (baseY + kotlin.math.sin(0f * 0.04f + phase) * amp).toFloat()
                    var x = 0f
                    while (x <= size) { val y = (baseY + kotlin.math.sin(x * 0.04f + phase) * amp + kotlin.math.sin(x * 0.012f - phase * 2f) * amp * 0.4f).toFloat(); if (!first) canvas.drawLine(prevX, prevY, x, y, paint); prevX = x; prevY = y; first = false; x += 6f }
                }
            }
            CoverVariant.MERIDIAN -> {
                val cx = size/2f; val cy = size/2f; val r = size * 0.34f
                var paint = Paint().apply { color = col(0); style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 230 }
                canvas.drawCircle(cx, cy, r, paint)
                paint = Paint().apply { color = col(1); style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 140 }
                canvas.drawOval(cx - r*0.55f, cy - r, cx + r*0.55f, cy + r, paint)
                paint = Paint().apply { color = col(2); style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 140 }
                canvas.drawOval(cx - r, cy - r*0.55f, cx + r, cy + r*0.55f, paint)
                val star = android.graphics.Path().apply { moveTo(cx, cy - r*0.85f); lineTo(cx + r*0.16f, cy - r*0.12f); lineTo(cx + r*0.85f, cy); lineTo(cx + r*0.16f, cy + r*0.12f); lineTo(cx, cy + r*0.85f); lineTo(cx - r*0.16f, cy + r*0.12f); lineTo(cx - r*0.85f, cy); lineTo(cx - r*0.16f, cy - r*0.12f); close() }
                canvas.drawPath(star, Paint().apply { color = col(3); alpha = 242 })
                canvas.drawCircle(cx, cy, 6f, Paint().apply { color = AndroidColor.parseColor("#181825") })
            }
            CoverVariant.ARCS -> {
                for (i in 6 downTo 1) {
                    val paint = Paint().apply { color = col(i); style = Paint.Style.STROKE; strokeWidth = 4f; alpha = 190 }
                    val sz = i * size / 6.5f
                    canvas.drawArc(0f - sz/2, size - sz/2, sz/2, size + sz/2, 270f, 90f, false, paint)
                }
            }
            CoverVariant.NOISE -> {
                for (i in 0 until 180) {
                    val paint = Paint().apply { color = pal[(rnd() * pal.size).toInt()]; alpha = (0.25f + rnd().toFloat() * 0.75f).toInt() * 255 }
                    canvas.drawRect((rnd()*size).toFloat(), (rnd()*size).toFloat(), (rnd()*size).toFloat() + (1f + rnd().toFloat()*2.5f), (rnd()*size).toFloat() + (1f + rnd().toFloat()*2.5f), paint)
                }
            }
            CoverVariant.SPARK -> {
                val cx = size/2f; val cy = size/2f
                for (i in 0 until 16) {
                    val a = (i / 16f) * 2f * kotlin.math.PI.toFloat()
                    val len = size * (0.28f + rnd().toFloat() * 0.28f)
                    val x2 = cx + kotlin.math.cos(a) * len; val y2 = cy + kotlin.math.sin(a) * len
                    val paint = Paint().apply { color = col(i); strokeWidth = 3f; alpha = 190 }
                    canvas.drawLine(cx, cy, x2, y2, paint)
                }
                canvas.drawCircle(cx, cy, 10f, Paint().apply { color = col(0) })
            }
            CoverVariant.HORIZON -> {
                val cx = size/2f
                for (i in 0 until 3) {
                    val paint = Paint().apply { color = col(i); strokeWidth = 3f; alpha = 180 }
                    val y = size * (0.62f + i * 0.11f)
                    canvas.drawLine(20f, y, size - 20f, y, paint)
                }
                canvas.drawCircle(cx, size * 0.62f, size * 0.22f, Paint().apply { color = col(3); alpha = 230 })
            }
            CoverVariant.ORBIT -> {
                val cx = size/2f; val cy = size/2f
                canvas.drawCircle(cx, cy, 9f, Paint().apply { color = col(0) })
                for (ring in 1..3) {
                    val r = size * (0.18f + ring * 0.13f)
                    canvas.drawCircle(cx, cy, r, Paint().apply { color = col(ring); style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 153 })
                    val nDots = 3 + ring * 2
                    for (k in 0 until nDots) {
                        val a = (k.toFloat() / nDots) * 2f * kotlin.math.PI.toFloat() + ring * 0.7f
                        canvas.drawCircle(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r, 4f, Paint().apply { color = col(ring + k) })
                    }
                }
            }
        }
        // Strip: dark scrim + Satins Gold meridian line + initial + ATLAS mark
        val stripBg = Paint().apply { color = AndroidColor.parseColor("#0B0D12"); alpha = 235 }
canvas.drawRect(0f, (size - 52f).toFloat(), size.toFloat(), size.toFloat(), stripBg)
val initial = title.trim().firstOrNull()?.uppercase() ?: "-"
val titlePaint = Paint().apply { color = AndroidColor.parseColor("#FFFFFF"); textSize = size * 0.095f; typeface = Typeface.MONOSPACE; isFakeBoldText = true }
canvas.drawText(initial, 14f, size - 18f, titlePaint)
val metaPaint = Paint().apply { color = AndroidColor.parseColor("#A6ADC8"); textSize = size * 0.042f; typeface = Typeface.MONOSPACE }
canvas.drawText("ATLAS  ·  MERIDIAN SETTLE", 14f + size * 0.13f, size - 18f, metaPaint)
val accentPaint = Paint().apply { color = AndroidColor.parseColor("#E5A83D"); alpha = 255 }
canvas.drawRect(0f, (size - 52f).toFloat(), size.toFloat(), (size - 50f).toFloat(), accentPaint)
        return bitmap
    }
}
