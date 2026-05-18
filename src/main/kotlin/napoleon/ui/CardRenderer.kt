package napoleon.ui

import napoleon.config.Config
import napoleon.core.Card
import napoleon.core.Suit
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

const val CARD_W = 88
const val CARD_H = 120
const val CARD_SM_W = CARD_W / 2
const val CARD_SM_H = CARD_H / 2
private const val GLYPH_SIZE = FONT_SIZE

// カード画像の角丸まわりの透過に使う緑 (chroma key)。背景にこの色が来ない前提。
private const val KEY_COLOR = 0xFF00FF00.toInt()

class CardRenderer(
    private val image: BufferedImage,
    private val config: Config,
) {
    private val cardM: BufferedImage
    private val cardS: BufferedImage
    private val suitImg: BufferedImage

    init {
        val cl = javaClass.classLoader
        cardM = ImageIO.read(cl.getResourceAsStream("1_DeckM.png"))
        cardS = ImageIO.read(cl.getResourceAsStream("2_DeckS.png"))
        suitImg = ImageIO.read(cl.getResourceAsStream("3_Suit.png"))
    }

    fun drawCard(
        g: Graphics2D,
        ax: Int,
        ay: Int,
        card: Card,
        small: Boolean,
        backMode: Int,
        gameNumber: Int,
    ) {
        var rank = card.rank.ordinal
        var suit = card.suit.ordinal

        // backMode != 0 のスロット (他プレイヤーの手札・キティ) は基本裏向き。
        // backMode==1 は常時裏、backMode==2 (キティ山) は数札 (rank<8) のみ裏向き。
        // gameNumber and 1 で局ごとに 2 種類の裏柄を交互に切り替える。
        if (!config.showAllCards && backMode != 0) {
            if (backMode == 1 || rank < 8) {
                rank = 13
                suit = 2 + (gameNumber and 1)
            }
        } else if (card.isJoker()) {
            // ジョーカーは画像シート上に専用枠を持たず、(rank=13, suit∈{0,1}) のスロットを流用する。
            rank = 13
            suit = gameNumber and 1
        }

        val src = if (small) cardS else cardM
        val cw = if (small) CARD_SM_W else CARD_W
        val ch = if (small) CARD_SM_H else CARD_H
        val sx = rank * cw
        val sy = suit * ch
        val cornerH = if (small) 2 else 3

        g.drawImage(
            src.getSubimage(sx, sy + cornerH, cw, ch - cornerH * 2),
            ax,
            ay + cornerH,
            null,
        )
        copyWithKey(src, ax, ay, sx, sy, cw, cornerH)
        copyWithKey(src, ax, ay + ch - cornerH, sx, sy + ch - cornerH, cw, cornerH)
    }

    // 3_Suit.png から FONT_SIZE x FONT_SIZE の 1 セルを切り出して描画する。Suit.NONE.ordinal=4 は副官マーク用に
    // 予約された 5 番目のセルを指す。
    fun drawSuit(
        ax: Int,
        ay: Int,
        suit: Suit,
    ) {
        val g = image.createGraphics()
        g.drawImage(suitImg.getSubimage(suit.ordinal * GLYPH_SIZE, 0, GLYPH_SIZE, GLYPH_SIZE), ax, ay, null)
        g.dispose()
    }

    private fun copyWithKey(
        src: BufferedImage,
        dx: Int,
        dy: Int,
        sx: Int,
        sy: Int,
        w: Int,
        h: Int,
    ) {
        val n = w * h
        val srcPixels = IntArray(n)
        val dstPixels = IntArray(n)
        src.getRGB(sx, sy, w, h, srcPixels, 0, w)
        image.getRGB(dx, dy, w, h, dstPixels, 0, w)
        for (i in 0 until n) {
            if (srcPixels[i] != KEY_COLOR) dstPixels[i] = srcPixels[i]
        }
        image.setRGB(dx, dy, w, h, dstPixels, 0, w)
    }
}
