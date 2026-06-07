package napoleon.ui

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private const val HALF_W = 10
private const val FULL_W = 20
private const val GLYPH_H = 20
private const val COLS = 16
private const val HALF_BAND_H = 40

// アトラス並びは napoleon.ui.GlyphAtlasGenerator (src/test/kotlin) と完全一致させること。
private const val HALF_CHARS = " /0123456789-AJKQTabcde"
private const val FULL_CHARS =
    "　〔〕〜！…" +
        "きしはめよりろ" +
        "パスナポジョーカマイティャックチェセムラブダヤハトペド" +
        "宣言副官軍連合枚配直完全勝利敗北請求正裏"

// 5_Glyph.png から各文字をブリットする盤面用テキスト描画器。
// 半角は 10x20、全角は 20x20。ベースラインに依存せずセル左上 (x, y) を指定する。
// 描画色は呼び出し側 Graphics2D の color を採用し、初出色ごとに着色アトラスをキャッシュする
// (アトラスは白 RGB + 可変アルファで作ってあるので、RGB を上書きすれば任意色に染まる)。
// アトラスは GlyphAtlasGenerator が FontMetrics ベースのベースラインで焼いてあり、各セル境界で
// クリップ済み。フォント差し替えで再生成するときはジェネレータ側のセル寸法を合わせること。
class GlyphRenderer {
    private val baseAtlas: BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream("5_Glyph.png"))
    private val tintCache = mutableMapOf<Int, BufferedImage>()

    fun charWidth(c: Char): Int = if (c.code <= 0x7F) HALF_W else FULL_W

    fun textWidth(s: String): Int = s.sumOf { charWidth(it) }

    fun drawString(
        g: Graphics2D,
        x: Int,
        y: Int,
        s: String,
    ) {
        val atlas = tintedAtlas(g.color.rgb)
        var dx = x
        for (c in s) {
            blit(g, atlas, dx, y, c)
            dx += charWidth(c)
        }
    }

    private fun tintedAtlas(rgb: Int): BufferedImage =
        tintCache.getOrPut(rgb) {
            val w = baseAtlas.width
            val h = baseAtlas.height
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(w * h)
            baseAtlas.getRGB(0, 0, w, h, pixels, 0, w)
            val tint = rgb and 0x00FFFFFF
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] and 0xFF000000.toInt()) or tint
            }
            out.setRGB(0, 0, w, h, pixels, 0, w)
            out
        }

    private fun blit(
        g: Graphics2D,
        atlas: BufferedImage,
        dx: Int,
        dy: Int,
        c: Char,
    ) {
        if (c.code <= 0x7F) {
            val i = HALF_CHARS.indexOf(c)
            if (i < 0) error("Glyph not in half-width atlas: '$c' (U+${"%04X".format(c.code)})")
            val sx = (i % COLS) * HALF_W
            val sy = (i / COLS) * GLYPH_H
            g.drawImage(atlas, dx, dy, dx + HALF_W, dy + GLYPH_H, sx, sy, sx + HALF_W, sy + GLYPH_H, null)
        } else {
            val i = FULL_CHARS.indexOf(c)
            if (i < 0) error("Glyph not in full-width atlas: '$c' (U+${"%04X".format(c.code)})")
            val sx = (i % COLS) * FULL_W
            val sy = HALF_BAND_H + (i / COLS) * GLYPH_H
            g.drawImage(atlas, dx, dy, dx + FULL_W, dy + GLYPH_H, sx, sy, sx + FULL_W, sy + GLYPH_H, null)
        }
    }
}
