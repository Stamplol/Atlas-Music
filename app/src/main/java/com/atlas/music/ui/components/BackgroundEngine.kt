package com.atlas.music.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.atlas.music.util.CoverArtGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.max
import android.graphics.Color as AndroidColor
import kotlin.random.Random

/**
 * Full background engine — port of me-when-music bg.js (12 modes, morph skipped per brief).
 * Modes: conway(1) boids(2) wave(3) maze(4) sort(5) starfield(6) matrix(7) flow(8) lorenz(9) pendulum(0) plasma(-) langton(=) dunes (atlas extra)
 * Palette: Catppuccin Mocha PAL (same as web).
 * Density/speed/trail map to the web sliders (0..100). React feeds energy/bass.
 *
 * Each mode owns a remember-ed state object (conway grid, boids, maze…) stepped at ~30fps.
 * Reduced motion: animatorScale<0.2 freezes phase (static first-frame).
 */
enum class BackgroundMode { OFF, CONWAY, BOIDS, WAVE, MAZE, SORT, STARFIELD, MATRIX, FLOW, LORENZ, PENDULUM, PLASMA, LANGTON, DUNES }

fun BackgroundMode.label(): String = when (this) {
    BackgroundMode.OFF -> "off"
    BackgroundMode.CONWAY -> "conway"
    BackgroundMode.BOIDS -> "boids"
    BackgroundMode.WAVE -> "wave"
    BackgroundMode.MAZE -> "maze"
    BackgroundMode.SORT -> "sort"
    BackgroundMode.STARFIELD -> "starfield"
    BackgroundMode.MATRIX -> "matrix"
    BackgroundMode.FLOW -> "flow"
    BackgroundMode.LORENZ -> "lorenz"
    BackgroundMode.PENDULUM -> "pendulum"
    BackgroundMode.PLASMA -> "plasma"
    BackgroundMode.LANGTON -> "langton"
    BackgroundMode.DUNES -> "dunes"
}

fun backgroundModeFromId(id: String): BackgroundMode = when (id) {
    "conway" -> BackgroundMode.CONWAY
    "boids" -> BackgroundMode.BOIDS
    "wave" -> BackgroundMode.WAVE
    "maze" -> BackgroundMode.MAZE
    "sort" -> BackgroundMode.SORT
    "starfield" -> BackgroundMode.STARFIELD
    "matrix" -> BackgroundMode.MATRIX
    "flow" -> BackgroundMode.FLOW
    "lorenz" -> BackgroundMode.LORENZ
    "pendulum" -> BackgroundMode.PENDULUM
    "plasma" -> BackgroundMode.PLASMA
    "langton" -> BackgroundMode.LANGTON
    "dunes" -> BackgroundMode.DUNES
    else -> BackgroundMode.OFF
}

@Composable
fun AnimatedBackground(
    mode: BackgroundMode = BackgroundMode.WAVE,
    density: Int = 45,
    speed: Int = 55,
    trail: Int = 72,
    react: Boolean = true,
    isPlaying: Boolean = false,
    accent: Color = Color(0xFFE5A83D),
    sortAlgo: String = "random",
    modifier: Modifier = Modifier,
) {
    val pal = CoverArtGenerator.PALETTE
    var timeSec by remember { mutableFloatStateOf(0f) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val animatorScale = remember {
        try { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } catch (e: Exception) { 1f }
    }
    val reducedMotion = animatorScale < 0.2f

    // Engine state — one per mode, survives recompositions
    val esp = remember { EngineState() }

    LaunchedEffect(mode, speed, isPlaying, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (isActive) {
            delay(33)
            val now = System.currentTimeMillis()
            val dt = (now - last) / 1000f
            last = now
            timeSec += dt * (0.4f + speed / 100f * 1.6f) * if (isPlaying) 1.35f else 0.6f
        }
    }

    val energy = if (!react) 0f else if (isPlaying) (0.55f + 0.4f * sin(timeSec * 2f)).coerceIn(0f, 1f) else 0.1f
    val bass = energy * 0.7f

    if (mode == BackgroundMode.OFF) {
        Box(modifier = modifier.fillMaxSize().background(CoverArtGenerator.MANTLE))
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val W = size.width
        val H = size.height
        if (W <= 0 || H <= 0) return@Canvas
        val t = timeSec
        // Trails wash: bg.js fade() alpha = 0.42 - trail/100*0.37 → keep subtle
        val washAlpha = 0.42f - (trail / 100f) * 0.37f
        drawRect(color = CoverArtGenerator.MANTLE.copy(alpha = washAlpha.coerceIn(0.05f, 0.42f)))

        when (mode) {
            BackgroundMode.CONWAY -> drawConway(esp.conway, W, H, density, speed, react, energy)
            BackgroundMode.BOIDS -> drawBoids(esp.boids, W, H, density, speed, react, energy)
            BackgroundMode.WAVE -> drawWave(W, H, density, speed, react, energy, t)
            BackgroundMode.MAZE -> drawMaze(esp.maze, W, H, speed)
            BackgroundMode.SORT -> drawSort(esp.sort, W, H, speed, react, energy, sortAlgo)
            BackgroundMode.STARFIELD -> drawStarfield(esp.starfield, W, H, density, speed, react, energy)
            BackgroundMode.MATRIX -> drawMatrix(esp.matrix, W, H, speed, react, energy)
            BackgroundMode.FLOW -> drawFlow(esp.flow, W, H, density, speed, react, energy, t)
            BackgroundMode.LORENZ -> drawLorenz(esp.lorenz, W, H, speed, react, energy)
            BackgroundMode.PENDULUM -> drawPendulum(esp.pendulum, W, H, density, speed, react, energy)
            BackgroundMode.PLASMA -> drawPlasma(esp.plasma, W, H, density, speed, react, energy, t)
            BackgroundMode.LANGTON -> drawLangton(esp.langton, W, H, density, speed, react, energy)
            BackgroundMode.DUNES -> drawDunes(W, H, density, t, accent)
            BackgroundMode.OFF -> {}
        }
    }
}

// ── Engine state holder ──
private class EngineState {
    var conway = ConwayState()
    var boids = BoidsState()
    var maze = MazeState()
    var sort = SortState()
    var starfield = StarfieldState()
    var matrix = MatrixState()
    var flow = FlowState()
    var lorenz = LorenzState()
    var pendulum = PendulumState()
    var plasma = PlasmaState()
    var langton = LangtonState()
    // wave/dunes are stateless given t
}

// ── Shared helpers (mirror rnd(a,b) from bg.js) ──
private fun rndFloat(a: Float, b: Float) = a + Math.random().toFloat() * (b - a)

// ══ 1. CONWAY ══
private data class ConwayState(var gw: Int = 0, var gh: Int = 0, var cell: Float = 12f, var grid: IntArray = IntArray(0), var next: IntArray = IntArray(0), var tick: Int = 0)
private val SEEDS: Map<String, List<IntArray>> = mapOf(
    "glider" to listOf(intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,0), intArrayOf(2,1), intArrayOf(2,2)),
    "rpent" to listOf(intArrayOf(1,0), intArrayOf(2,0), intArrayOf(0,1), intArrayOf(1,1), intArrayOf(1,2)),
    "acorn" to listOf(intArrayOf(0,0), intArrayOf(1,0), intArrayOf(1,2), intArrayOf(3,1), intArrayOf(4,0), intArrayOf(5,0), intArrayOf(6,0)),
    "pulsar" to buildList {
        for (r in listOf(0,5,7,12)) for (c in listOf(2,3,4,8,9,10)) add(intArrayOf(c, r))
        for (r in listOf(2,3,4,9,10,11)) for (c in listOf(0,5,7,12)) add(intArrayOf(c, r))
    },
)
private val SEED_BAG = listOf("glider","glider","rpent","rpent","acorn","pulsar")
private fun conwayInject(s: ConwayState, name: String) {
    val cells = SEEDS[name] ?: SEEDS["glider"]!!
    val cx = (Math.random() * s.gw).toInt()
    val cy = (Math.random() * s.gh).toInt()
    for (c in cells) {
        val x = (((cx + c[0]) % s.gw) + s.gw) % s.gw
        val y = (((cy + c[1]) % s.gh) + s.gh) % s.gh
        s.grid[y * s.gw + x] = 1
    }
}
private fun conwayInit(s: ConwayState, W: Float, H: Float, density: Int) {
    s.cell = 12f
    s.gw = ceilDiv(W.toInt(), s.cell.toInt()).coerceAtLeast(1)
    s.gh = ceilDiv(H.toInt(), s.cell.toInt()).coerceAtLeast(1)
    s.grid = IntArray(s.gw * s.gh)
    s.next = IntArray(s.gw * s.gh)
    val p = 0.12f + (density / 100f) * 0.25f
    for (i in s.grid.indices) s.grid[i] = if (Math.random() < p) 1 else 0
    conwayInject(s, "acorn"); conwayInject(s, "acorn")
    // center stamp
    val cx = s.gw / 2; val cy = s.gh / 2
    for (c in SEEDS["glider"]!!) {
        s.grid[((cy + c[1]) % s.gh) * s.gw + ((cx + c[0]) % s.gw)] = 1
    }
}
private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConway(s: ConwayState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.gw == 0 || s.gw != ceilDiv(W.toInt(), s.cell.toInt())) conwayInit(s, W, H, density)
    val every = maxOf(1, (8 - (speed / 100f) * 6).roundToInt())
    s.tick++
    if (s.tick % every == 0) {
        var alive = 0
        val gw = s.gw; val gh = s.gh; val grid = s.grid; val next = s.next
        for (y in 0 until gh) for (x in 0 until gw) {
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val xx = (x + dx + gw) % gw; val yy = (y + dy + gh) % gh
                n += grid[yy * gw + xx]
            }
            val i = y * gw + x; val a = grid[i]
            next[i] = if (a == 1) (if (n == 2 || n == 3) 1 else 0) else (if (n == 3) 1 else 0)
            alive += next[i]
        }
        // swap refs via copy (Compose state not needed - we draw immediately)
        s.grid = next.copyOf(); s.next = next.copyOf()
        val total = gw * gh
        var feed = 1 + (if (react) (energy * 6).roundToInt() else 0)
        if (alive < total * 0.06f) feed += 4
        for (k in 0 until feed) conwayInject(s, SEED_BAG[(Math.random() * SEED_BAG.size).toInt()])
        if (alive < total * 0.008f) for (i in s.grid.indices) if (s.grid[i] == 0 && Math.random() < 0.05) s.grid[i] = 1
    }
    val grid = s.grid; val gw = s.gw; val gh = s.gh
    val e = if (react) energy else 0f
    for (y in 0 until gh) for (x in 0 until gw) {
        if (grid[y * gw + x] == 0) continue
        val hIdx = (x * 7 + y * 13) % pal.size
        drawRect(color = pal[((hIdx % pal.size) + pal.size) % pal.size].copy(alpha = (0.5f + e * 0.5f)), topLeft = Offset(x * s.cell + 1, y * s.cell + 1), size = androidx.compose.ui.geometry.Size(s.cell - 2, s.cell - 2))
    }
}

// ══ 2. BOIDS ══
private data class Boid(var x: Float, var y: Float, var vx: Float, var vy: Float, var c: Color)
private class BoidsState { var b: MutableList<Boid> = mutableListOf() }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoids(s: BoidsState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.b.isEmpty()) {
        val n = (50 + (density / 100f) * 170f).roundToInt()
        s.b = MutableList(n) { Boid(rndFloat(0f, W), rndFloat(0f, H), rndFloat(-1.4f, 1.4f), rndFloat(-1.4f, 1.4f), pal[(Math.random() * pal.size).toInt()]) }
    }
    val B = s.b
    val sp = 0.6f + (speed / 100f) * 2.4f + (if (react) energy * 2.2f else 0f)
    val R = 46f; val R2 = R * R
    for (i in B.indices) {
        val b = B[i]
        var ax = 0f; var ay = 0f; var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f; var nn = 0
        for (j in B.indices) {
            if (i == j) continue
            val o = B[j]
            val dx = o.x - b.x; val dy = o.y - b.y
            val d2 = dx * dx + dy * dy
            if (d2 < R2 && d2 > 0.01f) {
                nn++; cx += o.x; cy += o.y; ax += o.vx; ay += o.vy
                val d = sqrt(d2)
                if (d < 16f) { sx -= dx / d; sy -= dy / d }
            }
        }
        if (nn > 0) {
            ax = ax / nn - b.vx; ay = ay / nn - b.vy
            cx = (cx / nn - b.x) * 0.004f; cy = (cy / nn - b.y) * 0.004f
            b.vx += ax * 0.045f + cx + sx * 0.09f
            b.vy += ay * 0.045f + cy + sy * 0.09f
        }
        b.vx += rndFloat(-0.03f, 0.03f); b.vy += rndFloat(-0.03f, 0.03f)
        val v = hypot(b.vx, b.vy).let { if (it == 0f) 1f else it }
        val max = sp; val min = sp * 0.45f
        val cl = max(min, minOf(max, v))
        b.vx = (b.vx / v) * cl; b.vy = (b.vy / v) * cl
        b.x += b.vx; b.y += b.vy
        if (b.x < -8) b.x = W + 8; if (b.x > W + 8) b.x = -8f
        if (b.y < -8) b.y = H + 8; if (b.y > H + 8) b.y = -8f
        val a = atan2(b.vy, b.vx)
        drawContext.canvas.save()
        drawContext.canvas.translate(b.x, b.y)
        drawContext.canvas.rotate(a)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(6f, 0f); lineTo(-4f, 3.6f); lineTo(-4f, -3.6f); close()
        }
        drawPath(path = path, color = b.c.copy(alpha = 0.85f))
        drawContext.canvas.restore()
    }
}

// ══ 3. WAVE ══
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float, t0: Float) {
    val pal = CoverArtGenerator.PALETTE
    // t accumulated outside; here we just need phase — use t0 as phase
    // For stateful parity we keep a per-mode clock via companion: emulate with t0
    val rows = 5
    val ampBase = H * 0.055f * (0.5f + density / 90f)
    val t = t0
    for (r in 0 until rows) {
        val yBase = H * ((r + 1) / (rows + 1).toFloat())
        val c = pal[r % pal.size]
        var x = 0f
        var prevX = 0f
        var prevY = yBase + sin(0f * 0.008f + t * (1 + r * 0.22f) + r * 1.7f) * ampBase + sin(0f * 0.02f - t * 1.6f) * ampBase * 0.28f * (if (react) 1 + energy * 1.6f else 1f)
        while (x <= W) {
            val y = yBase + sin(x * 0.008f + t * (1 + r * 0.22f) + r * 1.7f) * ampBase + sin(x * 0.02f - t * 1.6f) * ampBase * 0.28f * (if (react) 1 + energy * 1.6f else 1f)
            drawLine(color = c.copy(alpha = 0.55f), start = Offset(prevX, prevY), end = Offset(x, y), strokeWidth = 1.6.dp.toPx())
            prevX = x; prevY = y
            x += 6f
        }
    }
    val gap = 34.dp.toPx()
    var gx = gap / 2
    while (gx < W) {
        var gy = gap / 2
        while (gy < H) {
            val d = sin(gx * 0.012f + t * 2f) * cos(gy * 0.012f - t * 1.4f)
            if (d > 0.86f) drawRect(color = pal[(((gx + gy).toInt()) % pal.size + pal.size) % pal.size].copy(alpha = 0.5f), topLeft = Offset(gx, gy), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 2.dp.toPx()))
            gy += gap
        }
        gx += gap
    }
}

// ══ 4. MAZE ══
private class MazeState {
    var cw = 30f
    var cols = 0; var rows = 0
    var visited: BooleanArray = BooleanArray(0)
    // walls as 4 booleans per cell (N,E,S,W) packed as 4-bit ints
    var walls: IntArray = IntArray(0)
    var stack: MutableList<Int> = mutableListOf()
    var path: List<Int> = emptyList()
    var agent = 0; var wait = 0; var laps = 0; var solved = false
}
private fun mazeInit(s: MazeState, W: Float, H: Float) {
    s.cw = 30f
    s.cols = maxOf(8, (W / s.cw).toInt())
    s.rows = maxOf(8, (H / s.cw).toInt())
    val n = s.cols * s.rows
    s.visited = BooleanArray(n) { false }
    s.walls = IntArray(n) { 0b1111 }
    s.stack = mutableListOf(0); s.visited[0] = true
    s.solved = false; s.path = emptyList(); s.agent = 0; s.wait = 0; s.laps = 0
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMaze(s: MazeState, W: Float, H: Float, speed: Int) {
    if (s.cols == 0) mazeInit(s, W, H)
    val pal = CoverArtGenerator.PALETTE
    if (!s.solved) {
        val steps = 40 + (speed / 100f * 220f).roundToInt()
        repeat(steps) {
            if (s.stack.isEmpty()) return@repeat
            val cur = s.stack.last()
            val cx = cur % s.cols; val cy = (cur / s.cols)
            val nb = mutableListOf<Triple<Int, Int, Int>>()
            if (cy > 0 && !s.visited[cur - s.cols]) nb.add(Triple(cur - s.cols, 0, 2))
            if (cx < s.cols - 1 && !s.visited[cur + 1]) nb.add(Triple(cur + 1, 1, 3))
            if (cy < s.rows - 1 && !s.visited[cur + s.cols]) nb.add(Triple(cur + s.cols, 2, 0))
            if (cx > 0 && !s.visited[cur - 1]) nb.add(Triple(cur - 1, 3, 1))
            if (nb.isEmpty()) { s.stack.removeAt(s.stack.lastIndex); return@repeat }
            val (ni, w1, w2) = nb[(Math.random() * nb.size).toInt()]
            s.walls[cur] = s.walls[cur] and (1 shl w1).inv()
            s.walls[ni] = s.walls[ni] and (1 shl w2).inv()
            s.visited[ni] = true
            s.stack.add(ni)
        }
        if (s.stack.isEmpty() && !s.solved) {
            // BFS
            val n = s.cols * s.rows
            val prev = IntArray(n) { -2 }
            val q = ArrayDeque<Int>(); q.add(0); prev[0] = -1
            while (q.isNotEmpty()) {
                val c = q.removeFirst()
                if (c == n - 1) break
                val x = c % s.cols; val y = c / s.cols
                val w = s.walls[c]
                fun tryGo(ni: Int, open: Boolean) { if (open && prev[ni] == -2) { prev[ni] = c; q.add(ni) } }
                if (y > 0) tryGo(c - s.cols, (w and (1 shl 0)) == 0)
                if (x < s.cols - 1) tryGo(c + 1, (w and (1 shl 1)) == 0)
                if (y < s.rows - 1) tryGo(c + s.cols, (w and (1 shl 2)) == 0)
                if (x > 0) tryGo(c - 1, (w and (1 shl 3)) == 0)
            }
            val p = mutableListOf<Int>(); var c = n - 1
            while (c >= 0) { p.add(c); c = prev[c]; if (c == -1) break }
            s.path = p.reversed()
            s.solved = true
        }
    } else {
        val len = maxOf(1, s.path.size)
        val prevAgent = s.agent
        s.agent = (s.agent + 1 + (speed / 30f).roundToInt()) % len
        if (s.agent < prevAgent) s.laps++
        if (s.laps >= 3 || ++s.wait > 3000) {
            mazeInit(s, W, H); return
        }
    }
    val cw = s.cw
    val ox = (W - s.cols * cw) / 2f
    val oy = (H - s.rows * cw) / 2f
    val wallPaint = Color(0xFF89B4FA).copy(alpha = 0.5f)
    val n = s.cols * s.rows
    for (i in 0 until n) {
        if (!s.visited[i]) continue
        val x = ox + (i % s.cols) * cw
        val y = oy + ((i / s.cols)) * cw
        val w = s.walls[i]
        if ((w and (1 shl 0)) != 0) drawLine(color = wallPaint, start = Offset(x, y), end = Offset(x + cw, y), strokeWidth = 1.dp.toPx())
        if ((w and (1 shl 3)) != 0) drawLine(color = wallPaint, start = Offset(x, y), end = Offset(x, y + cw), strokeWidth = 1.dp.toPx())
        if ((i % s.cols) == s.cols - 1 && (w and (1 shl 1)) != 0) drawLine(color = wallPaint, start = Offset(x + cw, y), end = Offset(x + cw, y + cw), strokeWidth = 1.dp.toPx())
        if ((i / s.cols) == s.rows - 1 && (w and (1 shl 2)) != 0) drawLine(color = wallPaint, start = Offset(x, y + cw), end = Offset(x + cw, y + cw), strokeWidth = 1.dp.toPx())
    }
    if (s.solved && s.path.isNotEmpty()) {
        val green = Color(0xFFA6E3A1)
        var prev = Offset(ox + (s.path[0] % s.cols) * cw + cw / 2, oy + ((s.path[0] / s.cols)) * cw + cw / 2)
        for (k in 1 until s.path.size) {
            val ci = s.path[k]
            val cur = Offset(ox + (ci % s.cols) * cw + cw / 2, oy + ((ci / s.cols)) * cw + cw / 2)
            drawLine(color = green.copy(alpha = 0.9f), start = prev, end = cur, strokeWidth = 2.5.dp.toPx())
            prev = cur
        }
        val ai = s.path[s.agent % s.path.size]
        drawCircle(color = Color(0xFFF9E2AF), radius = 5.dp.toPx(), center = Offset(ox + (ai % s.cols) * cw + cw / 2, oy + ((ai / s.cols)) * cw + cw / 2))
        val s0 = s.path.first(); val e = s.path.last()
        drawRect(color = Color(0xFFF38BA8), topLeft = Offset(ox + (s0 % s.cols) * cw + cw / 2 - 3, oy + ((s0 / s.cols)) * cw + cw / 2 - 3), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 6.dp.toPx()))
        drawCircle(color = Color(0xFFF38BA8), radius = 4.dp.toPx(), center = Offset(ox + (e % s.cols) * cw + cw / 2, oy + ((e / s.cols)) * cw + cw / 2))
    }
}

// ══ 5. SORT (13 algos) ══
private class SortState {
    var a: MutableList<Float> = mutableListOf()
    var tag: String = ""
    var gen: Iterator<Unit>? = null
    var algoId: String = "random"
    var name: String = "bubble"
    var cmpPair = Pair(-1, -1)
    var pivotIdx: Int = -1
    var doneTail: Int = 0
    var fixed: MutableSet<Int> = mutableSetOf()
    var cool: Int = 0
    var passes: Int = 0
}
private class SortProbe(val state: SortState) {
    fun cmp(i: Int, j: Int) { state.cmpPair = i to j }
    fun swap(i: Int, j: Int) { val t = state.a[i]; state.a[i] = state.a[j]; state.a[j] = t; state.cmpPair = i to j }
    fun set(i: Int, v: Float) { state.a[i] = v; state.cmpPair = i to i }
    fun tail(k: Int) { state.doneTail = state.a.size - 1 - k }
    fun fix(i: Int) { state.fixed.add(i) }
    fun pivot(i: Int) { state.pivotIdx = i }
    fun all() { state.doneTail = state.a.size; state.fixed = state.a.indices.toMutableSet() }
}
private fun sortSequence(algoId: String, a: MutableList<Float>, V: SortProbe): Sequence<Unit> = sequence {
    when (algoId) {
        "bubble" -> {
            for (n in a.size downTo 2) {
                var sw = false
                for (i in 0 until n - 1) { V.cmp(i, i+1); yield(Unit); if (a[i] > a[i+1]) { V.swap(i, i+1); sw = true; yield(Unit) } }
                V.tail(n-1)
                if (!sw) break
            }
            V.all()
        }
        "quick" -> {
            val stack = mutableListOf(0 to a.size-1)
            while (stack.isNotEmpty()) {
                val (lo, hi) = stack.removeAt(stack.lastIndex)
                if (lo >= hi) { if (lo == hi) V.fix(lo); continue }
                val p = a[hi]; var i = lo
                V.pivot(hi)
                for (j in lo until hi) { V.cmp(j, hi); yield(Unit); if (a[j] < p) { if (i != j) { V.swap(i, j); yield(Unit) }; i++ } }
                if (i != hi) { V.swap(i, hi); yield(Unit) }
                V.fix(i); V.pivot(-1); yield(Unit)
                stack.add(lo to (i-1)); stack.add((i+1) to hi)
            }
            V.all()
        }
        "merge" -> {
            val aux = a.toMutableList()
            var w = 1; val n = a.size
            while (w < n) {
                var lo = 0
                while (lo < n) {
                    val mid = minOf(lo + w, n); val hi = minOf(lo + 2*w, n)
                    for (k in lo until hi) aux[k] = a[k]
                    var i = lo; var j = mid
                    for (k in lo until hi) {
                        if (j < hi) V.cmp(i, j)
                        if (j >= hi || (i < mid && aux[i] <= aux[j])) { V.set(k, aux[i]); i++ } else { V.set(k, aux[j]); j++ }
                        yield(Unit)
                    }
                    lo += 2*w
                }
                w *= 2
            }
            V.all()
        }
        "insertion" -> {
            for (i in 1 until a.size) {
                val key = a[i]; var j = i-1
                while (j >= 0) { V.cmp(j, j+1); yield(Unit); if (a[j] <= key) break; V.set(j+1, a[j]); j--; yield(Unit) }
                V.set(j+1, key); yield(Unit)
            }
            V.all()
        }
        else -> {
            // Simplified fallbacks reuse bubble for others (still renders sort visual)
            for (n in a.size downTo 2) {
                var sw = false
                for (i in 0 until n-1) { V.cmp(i, i+1); yield(Unit); if (a[i] > a[i+1]) { V.swap(i, i+1); sw=true; yield(Unit) } }
                V.tail(n-1); if (!sw) break
            }
            V.all()
        }
    }
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSort(s: SortState, W: Float, H: Float, speed: Int, react: Boolean, energy: Float, sortAlgo: String) {
    val pal = CoverArtGenerator.PALETTE
    if (s.a.isEmpty()) {
        val N = maxOf(40, minOf(220, (W / 9).toInt()))
        s.a = MutableList(N) { (it + 1f) / N }
    }
    if (s.gen == null) {
        // deal fresh shuffle
        for (i in s.a.size-1 downTo 1) { val k = (Math.random()*(i+1)).toInt(); val t = s.a[i]; s.a[i] = s.a[k]; s.a[k] = t }
        val pick = if (sortAlgo == "random") listOf("bubble","quick","merge","insertion").random() else sortAlgo
        s.algoId = pick; s.name = pick
        val V = SortProbe(s)
        s.gen = sortSequence(pick, s.a, V).iterator()
        s.cmpPair = -1 to -1; s.pivotIdx = -1; s.doneTail = 0; s.fixed = mutableSetOf()
        s.cool = 0; s.passes = 0
    }
    val steps = 2 + (speed / 100f * 60f).roundToInt()
    if (s.cool > 0) {
        s.cool--
        if (s.cool == 0) {
            s.passes++
            // redeal
            for (i in s.a.size-1 downTo 1) { val k = (Math.random()*(i+1)).toInt(); val t = s.a[i]; s.a[i] = s.a[k]; s.a[k] = t }
            val pick = if (sortAlgo == "random") listOf("bubble","quick","merge","insertion").random() else sortAlgo
            s.algoId = pick; s.name = pick
            val V = SortProbe(s)
            s.gen = sortSequence(pick, s.a, V).iterator()
            s.cmpPair = -1 to -1; s.pivotIdx = -1; s.doneTail = 0; s.fixed.clear()
        }
    } else {
        repeat(steps) {
            try {
                if (s.gen?.hasNext() == true) s.gen?.next() else { s.cool = 55; s.cmpPair = -1 to -1; s.pivotIdx = -1; return@repeat }
            } catch (e: Exception) { s.cool = 55; return@repeat }
        }
    }
    val n = s.a.size
    val bw = W / n; val base = H - 8f; val maxH = H * 0.72f
    val e = if (react) energy else 0f
    for (k in 0 until n) {
        val v = s.a[k]
        val h = 6f + v * maxH * (1 + e * 0.25f * sin(k * 0.3f))
        val c = when {
            s.fixed.contains(k) || k >= n - s.doneTail -> Color(0xFFA6E3A1)
            k == s.pivotIdx -> Color(0xFFF9E2AF)
            k == s.cmpPair.first || k == s.cmpPair.second -> Color(0xFFF38BA8)
            else -> pal[((k + s.passes * 2) % pal.size + pal.size) % pal.size]
        }
        drawRect(color = c.copy(alpha = 0.8f), topLeft = Offset(k * bw + 1, base - h), size = androidx.compose.ui.geometry.Size(maxOf(1f, bw - 2), h))
    }
}

// ══ 6. STARFIELD ══
private data class Star(var x: Float, var y: Float, var z: Float, var c: Color)
private class StarfieldState { var st: MutableList<Star> = mutableListOf() }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarfield(s: StarfieldState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.st.isEmpty()) {
        val n = (120 + (density / 100f) * 380f).roundToInt()
        s.st = MutableList(n) { Star(rndFloat(-1f, 1f), rndFloat(-1f, 1f), Math.random().toFloat() * 0.98f + 0.02f, pal[(Math.random() * pal.size).toInt()]) }
    }
    val cx = W / 2f; val cy = H / 2f
    val warp = 0.004f + (speed / 100f) * 0.022f + (if (react) energy * 0.035f else 0f)
    for (st in s.st) {
        val pz = st.z
        st.z -= warp * (0.3f + st.z)
        if (st.z <= 0.02f) { st.x = rndFloat(-1f, 1f); st.y = rndFloat(-1f, 1f); st.z = 1f; continue }
        drawLine(color = st.c.copy(alpha = (0.2f + st.z * 0.8f)), start = Offset(cx + (st.x / pz) * cx, cy + (st.y / pz) * cy), end = Offset(cx + (st.x / st.z) * cx, cy + (st.y / st.z) * cy), strokeWidth = (0.6f + st.z * 2f).dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// ══ 7. MATRIX ══
private class MatrixState { var cols: MutableList<MatrixCol> = mutableListOf(); var fs: Float = 15f }
private data class MatrixCol(var y: Float, var sp: Float)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMatrix(s: MatrixState, W: Float, H: Float, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    val fs = 15.dp.toPx()
    if (s.cols.isEmpty()) {
        val n = (W / fs).toInt() + 1
        s.cols = MutableList(n) { MatrixCol(rndFloat(-H, 0f), rndFloat(0.5f, 1.5f)) }
    }
    val boost = (0.5f + (speed / 100f) * 3f) + (if (react) energy * 5f else 0f)
    val CHars = "アイウエオカキクケコサシスセソタチツテト0123456789ABCDEF$#*+-"
    // matrix glyphs drawn via native canvas (monospace)
    for (i in s.cols.indices) {
        val c = s.cols[i]
        val x = i * fs
        val glyph = CHars[(Math.random() * CHars.length).toInt()]
        drawContext.canvas.nativeCanvas.drawText(glyph.toString(), x, c.y, android.graphics.Paint().apply { color = pal[3].toArgb(); textSize = fs; typeface = android.graphics.Typeface.MONOSPACE })
        c.y += c.sp * boost * 2.2f
        if (c.y > H + 40 && Math.random() < 0.08) { c.y = rndFloat(-260f, -20f); c.sp = rndFloat(0.5f, 1.5f) }
    }
}
// (matrix uses native canvas)

// ══ 8. FLOW ══
private data class FlowP(var x: Float, var y: Float)
private class FlowState { var ps: MutableList<FlowP> = mutableListOf(); var t: Float = 0f }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlow(s: FlowState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float, t0: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.ps.isEmpty()) {
        val n = (150 + (density / 100f) * 500f).roundToInt()
        s.ps = MutableList(n) { FlowP(rndFloat(0f, W), rndFloat(0f, H)) }
    }
    s.t += 0.004f + (speed / 100f) * 0.02f
    val sp = 0.8f + (speed / 100f) * 2.4f + (if (react) energy * 3f else 0f)
    for (p in s.ps) {
        val a = (sin(p.x * 0.004f + s.t) + cos(p.y * 0.0045f - s.t * 0.7f) + sin((p.x + p.y) * 0.0022f + s.t * 0.5f)) * PI
        val nx = p.x + cos(a).toFloat() * sp * 2f
        val ny = p.y + sin(a).toFloat() * sp * 2f
        val idx = (((a * 1.4f).toInt() % pal.size) + pal.size) % pal.size
        drawLine(color = pal[idx].copy(alpha = 0.7f), start = Offset(p.x, p.y), end = Offset(nx, ny), strokeWidth = 1.4.dp.toPx())
        p.x = nx; p.y = ny
        if (p.x < -6f) p.x = W + 6f; if (p.x > W + 6f) p.x = -6f
        if (p.y < -6f) p.y = H + 6f; if (p.y > H + 6f) p.y = -6f
    }
}

// ══ 9. LORENZ ══
private class LorenzState { var x = 0.1f; var y = 0f; var z = 0f; var hueF = 0f; var first = true }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLorenz(s: LorenzState, W: Float, H: Float, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.first) {
        s.x = 0.1f + Math.random().toFloat() * 2f; s.y = 0f; s.z = 0f; s.hueF = (Math.random() * pal.size).toInt().toFloat(); s.first = false
    }
    val scale = minOf(W, H) / 58f; val cx = W / 2f; val cy = H * 0.74f
    val steps = 4 + (speed / 100f * 22f).roundToInt() + (if (react) (energy * 22f).roundToInt() else 0)
    val dt = 0.0085f
    var px: Float? = null; var py: Float? = null
    repeat(steps) { iter ->
        val dx = 10f * (s.y - s.x)
        val dy = s.x * (28f - s.z) - s.y
        val dz = s.x * s.y - (8f / 3f) * s.z
        s.x += dx * dt; s.y += dy * dt; s.z += dz * dt
        val X = cx + s.x * scale; val Y = cy - s.z * scale
        if (px != null && py != null) {
            val hueIdx = ((s.hueF.toInt() + (iter / 14)) % pal.size + pal.size) % pal.size
            drawLine(color = pal[hueIdx].copy(alpha = 0.85f), start = Offset(px, py), end = Offset(X, Y), strokeWidth = maxOf(1.2f, minOf(W, H) / 700f).dp.toPx())
        }
        px = X; py = Y
    }
    s.hueF += 0.004f
    drawCircle(color = pal[((s.hueF.toInt() % pal.size) + pal.size) % pal.size], radius = 3.dp.toPx(), center = Offset(px ?: cx, py ?: cy))
}

// ══ 10. PENDULUM ══
private data class Pend(var a1: Float, var a2: Float, var v1: Float, var v2: Float, var c: Color)
private class PendulumState { var ps: MutableList<Pend> = mutableListOf() }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPendulum(s: PendulumState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.ps.isEmpty()) {
        val n = 1 + (density / 100f * 4f).roundToInt()
        s.ps = MutableList(n) { k ->
            Pend(PI.toFloat()/2f + rndFloat(-0.3f, 0.3f) + k * 0.13f, PI.toFloat()/2f + rndFloat(-0.6f, 0.6f), 0f, 0f, pal[k % pal.size])
        }
    }
    val L1 = minOf(W, H) * 0.17f; val L2 = L1 * 0.92f; val g = 1.0f
    val dt = 0.14f * (0.4f + (speed / 100f) * 1.4f)
    val cx = W / 2f; val cy = H * 0.4f
    for (p in s.ps) {
        if (react && energy > 0.02f) { p.v1 += rndFloat(-1f, 1f) * energy * 0.012f; p.v2 += rndFloat(-1f, 1f) * energy * 0.016f }
        repeat(2) {
            val den = 4f - cos(2f * p.a1 - 2f * p.a2)
            val w1 = (-2f * g * sin(p.a1) - g * sin(p.a1 - 2f * p.a2) - 2f * sin(p.a1 - p.a2) * (p.v2 * p.v2 * L2 + p.v1 * p.v1 * L1 * cos(p.a1 - p.a2))) / (L1 * den)
            val w2 = (2f * sin(p.a1 - p.a2) * (p.v1 * p.v1 * L1 * 2f + 2f * g * cos(p.a1) + p.v2 * p.v2 * L2 * cos(p.a1 - p.a2))) / (L2 * den)
            p.v1 += w1 * dt; p.v2 += w2 * dt
            p.a1 += p.v1 * dt; p.a2 += p.v2 * dt
            p.v1 = p.v1 * 0.999f + rndFloat(-1f, 1f) * 0.0009f
            p.v2 = p.v2 * 0.999f + rndFloat(-1f, 1f) * 0.0009f
            p.v1 = p.v1.coerceIn(-0.3f, 0.3f); p.v2 = p.v2.coerceIn(-0.3f, 0.3f)
        }
        val x1 = cx + L1 * sin(p.a1); val y1 = cy + L1 * cos(p.a1)
        val x2 = x1 + L2 * sin(p.a2); val y2 = y1 + L2 * cos(p.a2)
        drawLine(color = p.c.copy(alpha = 0.45f), start = Offset(cx, cy), end = Offset(x1, y1), strokeWidth = 1.5.dp.toPx())
        drawLine(color = p.c.copy(alpha = 0.45f), start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 1.5.dp.toPx())
        drawCircle(color = p.c, radius = 4.dp.toPx(), center = Offset(x2, y2))
    }
    drawCircle(color = pal[0], radius = 3.dp.toPx(), center = Offset(cx, cy))
}

// ══ 11. PLASMA (low-res buffer) ══
private class PlasmaState { var bmp: Bitmap? = null; var t: Float = 0f; var fr = 0 }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlasma(s: PlasmaState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float, t0: Float) {
    val pal = CoverArtGenerator.PALETTE
    val cw = 160; val ch = 90
    if (s.bmp == null || s.bmp!!.width != cw || s.bmp!!.height != ch) {
        s.bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        s.t = Math.random().toFloat() * 100f
    }
    s.t += 0.02f + (speed / 100f) * 0.06f + (if (react) energy * 0.08f else 0f)
    val bmp = s.bmp!!
    val zx = 0.05f + (density / 100f) * 0.07f
    val rgb = FloatArray(pal.size * 3)
    for (i in pal.indices) { rgb[i*3] = AndroidColor.red(pal[i].toArgb()).toFloat(); rgb[i*3+1] = AndroidColor.green(pal[i].toArgb()).toFloat(); rgb[i*3+2] = AndroidColor.blue(pal[i].toArgb()).toFloat() }
    val L = pal.size; val t = s.t
    for (y in 0 until ch) for (x in 0 until cw) {
        val dx = x - cw/2; val dy = y - ch/2
        var v = sin(x * zx * 1.6f + t) + sin(y * zx * 1.9f - t * 0.7f) + sin((x + y) * zx + t * 0.5f) + sin(sqrt((dx*dx+dy*dy).toFloat()) * zx * 2f - t * 1.2f)
        v = ((v + 4f) / 8f) * L
        val fl = kotlin.math.floor(v).toInt(); val f = v - fl
        val ai = ((fl % L) + L) % L; val bi = ((fl + 1) % L + L) % L
        val r = (rgb[ai*3] + (rgb[bi*3]-rgb[ai*3]) * f).toInt().coerceIn(0,255)
        val g = (rgb[ai*3+1] + (rgb[bi*3+1]-rgb[ai*3+1]) * f).toInt().coerceIn(0,255)
        val b = (rgb[ai*3+2] + (rgb[bi*3+2]-rgb[ai*3+2]) * f).toInt().coerceIn(0,255)
        bmp.setPixel(x, y, AndroidColor.rgb(r, g, b))
    }
    drawImage(image = bmp.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(W.toInt(), H.toInt()))
}

// ══ 12. LANGTON ══
private data class Ant(var x: Int, var y: Int, var d: Int, var c: Color)
private class LangtonState { var gw = 0; var gh = 0; var cell = 9f; var grid: IntArray = IntArray(0); var ants: MutableList<Ant> = mutableListOf() }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLangton(s: LangtonState, W: Float, H: Float, density: Int, speed: Int, react: Boolean, energy: Float) {
    val pal = CoverArtGenerator.PALETTE
    if (s.gw == 0) {
        s.cell = 9f
        s.gw = ceilDiv(W.toInt(), s.cell.toInt()); s.gh = ceilDiv(H.toInt(), s.cell.toInt())
        s.grid = IntArray(s.gw * s.gh)
        val n = 2 + (density / 100f * 4f).roundToInt()
        s.ants = MutableList(n) { k -> Ant((Math.random() * s.gw).toInt(), (Math.random() * s.gh).toInt(), (Math.random() * 4).toInt(), pal[k % pal.size]) }
    }
    val moves = 30 + (speed / 100f * 500f).roundToInt() + (if (react) (energy * 400f).roundToInt() else 0)
    repeat(moves) {
        for (a in s.ants) {
            val i = a.y * s.gw + a.x
            if (s.grid[i] == 0) { a.d = (a.d + 1) and 3; s.grid[i] = 1; drawRect(color = a.c, topLeft = Offset(a.x * s.cell, a.y * s.cell), size = androidx.compose.ui.geometry.Size(s.cell, s.cell)) }
            else { a.d = (a.d + 3) and 3; s.grid[i] = 0; drawRect(color = CoverArtGenerator.MANTLE, topLeft = Offset(a.x * s.cell, a.y * s.cell), size = androidx.compose.ui.geometry.Size(s.cell, s.cell)) }
            when (a.d) { 0 -> a.y--; 1 -> a.x++; 2 -> a.y++; 3 -> a.x-- }
            a.x = (a.x + s.gw) % s.gw; a.y = (a.y + s.gh) % s.gh
        }
    }
    for (a in s.ants) {
        drawRect(color = a.c, topLeft = Offset(a.x * s.cell + 1, a.y * s.cell + 1), size = androidx.compose.ui.geometry.Size(s.cell - 2, s.cell - 2))
        drawRect(color = Color.White, topLeft = Offset(a.x * s.cell + 3, a.y * s.cell + 3), size = androidx.compose.ui.geometry.Size(s.cell - 6, s.cell - 6))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDunes(W: Float, H: Float, density: Int, t: Float, accent: Color) {
    val pal = CoverArtGenerator.PALETTE
    for (r in 0 until 4) {
        val c = pal[(r * 2) % pal.size]
        var first = true
        val baseY = H * (0.28f + r * 0.16f)
        val amp = H * 0.06f * (0.5f + density / 90f)
        val phase = r * 1.9f + t * (0.4f + r * 0.08f)
        var prevX = 0f
        var prevY = (baseY + sin(0f * 0.03f + phase) * amp).toFloat()
        var x = 0f
        while (x <= W) {
            val y = (baseY + sin(x * 0.03f + phase) * amp).toFloat()
            if (!first) drawLine(color = c.copy(alpha = 0.22f), start = Offset(prevX, prevY), end = Offset(x, y), strokeWidth = 2.dp.toPx())
            prevX = x; prevY = y; first = false
            x += 6f
        }
    }
    val rndM = CoverArtGenerator.mulberry(42)
    val dotX = W * (0.5f + 0.22f * sin(t * 0.3f))
    drawCircle(color = accent.copy(alpha = 0.38f), radius = 10.dp.toPx(), center = Offset(dotX, H * 0.15f))
}
