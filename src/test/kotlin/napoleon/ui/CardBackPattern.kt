package napoleon.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import kotlin.math.sqrt

// カード裏面に使うひし形格子パターン (CARD_W × CARD_H) を生成する。
//   1. SIZE × SIZE のレイヤに、太さ LINE_WIDTH の斜め線 3 本 (主線 1 + 平行な副線 2) を AA で描く
//   2. 同じレイヤを 90° 回転させて逆向きの斜めレイヤを作り、白背景に両方重ねてひし形格子タイルにする
//   3. タイル境界の歪みを CROP px トリムし、TILE_COLS × TILE_ROWS に並べる
//   4. その中央を CARD_W × CARD_H で切り抜くことで、タイルの継ぎ目がカード端の外側に来るようにする
object CardBackPattern {
    fun generate(lineColor: Color): BufferedImage {
        val tile = renderTile(lineColor)
        val tiled = tileImage(tile)
        return centerCrop(tiled, TILED_CROP_WIDTH, TILED_CROP_HEIGHT)
    }

    private fun renderTile(lineColor: Color): BufferedImage {
        val backslashLayer = renderBackslashLayer(lineColor)
        val slashLayer = rotateClockwise90(backslashLayer)
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(0xFFFFFF)
        g.fillRect(0, 0, SIZE, SIZE)
        g.drawImage(backslashLayer, 0, 0, null)
        g.drawImage(slashLayer, 0, 0, null)
        g.dispose()
        return img.getSubimage(CROP, CROP, SIZE - 2 * CROP, SIZE - 2 * CROP)
    }

    private fun renderBackslashLayer(lineColor: Color): BufferedImage {
        val layer = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = layer.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.stroke = BasicStroke(LINE_WIDTH.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        val perp = (LINE_WIDTH + LINE_GAP) / sqrt(2.0)
        g.color = Color(lineColor.red, lineColor.green, lineColor.blue, LINE_ALPHA)
        g.draw(Line2D.Double(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble()))
        g.color = Color(lineColor.red, lineColor.green, lineColor.blue, SIDE_LINE_ALPHA)
        g.draw(Line2D.Double(perp, -perp, SIZE + perp, SIZE - perp))
        g.draw(Line2D.Double(-perp, perp, SIZE - perp, SIZE + perp))
        g.dispose()
        return layer
    }

    private fun rotateClockwise90(src: BufferedImage): BufferedImage {
        val dst = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                dst.setRGB(x, y, src.getRGB(y, SIZE - 1 - x))
            }
        }
        return dst
    }

    private fun tileImage(tile: BufferedImage): BufferedImage {
        val tw = tile.width
        val th = tile.height
        val tiled = BufferedImage(tw * TILE_COLS, th * TILE_ROWS, BufferedImage.TYPE_INT_RGB)
        val g = tiled.createGraphics()
        for (row in 0 until TILE_ROWS) {
            for (col in 0 until TILE_COLS) {
                g.drawImage(tile, col * tw, row * th, null)
            }
        }
        g.dispose()
        return tiled
    }

    private fun centerCrop(
        src: BufferedImage,
        w: Int,
        h: Int,
    ): BufferedImage {
        val ox = (src.width - w) / 2
        val oy = (src.height - h) / 2
        return src.getSubimage(ox, oy, w, h)
    }

    private const val SIZE = 34
    private const val CROP = 1
    private const val LINE_WIDTH = 5
    private const val LINE_GAP = 3
    private const val LINE_ALPHA = 204
    private const val SIDE_LINE_ALPHA = 153
    private const val TILE_COLS = 3
    private const val TILE_ROWS = 4
    private const val TILED_CROP_WIDTH = 88
    private const val TILED_CROP_HEIGHT = 120
}
