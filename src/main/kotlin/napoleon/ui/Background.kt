package napoleon.ui

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// 緑フェルト風の背景画像を生成する。
//
// アルゴリズム:
//  1. ベース色 (HSB)
//  2. 多重オクターブ Value ノイズ (FBM) で中周波の明度モジュレーション (布の不均一さ)
//  3. 高周波ホワイトノイズで粒感
//  4. 短い繊維線分を半透明描画
// FBM は両軸で同じ物理セルサイズを保つため、長方形でもブロブが等方に見える。
fun generateFeltBackground(
    width: Int,
    height: Int,
    seed: Long = 12345,
): BufferedImage {
    val brightness = 0.42f
    val hue = 0.33f
    val saturation = 0.45f
    val fbmAmplitude = 0.07f
    val fbmBaseCellPx = 32
    val fbmOctaves = 5
    val grainAmplitude = 0.04f
    val fiberDensity = 0.09f

    val random = Random(seed)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val fbm = Fbm(width, height, fbmBaseCellPx, fbmOctaves, random.nextLong())

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val mottle = fbm.sample(x, y) * fbmAmplitude
            val vGrain = (random.nextFloat() - 0.5f) * 2f * grainAmplitude
            val sGrain = (random.nextFloat() - 0.5f) * 2f * grainAmplitude
            val v = (brightness + mottle + vGrain).coerceIn(0f, 1f)
            val s = (saturation + sGrain).coerceIn(0f, 1f)
            pixels[y * width + x] = Color.HSBtoRGB(hue, s, v)
        }
    }
    image.setRGB(0, 0, width, height, pixels, 0, width)

    val fiberCount = (width.toLong() * height * fiberDensity).roundToInt()
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f)
    for (i in 0 until fiberCount) {
        val cx = random.nextInt(width)
        val cy = random.nextInt(height)
        val len = 2.5 + random.nextDouble() * 7.0
        val ang = random.nextDouble() * Math.PI * 2
        val dx = (cos(ang) * len).toInt()
        val dy = (sin(ang) * len).toInt()
        val v = (brightness + (random.nextFloat() - 0.5f) * 0.45f).coerceIn(0f, 1f)
        val s = (saturation + (random.nextFloat() - 0.5f) * 0.25f).coerceIn(0f, 1f)
        g.color = Color.getHSBColor(hue, s, v)
        g.drawLine(cx - dx / 2, cy - dy / 2, cx + dx / 2, cy + dy / 2)
    }
    g.dispose()
    return image
}

// Value ノイズの FBM 合成。出力は概ね [-1, 1]。
// 各オクターブで物理セルサイズを半分にする。両軸で同じ cellPx を使うので等方ブロブが得られる。
private class Fbm(
    width: Int,
    height: Int,
    baseCellPx: Int,
    octaves: Int,
    seed: Long,
) {
    private val layers: List<ValueNoiseLayer>

    init {
        val random = Random(seed)
        layers =
            (0 until octaves).map { i ->
                val cellPx = baseCellPx.toFloat() / (1 shl i)
                val cellsX = ceil(width / cellPx).toInt().coerceAtLeast(1) + 1
                val cellsY = ceil(height / cellPx).toInt().coerceAtLeast(1) + 1
                ValueNoiseLayer(cellsX, cellsY, cellPx, random.nextLong())
            }
    }

    fun sample(
        x: Int,
        y: Int,
    ): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        for (layer in layers) {
            sum += layer.sample(x, y) * amp
            norm += amp
            amp *= 0.5f
        }
        return sum / norm
    }
}

private class ValueNoiseLayer(
    private val cellsX: Int,
    cellsY: Int,
    private val cellPx: Float,
    seed: Long,
) {
    private val grid: FloatArray =
        Random(seed).let { r -> FloatArray(cellsX * cellsY) { r.nextFloat() * 2f - 1f } }

    fun sample(
        px: Int,
        py: Int,
    ): Float {
        val fx = px / cellPx
        val fy = py / cellPx
        val ix = floor(fx).toInt()
        val iy = floor(fy).toInt()
        val tx = smoothstep(fx - ix)
        val ty = smoothstep(fy - iy)
        val v00 = grid[iy * cellsX + ix]
        val v10 = grid[iy * cellsX + ix + 1]
        val v01 = grid[(iy + 1) * cellsX + ix]
        val v11 = grid[(iy + 1) * cellsX + ix + 1]
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), ty)
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(
        a: Float,
        b: Float,
        t: Float,
    ): Float = a + (b - a) * t
}
