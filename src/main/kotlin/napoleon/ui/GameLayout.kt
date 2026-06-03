package napoleon.ui

// 以下はレイアウトの視覚調整値 (見た目のさじ加減で決めた値)。
// これら以外の座標・寸法は CANVAS/CARD/FONT サイズとこれらから機械的に導出される。
private const val GAP = 10 // オブジェクト同士の間隔
private const val FIELD_SIZE = 216 // 場札 5 枚を収める正方形の一辺
private const val TOP_ROW_INSET = 24 // P2/P3 が重なったとき P2 カード右下のランクが少しだけ見える幅
private const val HONOR_STEP_W = 26 // オナーカード一覧の横ステップ (キティ山札の重ね幅も兼ねる)
private const val HONOR_STEP_H = 45 // オナーカード一覧の縦ステップ
private const val HAND_STEP_LARGE = 30 // 大カード手札 / キティ中央札の重ね幅
private const val HAND_STEP_SMALL = 19 // 小カード手札の重ね幅

sealed interface Slot {
    val handX: Int
    val handY: Int
    val handStep: Int
    val small: Boolean
    val backMode: Int
}

data class PlayerSlot(
    override val handX: Int,
    override val handY: Int,
    override val handStep: Int,
    override val small: Boolean,
    override val backMode: Int,
    val balloonX: Int,
    val balloonY: Int,
    val areaX: Int,
    val areaY: Int,
    val infoX: Int,
    val infoY: Int,
) : Slot {
    // handStep の符号が席の左右を決める。負 = 画面右側で情報欄も右寄せ (infoX 参照)。
    // 情報欄内の要素 (得点/絵札数/スートマーク) の配置もこれに合わせて左右反転する。
    val rightAnchored: Boolean get() = handStep < 0
}

data class KittySlot(
    override val handX: Int,
    override val handY: Int,
    override val handStep: Int,
    override val small: Boolean,
    override val backMode: Int,
) : Slot

class GameLayout {
    val playerSlots: Array<PlayerSlot>
    val kittyCenterSlot: KittySlot
    val kittyPileSlot: KittySlot
    val infoRect: Rect
    val honorRect: Rect
    val balloonW: Int
    val balloonH: Int
    val infoW: Int
    val handLift: Int

    init {
        balloonW = BOX_PADDING * 2 + FONT_SIZE * 5
        balloonH = BOX_PADDING * 2 + FONT_SIZE * 2
        infoW = BOX_PADDING * 2 + FONT_SIZE * 9 / 2
        handLift = GAP
        val infoRectY = CARD_SM_H * 2 + balloonH * 2 + GAP * 5

        // 場札 5 枚を一辺 FIELD_SIZE の正方形に収める。正方形は画面中央水平、P0 バルーンから縦 GAP 上に下辺。
        // 縦は 0 / 1,4 / 2,3 が等間隔。横は 0 が中央、1/4 が正方形左右の縁、2/3 が縁から TOP_ROW_INSET 内側。
        val fieldBottom = infoRectY - GAP
        val fieldTop = fieldBottom - FIELD_SIZE
        val fieldLeft = (CANVAS_W - FIELD_SIZE) / 2
        val fieldRight = fieldLeft + FIELD_SIZE
        val rowStep = (FIELD_SIZE - CARD_H) / 2
        val areaY23 = fieldTop
        val areaY14 = fieldTop + rowStep
        val areaY0 = fieldBottom - CARD_H
        val areaX0 = (CANVAS_W - CARD_W) / 2
        val areaX1 = fieldLeft
        val areaX2 = fieldLeft + TOP_ROW_INSET
        val areaX3 = fieldRight - CARD_W - TOP_ROW_INSET
        val areaX4 = fieldRight - CARD_W

        fun playerSlot(
            handX: Int,
            handY: Int,
            handStep: Int,
            small: Boolean,
            backMode: Int,
            balloonX: Int,
            balloonY: Int,
            areaX: Int,
            areaY: Int,
        ): PlayerSlot {
            val infoX = handX + if (handStep < 0) CARD_SM_W - infoW else 0
            val infoY = handY - balloonH - handLift
            return PlayerSlot(
                handX,
                handY,
                handStep,
                small,
                backMode,
                balloonX,
                balloonY,
                areaX,
                areaY,
                infoX,
                infoY,
            )
        }

        playerSlots =
            arrayOf(
                playerSlot(
                    (CANVAS_W - (CARD_W + 9 * HAND_STEP_LARGE)) / 2,
                    CANVAS_H - CARD_H - GAP,
                    HAND_STEP_LARGE,
                    false,
                    0,
                    (CANVAS_W - balloonW) / 2,
                    infoRectY,
                    areaX0,
                    areaY0,
                ),
                playerSlot(
                    GAP,
                    CARD_SM_H + balloonH * 2 + GAP * 4,
                    HAND_STEP_SMALL,
                    true,
                    1,
                    areaX1 - balloonW - GAP,
                    CARD_SM_H + balloonH + GAP * 3,
                    areaX1,
                    areaY14,
                ),
                playerSlot(GAP, balloonH + GAP * 2, HAND_STEP_SMALL, true, 1, areaX2 - balloonW - GAP, GAP, areaX2, areaY23),
                playerSlot(
                    CANVAS_W - CARD_SM_W - GAP,
                    balloonH + GAP * 2,
                    -HAND_STEP_SMALL,
                    true,
                    1,
                    areaX3 + CARD_W + GAP,
                    GAP,
                    areaX3,
                    areaY23,
                ),
                playerSlot(
                    CANVAS_W - CARD_SM_W - GAP,
                    CARD_SM_H + balloonH * 2 + GAP * 4,
                    -HAND_STEP_SMALL,
                    true,
                    1,
                    areaX4 + CARD_W + GAP,
                    CARD_SM_H + balloonH + GAP * 3,
                    areaX4,
                    areaY14,
                ),
            )

        kittyCenterSlot = KittySlot((CANVAS_W - (CARD_W + 2 * HAND_STEP_LARGE)) / 2, areaY14, HAND_STEP_LARGE, false, 1)
        kittyPileSlot = KittySlot(GAP, CANVAS_H - CARD_SM_H - GAP, HONOR_STEP_W, true, 2)

        infoRect =
            Rect(
                GAP,
                infoRectY,
                BOX_PADDING * 2 + FONT_SIZE * 7 + FONT_SIZE / 2,
                BOX_PADDING * 2 + FONT_SIZE * 6,
            )
        honorRect =
            Rect(
                CANVAS_W - GAP - CARD_SM_W - HONOR_STEP_W * 4,
                CANVAS_H - GAP - CARD_SM_H - HONOR_STEP_H * 3,
                HONOR_STEP_W,
                HONOR_STEP_H,
            )

        // CANVAS_W/H が十分大きく、隣り合う要素が重ならないことを検証する。
        // 視覚調整値やキャンバスサイズを変えて窮屈になったら起動時に気付けるようにする。
        val kittyTopGap = kittyPileSlot.handY - (infoRect.y + infoRect.h)
        check(kittyTopGap >= GAP) {
            "CANVAS_H が小さすぎる: 左下の情報欄と交換後キティの間隔が $kittyTopGap px (GAP=$GAP 以上必要)"
        }
        val p4HonorGap = honorRect.y - (playerSlots[4].handY + CARD_SM_H)
        check(p4HonorGap >= GAP) {
            "CANVAS_H が小さすぎる: P4 手札下端と獲得絵札の間隔が $p4HonorGap px (GAP=$GAP 以上必要)"
        }
        val p0InfoGap = playerSlots[0].handX - (infoRect.x + infoRect.w)
        check(p0InfoGap >= GAP) {
            "CANVAS_W が小さすぎる: P0 手札左端と左下情報欄の右端の間隔が $p0InfoGap px (GAP=$GAP 以上必要)"
        }
    }
}
