package napoleon.ui

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
) : Slot

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
    val handLift: Int

    init {
        val bx = (CANVAS_W - CARD_W) / 2
        val by = 12
        val bz = 42
        balloonW = 4 + FONT_SIZE * 6
        balloonH = 4 + FONT_SIZE * 2
        val xx = bz + (CARD_H - CARD_W) / 2
        val gp = 10
        handLift = gp
        val honorStepW = 26
        val honorStepH = 45

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
            val infoX = handX + if (handStep < 0) CARD_SM_W - balloonW else 0
            val infoY = handY - balloonH - handLift
            return PlayerSlot(handX, handY, handStep, small, backMode, balloonX, balloonY, areaX, areaY, infoX, infoY)
        }

        playerSlots =
            arrayOf(
                playerSlot(
                    (CANVAS_W - (CARD_W + 9 * 30)) / 2,
                    CANVAS_H - CARD_H - 8,
                    30,
                    false,
                    0,
                    (CANVAS_W - balloonW) / 2,
                    by + bz * 2 + CARD_H + 8,
                    bx,
                    by + bz * 2,
                ),
                playerSlot(
                    8,
                    CARD_SM_H + balloonH * 2 + gp * 3 + 8,
                    19,
                    true,
                    1,
                    bx - xx - balloonW - 8,
                    CARD_SM_H + balloonH + gp * 2 + 8,
                    bx - xx,
                    by + bz,
                ),
                playerSlot(8, balloonH * 1 + gp * 1 + 8, 19, true, 1, bx - 37 - balloonW - 8, 8, bx - 37, by),
                playerSlot(
                    CANVAS_W - CARD_SM_W - 8,
                    balloonH * 1 + gp * 1 + 8,
                    -19,
                    true,
                    1,
                    bx + 37 + CARD_W + 8,
                    8,
                    bx + 37,
                    by,
                ),
                playerSlot(
                    CANVAS_W - CARD_SM_W - 8,
                    CARD_SM_H + balloonH * 2 + gp * 3 + 8,
                    -19,
                    true,
                    1,
                    bx + xx + CARD_W + 8,
                    CARD_SM_H + balloonH + gp * 2 + 8,
                    bx + xx,
                    by + bz,
                ),
            )

        kittyCenterSlot = KittySlot((CANVAS_W - (CARD_W + 2 * 30)) / 2, 54, 30, false, 1)
        kittyPileSlot = KittySlot(8, CANVAS_H - CARD_SM_H - 8, honorStepW, true, 2)

        infoRect =
            Rect(
                8,
                CARD_SM_H * 2 + balloonH * 2 + gp * 4 + 8,
                4 + FONT_SIZE * 7 + FONT_SIZE / 2,
                4 + FONT_SIZE * 6,
            )
        honorRect = Rect(CANVAS_W - 8 - CARD_SM_W - honorStepW * 4, CANVAS_H - 8 - CARD_SM_H - honorStepH * 3, honorStepW, honorStepH)
    }
}
