package com.atlas.music.util

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atlas.music.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural cover art generator ported from me-when-music (app.js paintCover).
 *
 * Seed = file checksum (SHA-256 of content, sampled). Drop the same file anywhere
 * on the device and the same cover appears — stable forever. The art is uniquely
 * generated per content hash, not per path/name.
 *
 * Variants: geo | rings | grid | diag | dunes | glyph — auto-selected by seed,
 * exactly like the original: coverVariant = AUTO_COVERS[seed % 6].
 */
enum class CoverVariant { GEO, RINGS, GRID, DIAG, DUNES, AURORA, MERIDIAN, ARCS, NOISE, SPARK, HORIZON, ORBIT }

object CoverArtGenerator {

    // Catppuccin Mocha palette from me-when-music (app.js)
    val PALETTE: List<Color> = listOf(
        Color(0xFF89B4FA), // blue
        Color(0xFF94E2D5), // teal
        Color(0xFFCBA6F7), // mauve
        Color(0xFFA6E3A1), // green
        Color(0xFFF9E2AF), // yellow
        Color(0xFFFAB387), // peach
        Color(0xFFF38BA8), // red
    )
    val MANTLE = Color(0xFF181825)
    val BORDER = Color(0xFF313244)
    val TEXT_COLOR = Color(0xFFCDD6F4)
    val DIM = Color(0xFF6C7086)

    val AUTO_COVERS: List<CoverVariant> = listOf(
        CoverVariant.GEO, CoverVariant.RINGS, CoverVariant.GRID,
        CoverVariant.DIAG, CoverVariant.DUNES, CoverVariant.AURORA,
        CoverVariant.MERIDIAN, CoverVariant.ARCS, CoverVariant.NOISE,
        CoverVariant.SPARK, CoverVariant.HORIZON, CoverVariant.ORBIT,
    )

    private val seedCache = ConcurrentHashMap<String, Int>()

    /** FNV-1a hash (matches hashStr in me-when-music) — used for fallback seeds. */
    fun hashStr(s: String): Int {
        var h: Int = -375076303 // 2166136261 as signed 32-bit
        for (ch in s) {
            h = h xor ch.code
            h = h * 16777619
        }
        return h
    }

    /** mulberry32 — same PRNG as the web project, deterministic per seed. */
    fun mulberry(seed: Int): () -> Double {
        var a = seed
        return {
            a = (a + 0x6D2B79F5)
            var t = a
            // Integer math mimics JS Math.imul / >>> 0 via 32-bit wrapping
            t = ((t xor (t ushr 15)) * (t or 1))
            t = (t + ((t xor (t ushr 7)) * (t or 61))) xor t
            ((t xor (t ushr 14)) ushr 0).toUInt().toDouble() / 4294967296.0
        }
    }

    fun variantForSeed(seed: Int): CoverVariant {
        val idx = ((seed % AUTO_COVERS.size) + AUTO_COVERS.size) % AUTO_COVERS.size
        return AUTO_COVERS[idx]
    }

    /**
     * Compute a stable seed from the file's content checksum.
     * Reads up to 1 MB (first chunk) + file size, so it's fast even for long mixes.
     * Result is cached per URI so the same track never re-reads its file.
     */
    suspend fun seedForTrack(context: Context, track: TrackEntity): Int =
        withContext(Dispatchers.IO) {
            val key = track.uri
            seedCache[key]?.let { return@withContext it }
            val seed = runCatching {
                computeFileSeed(context, track)
            }.getOrElse {
                // Fallback: stable hash of URI + size (still unique per file identity)
                hashStr(track.uri + "|" + track.sizeBytes + "|" + track.durationMs)
            }
            seedCache[key] = seed
            seed
        }

    private fun computeFileSeed(context: Context, track: TrackEntity): Int {
        // Prefer a real file path (cheapest); fall back to ContentResolver stream.
        val bytes = readSample(context, track) ?: return hashStr(track.uri + track.sizeBytes)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.update(track.sizeBytes.toString().toByteArray())
        val digest = md.digest()
        // First 4 bytes as Int (unsigned → signed, but variant math handles negatives)
        var seed = 0
        for (i in 0 until 4) {
            seed = (seed shl 8) or (digest[i].toInt() and 0xFF)
        }
        return seed
    }

    private fun readSample(context: Context, track: TrackEntity): ByteArray? {
        // Direct file
        if (track.filePath.isNotBlank()) {
            val f = File(track.filePath)
            if (f.exists() && f.canRead() && f.length() > 0) {
                FileInputStream(f).use { input ->
                    val buf = ByteArray(1024 * 1024) // 1 MB cap for seed
                    val n = input.read(buf)
                    if (n > 0) return buf.copyOf(minOf(n, 1024 * 1024))
                }
            }
        }
        // Content URI
        val uri = Uri.parse(track.uri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(1024 * 1024)
            val n = input.read(buf)
            if (n > 0) return buf.copyOf(minOf(n, 1024 * 1024))
        }
        return null
    }

    /** For tests / previews where no file exists — deterministic from title+size. */
    fun seedForPreview(title: String, sizeBytes: Long): Int = hashStr(title + "|" + sizeBytes)
}

@Composable
fun ProceduralCoverArt(
    seed: Int,
    title: String,
    ext: String,
    sizeBytes: Long,
    modifier: Modifier = Modifier,
    showStrip: Boolean = true,
) {
    val variant = remember(seed) { CoverArtGenerator.variantForSeed(seed) }
    val pal = CoverArtGenerator.PALETTE
    val base = CoverArtGenerator.MANTLE
    val border = CoverArtGenerator.BORDER
    val textColor = CoverArtGenerator.TEXT_COLOR

    Box(modifier = modifier.background(base)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            if (W <= 0 || H <= 0) return@Canvas
            val minWH = minOf(W, H)
            val rnd = CoverArtGenerator.mulberry(seed)
            fun col(idx: Int) = pal[((seed + idx) % pal.size + pal.size) % pal.size]

            when (variant) {
                CoverVariant.GEO -> {
                    // Dot grid
                    var y = 10.dp.toPx()
                    while (y < H) {
                        var x = 10.dp.toPx()
                        while (x < W) {
                            drawRect(border, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(1.5.dp.toPx(), 1.5.dp.toPx()))
                            x += 18.dp.toPx()
                        }
                        y += 18.dp.toPx()
                    }
                    for (i in 0 until 5) {
                        val c = col(i * 2)
                        val kind = (rnd() * 4).toInt()
                        val x = (rnd() * W).toFloat()
                        val y = (rnd() * H).toFloat()
                        val r = (18.0 + rnd() * 70.0).toFloat()
                        val alpha = when (kind) {
                            1 -> 0.28f
                            2 -> 0.9f
                            else -> 0.9f
                        }
                        when (kind) {
                            0 -> {
                                drawCircle(color = c.copy(alpha = alpha), radius = r, style = Stroke(width = 2.5.dp.toPx()), center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                            1 -> {
                                drawCircle(color = c.copy(alpha = alpha), radius = r, center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                            2 -> {
                                drawLine(color = c.copy(alpha = alpha), start = androidx.compose.ui.geometry.Offset(x - r, y + r), end = androidx.compose.ui.geometry.Offset(x + r, y - r), strokeWidth = 2.5.dp.toPx())
                                drawLine(color = c.copy(alpha = 0.4f), start = androidx.compose.ui.geometry.Offset(x - r + 8, y + r), end = androidx.compose.ui.geometry.Offset(x + r + 8, y - r), strokeWidth = 2.5.dp.toPx())
                            }
                            3 -> {
                                drawRect(color = c.copy(alpha = 0.85f), topLeft = androidx.compose.ui.geometry.Offset(x - r / 2, y - r / 2), size = androidx.compose.ui.geometry.Size(r, r), style = Stroke(width = 2.5.dp.toPx()))
                            }
                            else -> {
                                for (k in 0 until 4) {
                                    drawCircle(color = c.copy(alpha = 0.9f), radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x + k * 14 - 20, y))
                                }
                            }
                        }
                    }
                }
                CoverVariant.RINGS -> {
                    val cx = (W * (0.3 + rnd() * 0.4)).toFloat()
                    val cy = (H * (0.3 + rnd() * 0.35)).toFloat()
                    for (i in 6 downTo 1) {
                        val c = col(i)
                        val radius = (i * minWH / 7.5f)
                        val alpha = if (i == 6) 0.9f else 0.55f
                        val width = if (i == 6) 5.dp.toPx() else 2.dp.toPx()
                        drawCircle(color = c.copy(alpha = alpha), radius = radius, style = Stroke(width = width), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    }
                    for (i in 0 until 24) {
                        val a = (0.25 + rnd() * 0.6).toFloat()
                        drawRect(color = col(seed % pal.size).copy(alpha = a), topLeft = androidx.compose.ui.geometry.Offset((rnd() * W).toFloat(), (rnd() * H).toFloat()), size = androidx.compose.ui.geometry.Size(2.5.dp.toPx(), 2.5.dp.toPx()))
                    }
                }
                CoverVariant.GRID -> {
                    val n = 7
                    val s = W / n
                    for (gy in 0 until n) for (gx in 0 until n) {
                        val r = rnd()
                        if (r < 0.42) {
                            val c = pal[(((gx * 3 + gy * 5 + seed) % pal.size) + pal.size) % pal.size]
                            val a = (0.35 + rnd() * 0.55).toFloat()
                            drawRect(color = c.copy(alpha = a), topLeft = androidx.compose.ui.geometry.Offset(gx * s + 2, gy * s + 2), size = androidx.compose.ui.geometry.Size(s - 4, s - 4))
                        } else if (r < 0.5) {
                            drawRect(color = CoverArtGenerator.DIM.copy(alpha = 0.7f), topLeft = androidx.compose.ui.geometry.Offset(gx * s + 2, gy * s + 2), size = androidx.compose.ui.geometry.Size(s - 4, s - 4), style = Stroke(width = 1.5.dp.toPx()))
                        }
                    }
                }
                CoverVariant.DIAG -> {
                    var i = -H.toInt()
                    while (i < W + H) {
                        val idx = ((i / 22) + seed) % pal.size
                        val c = pal[((idx % pal.size) + pal.size) % pal.size]
                        drawLine(color = c.copy(alpha = 0.55f), start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(i + H, H), strokeWidth = 3.dp.toPx())
                        i += 22
                    }
                    drawCircle(color = textColor.copy(alpha = 0.92f), radius = minWH * 0.2f, style = Stroke(width = 3.dp.toPx()), center = androidx.compose.ui.geometry.Offset(W * 0.68f, H * 0.34f))
                }
                CoverVariant.DUNES -> {
                    for (r in 0 until 4) {
                        val c = col(r * 2)
                        var first = true
                        val baseY = H * (0.25f + r * 0.16f)
                        val amp = H * 0.05f
                        val phase = (r * 1.9 + (seed % 100)).toFloat()
                        var x = 0f
                        // Trace with line segments (Canvas has no path chaining helper here)
                        var prevX = 0f
                        var prevY = (baseY + sin(0f * 0.03f + phase) * amp).toFloat()
                        while (x <= W) {
                            val y = (baseY + sin(x * 0.03f + phase) * amp).toFloat()
                            if (!first) {
                                drawLine(color = c.copy(alpha = 0.85f), start = androidx.compose.ui.geometry.Offset(prevX, prevY), end = androidx.compose.ui.geometry.Offset(x, y), strokeWidth = 2.5.dp.toPx())
                            }
                            prevX = x; prevY = y
                            first = false
                            x += 5f
                        }
                    }
                    val dotX = (W * (0.2 + rnd() * 0.6)).toFloat()
                    drawCircle(color = col(3).copy(alpha = 0.5f), radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(dotX, H * 0.14f))
                }
                CoverVariant.AURORA -> {
                    // Flowing northern lights — thick sine bands, teal→mauve
                    for (r in 0 until 5) {
                        val c = pal[((r + seed) % pal.size + pal.size) % pal.size]
                        var first = true
                        var prevX = 0f
                        val baseY = H * (0.28f + r * 0.13f)
                        val amp = H * 0.07f
                        val phase = r * 1.3f + (seed % 20)
                        var prevY = (baseY + sin(0f * 0.04f + phase) * amp).toFloat()
                        var x = 0f
                        while (x <= W) {
                            val y = (baseY + sin(x * 0.04f + phase) * amp + sin(x * 0.012f - phase * 2f) * amp * 0.4f).toFloat()
                            if (!first) drawLine(color = c.copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(prevX, prevY), end = androidx.compose.ui.geometry.Offset(x, y), strokeWidth = 8.dp.toPx())
                            prevX = x; prevY = y; first = false
                            x += 6f
                        }
                    }
                    // Subtle grid dots
                    var gy = 10.dp.toPx()
                    while (gy < H) { var gx = 10.dp.toPx(); while (gx < W) { drawRect(border.copy(alpha=0.5f), topLeft = androidx.compose.ui.geometry.Offset(gx, gy), size = androidx.compose.ui.geometry.Size(1f, 1f)); gx += 22.dp.toPx() }; gy += 22.dp.toPx() }
                }
                CoverVariant.MERIDIAN -> {
                    val cx = W / 2f; val cy = H / 2f; val r = minWH * 0.34f
                    // Outer ring
                    drawCircle(color = col(0).copy(alpha = 0.9f), radius = r, style = Stroke(width = 3.dp.toPx()), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    // Ellipses (globe)
                    drawOval(color = col(1).copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(cx - r * 0.55f, cy - r), size = androidx.compose.ui.geometry.Size(r * 1.1f, r * 2f), style = Stroke(width = 2.dp.toPx()))
                    drawOval(color = col(2).copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(cx - r, cy - r * 0.55f), size = androidx.compose.ui.geometry.Size(r * 2f, r * 1.1f), style = Stroke(width = 2.dp.toPx()))
                    // Star needle
                    val star = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - r * 0.85f)
                        lineTo(cx + r * 0.16f, cy - r * 0.12f)
                        lineTo(cx + r * 0.85f, cy)
                        lineTo(cx + r * 0.16f, cy + r * 0.12f)
                        lineTo(cx, cy + r * 0.85f)
                        lineTo(cx - r * 0.16f, cy + r * 0.12f)
                        lineTo(cx - r * 0.85f, cy)
                        lineTo(cx - r * 0.16f, cy - r * 0.12f)
                        close()
                    }
                    drawPath(path = star, color = col(3).copy(alpha = 0.95f))
                    drawCircle(color = base, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    // Dots on orbit
                    for (i in 0 until 12) {
                        val a = (rnd() * 360).toFloat() * PI.toFloat() / 180f
                        drawCircle(color = col(i).copy(alpha = 0.8f), radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + cos(a) * r, cy + sin(a) * r))
                    }
                }
                CoverVariant.ARCS -> {
                    // Concentric rainbow arcs from bottom-left
                    val cx = 0f; val cy = H
                    for (i in 6 downTo 1) {
                        val c = col(i)
                        drawArc(color = c.copy(alpha = 0.75f), startAngle = 270f, sweepAngle = 90f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(cx - i * minWH / 6.5f, cy - i * minWH / 6.5f), size = androidx.compose.ui.geometry.Size(i * minWH / 3.25f, i * minWH / 3.25f), style = Stroke(width = 4.dp.toPx()))
                    }
                    for (i in 0 until 20) {
                        drawRect(color = col(i).copy(alpha = 0.4f), topLeft = androidx.compose.ui.geometry.Offset((rnd() * W).toFloat(), (rnd() * H).toFloat()), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), 3.dp.toPx()))
                    }
                }
                CoverVariant.NOISE -> {
                    // Starfield noise + diagonal streak
                    for (i in 0 until 180) {
                        val a = (0.25f + rnd() * 0.75f).toFloat()
                        val r = (1f + rnd().toFloat() * 2.5f).dp.toPx()
                        drawRect(color = pal[(rnd() * pal.size).toInt()].copy(alpha = a), topLeft = androidx.compose.ui.geometry.Offset((rnd() * W).toFloat(), (rnd() * H).toFloat()), size = androidx.compose.ui.geometry.Size(r, r))
                    }
                    for (i in 0 until 3) {
                        val c = col(i * 2 + 1)
                        var x = -H; while (x < W) {
                            drawLine(color = c.copy(alpha = 0.35f), start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(x + H * 0.4f, H), strokeWidth = 2.dp.toPx())
                            x += 48
                        }
                    }
                }
                CoverVariant.SPARK -> {
                    val cx = W / 2f; val cy = H / 2f
                    for (i in 0 until 16) {
                        val a = (i / 16f) * 2f * PI.toFloat()
                        val len = minWH * (0.28f + rnd().toFloat() * 0.28f)
                        val x2 = cx + cos(a) * len; val y2 = cy + sin(a) * len
                        drawLine(color = col(i).copy(alpha = 0.75f), start = androidx.compose.ui.geometry.Offset(cx, cy), end = androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        drawCircle(color = col(i).copy(alpha = 0.9f), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x2, y2))
                    }
                    drawCircle(color = col(0), radius = 10.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    drawCircle(color = base, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                }
                CoverVariant.HORIZON -> {
                    // Sun on horizon + 3 strata + dots
                    val sunY = H * 0.62f; val sunR = minWH * 0.22f; val cx = W / 2f
                    for (i in 0 until 3) {
                        val y = H * (0.62f + i * 0.11f)
                        drawLine(color = col(i).copy(alpha = 0.7f), start = androidx.compose.ui.geometry.Offset(20.dp.toPx(), y), end = androidx.compose.ui.geometry.Offset(W - 20.dp.toPx(), y), strokeWidth = 3.dp.toPx())
                    }
                    drawCircle(color = col(3).copy(alpha = 0.9f), radius = sunR, center = androidx.compose.ui.geometry.Offset(cx, sunY))
                    for (i in 0 until 30) {
                        drawCircle(color = col(i).copy(alpha = 0.45f), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset((rnd() * W).toFloat(), (rnd() * H).toFloat()))
                    }
                }
                CoverVariant.ORBIT -> {
                    val cx = W / 2f; val cy = H / 2f
                    drawCircle(color = col(0).copy(alpha = 0.9f), radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy))
                    for (ring in 1..3) {
                        val r = minWH * (0.18f + ring * 0.13f)
                        drawCircle(color = col(ring).copy(alpha = 0.6f), radius = r, style = Stroke(width = 2.dp.toPx()), center = androidx.compose.ui.geometry.Offset(cx, cy))
                        val nDots = 3 + ring * 2
                        for (k in 0 until nDots) {
                            val a = (k.toFloat() / nDots) * 2f * PI.toFloat() + ring * 0.7f
                            drawCircle(color = col(ring + k).copy(alpha = 0.95f), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + cos(a) * r, cy + sin(a) * r))
                        }
                    }
                }
            }
        }

        if (showStrip) {
            // Bottom meta strip (drawStrip equivalent) — only on larger covers
            val stripHeight = 52.dp
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(base.copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Accent hairline on top of the strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(CoverArtGenerator.PALETTE[((seed % CoverArtGenerator.PALETTE.size) + CoverArtGenerator.PALETTE.size) % CoverArtGenerator.PALETTE.size].copy(alpha = 0.9f))
                        .height(2.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // hairline
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (title.trim().firstOrNull()?.uppercase() ?: "—").toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                    )
                    Text(
                        "  ${ext.replace(".", "").uppercase()} · ${Format.bytes(sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoverArtGenerator.DIM,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** Convenience: load seed for a track and show its procedural cover. */
@Composable
fun TrackProceduralCover(
    track: TrackEntity,
    modifier: Modifier = Modifier,
    showStrip: Boolean = true,
) {
    val context = LocalContext.current
    val seed by produceState(initialValue = 0, track.uri, track.sizeBytes) {
        value = CoverArtGenerator.seedForTrack(context, track)
    }
    if (seed == 0) {
        // Seed 0 is valid but rare; fall back to instant preview seed to avoid flicker
        ProceduralCoverArt(
            seed = CoverArtGenerator.seedForPreview(track.title, track.sizeBytes),
            title = track.title,
            ext = track.mimeType.substringAfter("/", ""),
            sizeBytes = track.sizeBytes,
            modifier = modifier,
            showStrip = showStrip,
        )
    } else {
        ProceduralCoverArt(
            seed = seed,
            title = track.title,
            ext = track.mimeType.substringAfter("/", ""),
            sizeBytes = track.sizeBytes,
            modifier = modifier,
            showStrip = showStrip,
        )
    }
}
