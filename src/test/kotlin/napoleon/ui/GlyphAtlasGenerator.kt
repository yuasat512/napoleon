package napoleon.ui

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// 5_Glyph.png を再生成するスクリプト。盤面描画 (GameView) で使うグリフ集合のみ収録する。
//   半角 (10x20): 上 40px に 16 文字/行 × 2 行
//   全角 (20x20): y=40 以降に 16 文字/行 × 4 行
// HALF_CHARS / FULL_CHARS の並びと寸法は napoleon.ui.GlyphRenderer と完全一致させること。
//
// フォントは「ＭＳ ゴシック」(MS Gothic) を使う。Latin が monospace の書体でないと半角文字が
// HALF_W に収まらず欠ける (IPAex ゴシックや Noto Sans CJK JP は不可)。

private const val HALF_CHARS = " /0123456789-AJKQTabcde"
private const val FULL_CHARS =
    "　〔〕〜！…" +
        "きしはめよりろ" +
        "パスナポジョーカマイティャックチェセムラブダヤハトペド" +
        "宣言副官軍連合枚配直完全勝利敗北請求正裏"

private const val HALF_W = 10
private const val FULL_W = 20
private const val GLYPH_H = 20
private const val COLS = 16
private const val HALF_BAND_H = 40
private const val ATLAS_W = 320
private const val ATLAS_H = 120

object GlyphAtlasGenerator {
    fun generate() {
        if (HALF_CHARS.length > COLS * (HALF_BAND_H / GLYPH_H)) {
            error("HALF_CHARS overflows half band")
        }
        if (FULL_CHARS.length > COLS * ((ATLAS_H - HALF_BAND_H) / GLYPH_H)) {
            error("FULL_CHARS overflows full band")
        }
        val atlas = BufferedImage(ATLAS_W, ATLAS_H, BufferedImage.TYPE_INT_ARGB)
        val g = atlas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.font = Font("ＭＳ ゴシック", Font.BOLD, GLYPH_H)
            g.color = Color.WHITE

            // ベースラインは FontMetrics から取得。GLYPH_H とフォントメトリクスが釣り合わない場合
            // (ascent+descent > GLYPH_H) はディセンダがセル外に溢れて隣接セルへゴミとなって写るので、
            // setClip でセル境界を固定しておく。
            val fm = g.fontMetrics
            val baseline = fm.ascent
            println("Font metrics: ascent=${fm.ascent} descent=${fm.descent} GLYPH_H=$GLYPH_H")

            for (i in HALF_CHARS.indices) {
                val cx = (i % COLS) * HALF_W
                val cy = (i / COLS) * GLYPH_H
                g.setClip(cx, cy, HALF_W, GLYPH_H)
                g.drawString(HALF_CHARS[i].toString(), cx, cy + baseline)
            }
            for (i in FULL_CHARS.indices) {
                val cx = (i % COLS) * FULL_W
                val cy = HALF_BAND_H + (i / COLS) * GLYPH_H
                g.setClip(cx, cy, FULL_W, GLYPH_H)
                g.drawString(FULL_CHARS[i].toString(), cx, cy + baseline)
            }
            g.clip = null
        } finally {
            g.dispose()
        }
        val out = File("src/main/resources/5_Glyph.png")
        ImageIO.write(atlas, "PNG", out)
        println("wrote ${out.absolutePath}  half=${HALF_CHARS.length} full=${FULL_CHARS.length}")
    }
}
