package napoleon.ui

import napoleon.config.Config
import napoleon.core.Card
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.INVALID_INDEX
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit
import napoleon.engine.GameEngine
import napoleon.record.PLAYER_NAMES
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

private const val FS = FONT_SIZE
private val TXT_COL = Color.WHITE
private val NUM_COL = Color(0x80, 0xFF, 0xFF)
private val NEG_COL = Color(0xFF, 0xB0, 0xB0)

private enum class Balloon { NONE, BID, REDEAL, APPOINT, PLAY, DECLARE }

// ゲーム盤の描画ロジック。BufferedImage を 2 倍高さで確保し、上半分が表示画像・下半分が
// 初期背景 (フェルト) のスナップショット。restore() でこのスナップショットから矩形領域を
// 復元することで部分消去を実現している。
class GameView(
    private val engine: GameEngine,
    private val config: Config,
) {
    val image = BufferedImage(CANVAS_W, CANVAS_H * 2, BufferedImage.TYPE_INT_ARGB)
    private val renderer = CardRenderer(image, config)
    private val glyph = GlyphRenderer()

    private val layout = GameLayout()
    private val playerSlots = layout.playerSlots
    private val kittyCenterSlot = layout.kittyCenterSlot
    private val kittyPileSlot = layout.kittyPileSlot
    private val infoRect = layout.infoRect
    private val honorRect = layout.honorRect
    private val balloonW = layout.balloonW
    private val balloonH = layout.balloonH
    private val handLift = layout.handLift

    // キティの表向き枚数。配布で 3、ナポが拾うと 0、返却で 3、第 1 トリック後は絵札分減る。
    private var kittyVisibleCount = 0

    init {
        val back = generateFeltBackground(CANVAS_W, CANVAS_H)
        withGraphics { g ->
            g.drawImage(back, 0, 0, null)
            g.drawImage(back, 0, CANVAS_H, null)
        }
    }

    fun hitTestHand(
        x: Int,
        y: Int,
    ): Int {
        val p = playerSlots[engine.curPlayer.id]
        val width = (engine.curPlayer.handCount - 1) * p.handStep + CARD_W
        if (x < p.handX || x >= p.handX + width || y < p.handY || y >= p.handY + CARD_H) return INVALID_INDEX
        val idx = (x - p.handX) / p.handStep
        return if (idx >= engine.curPlayer.handCount) engine.curPlayer.handCount - 1 else idx
    }

    fun getCardCenter(idx: Int): Pair<Int, Int> {
        val slot = playerSlots[engine.curPlayer.id]
        val x = slot.handX + idx * slot.handStep + slot.handStep / 2
        val y = slot.handY + CARD_H / 2
        return x to y
    }

    fun onDeal() {
        kittyVisibleCount = KITTY_SIZE
        withGraphics { g ->
            restore(g, Rect(0, 0, CANVAS_W, CANVAS_H))

            for (pid in 0 until PLAYER_COUNT) {
                val slot = playerSlots[pid]
                val pl = engine.players[pid]
                for (j in 0 until pl.handCount) {
                    renderer.drawCard(
                        g,
                        slot.handX + j * slot.handStep,
                        slot.handY,
                        pl.hand[j],
                        slot.small,
                        slot.backMode,
                        engine.gameNumber,
                    )
                }
                val rc = Rect(slot.infoX, slot.infoY, balloonW, balloonH)
                drawInfoRect(g, rc)
                drawText(g, rc.x, rc.y, -rc.w, PLAYER_NAMES[pid])
                g.color = if (pl.points < 0) NEG_COL else NUM_COL
                drawText(g, rc.x, rc.y + FS, 0, "${pl.points}")
                g.color = TXT_COL
                drawText(g, rc.x, rc.y + FS, rc.w, "${pl.honorsTaken}枚")
            }

            drawKitty(g, kittyCenterSlot)

            val rc = infoRect
            drawInfoRect(g, rc)
            val gameStr =
                if (config.gameCount > 0) "${engine.gameNumber}/${config.gameCount}" else "${engine.gameNumber}"
            drawText(g, rc.x, rc.y, -rc.w, "〔$gameStr〕")
            drawText(g, rc.x, rc.y + FS, 0, "宣言\n副官\n\nナポ軍\n連合軍")
            drawText(g, rc.x, rc.y + FS * 4, rc.w, "0枚")
            drawText(g, rc.x, rc.y + FS * 5, rc.w, "0枚")
        }
    }

    fun onBid() {
        withGraphics { g ->
            val pidPrev = engine.playerIdAt(-2)
            if (engine.players[pidPrev].bid == null) updateBalloon(g, pidPrev, Balloon.NONE)
            updateBalloon(g, engine.playerIdAt(-1), Balloon.BID)
        }
    }

    fun onRedeal() {
        withGraphics { g ->
            updateBalloon(g, engine.playerIdAt(-1), Balloon.NONE)
            updateBalloon(g, engine.curPlayer.id, Balloon.REDEAL)
        }
    }

    fun onAppointPre() {
        withGraphics { g ->
            updateBalloon(g, engine.playerIdAt(-1), Balloon.NONE)

            val text = "　%2d枚".format(engine.bidTarget)
            val tx = infoRect.x + infoRect.w - glyph.textWidth(text) - 2
            val ty = infoRect.y + 2 + FS
            g.color = TXT_COL
            renderer.drawSuit(tx, ty, engine.trump)
            glyph.drawString(g, tx, ty, text)

            val slot = playerSlots[engine.curPlayer.id]
            renderer.drawSuit(slot.infoX + (balloonW - FS) / 2, slot.infoY + 2 + FS, engine.trump)
        }
    }

    fun onAppoint() {
        withGraphics { g ->
            updateBalloon(g, engine.curPlayer.id, Balloon.APPOINT)

            val card = engine.adjutantCard!!
            val str = adjutantName(card, engine.trump)
            val tx = infoRect.x + infoRect.w - glyph.textWidth(str) - 2
            val ty = infoRect.y + 2 + FS * 2
            g.color = TXT_COL
            if (!card.isPowerCard(engine.trump)) {
                renderer.drawSuit(tx, ty, card.suit)
            }
            glyph.drawString(g, tx, ty, str)
        }
    }

    fun onDraw() {
        kittyVisibleCount = 0
        withGraphics { g ->
            eraseKittyAt(g, kittyCenterSlot)
            updatePlayerHand(g, engine.curPlayer.id)
        }
    }

    fun onSwap() {
        kittyVisibleCount = KITTY_SIZE
        withGraphics { g ->
            updatePlayerHand(g, engine.curPlayer.id, dec = KITTY_SIZE)
            drawKittyAt(g, kittyPileSlot)
            updateBalloon(g, engine.curPlayer.id, Balloon.NONE)
            if (engine.adjutantRevealed) updateAdjutant(g)
        }
    }

    fun onPlay() {
        withGraphics { g ->
            val pid = engine.playerIdAt(-1)
            updatePlayerHand(g, pid, dec = 1)

            val slot = playerSlots[pid]
            val card = engine.players[pid].playedCard
            renderer.drawCard(g, slot.areaX, slot.areaY, card, false, 0, engine.gameNumber)

            updateBalloon(g, pid, Balloon.PLAY)

            // マイティが今出されたら、同一トリック内で先に出された SLIP の出し手のバルーンを更新する。
            // 同一トリック判定は handCount 一致で行う (まだ出していない人は handCount が 1 多い)。
            if (card.isMighty()) {
                val mighty = engine.players[pid]
                for (p in engine.players) {
                    if (p.id != pid && p.handCount == mighty.handCount && p.playedCard.isSlip()) {
                        updateBalloon(g, p.id, Balloon.PLAY)
                        break
                    }
                }
            }

            if (card == engine.adjutantCard) updateAdjutant(g)
        }
    }

    fun onTrick() {
        withGraphics { g ->
            val centerRc =
                Rect(
                    playerSlots[1].areaX,
                    playerSlots[2].areaY,
                    playerSlots[4].areaX + CARD_W - playerSlots[1].areaX,
                    playerSlots[0].areaY + CARD_H - playerSlots[2].areaY,
                )
            restore(g, centerRc)

            for (pid in 0 until PLAYER_COUNT) {
                updateBalloon(g, pid, Balloon.NONE)
            }

            val honorArea =
                Rect(
                    honorRect.x,
                    honorRect.y,
                    honorRect.w * 4 + CARD_SM_W,
                    honorRect.h * 3 + CARD_SM_H,
                )
            restore(g, honorArea)
            for (i in 0 until 4) {
                for (j in 0 until 5) {
                    val card = engine.honorCards[j + i * 5]
                    if (card != null) {
                        renderer.drawCard(g, honorArea.x + j * honorRect.w, honorArea.y + i * honorRect.h, card, true, 0, engine.gameNumber)
                    }
                }
            }

            val slot = playerSlots[engine.curPlayer.id]
            val honorMaxW = 2 * FS
            val rc3 = Rect(slot.infoX + balloonW - honorMaxW - 2, slot.infoY + 2 + FS, honorMaxW, FS)
            restore(g, rc3)
            drawInfoRect(g, rc3)
            val honorStr = "${engine.curPlayer.honorsTaken}枚"
            glyph.drawString(g, slot.infoX + balloonW - glyph.textWidth(honorStr) - 2, rc3.y, honorStr)

            drawHonorEstimate(g)

            if (engine.curPlayer.handCount == HAND_SIZE - 1) {
                kittyVisibleCount -= engine.kittyHonorCards.size
                drawKittyAt(g, kittyPileSlot)
            }
        }
    }

    fun onDeclare() {
        withGraphics { g ->
            updateBalloon(g, engine.adjutantId, Balloon.DECLARE)
        }
    }

    fun onResult() {
        withGraphics { g ->
            for (pid in 1 until PLAYER_COUNT) {
                drawHandAtSlot(g, playerSlots[pid], engine.players[pid].hand, engine.players[pid].handCount, 0, null, backMode = 0)
            }
        }
    }

    fun reset() {
        withGraphics { g -> restore(g, Rect(0, 0, CANVAS_W, CANVAS_H)) }
    }

    fun updateHand(
        pid: Int,
        dec: Int = 0,
        selected: IntArray? = null,
    ) {
        withGraphics { g -> updatePlayerHand(g, pid, dec, selected) }
    }

    private fun withGraphics(block: (Graphics2D) -> Unit) {
        val g = image.createGraphics()
        g.color = TXT_COL
        block(g)
        g.dispose()
    }

    private fun restore(
        g: Graphics2D,
        rc: Rect,
    ) {
        g.drawImage(image.getSubimage(rc.x, CANVAS_H + rc.y, rc.w, rc.h), rc.x, rc.y, null)
    }

    private fun drawInfoRect(
        g: Graphics2D,
        rc: Rect,
    ) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 96f / 255f)
        g.color = Color(0x40, 0x40, 0xFF)
        g.fillRect(rc.x, rc.y, rc.w, rc.h)
        g.composite = AlphaComposite.SrcOver
        g.color = TXT_COL
    }

    private fun drawText(
        g: Graphics2D,
        ax: Int,
        ay: Int,
        aPos: Int,
        text: String,
    ) {
        var y = ay + 2
        for (line in text.split("\n")) {
            val x =
                when {
                    aPos < 0 -> ax - (aPos + glyph.textWidth(line)) / 2
                    aPos > 0 -> ax + aPos - glyph.textWidth(line) - 2
                    else -> ax + 2
                }
            glyph.drawString(g, x, y, line)
            y += FS
        }
    }

    private fun updatePlayerHand(
        g: Graphics2D,
        pid: Int,
        dec: Int = 0,
        selected: IntArray? = null,
    ) {
        val slot = playerSlots[pid]
        val pl = engine.players[pid]
        drawHandAtSlot(g, slot, pl.hand, pl.handCount, dec, selected, slot.backMode)
    }

    private fun drawHandAtSlot(
        g: Graphics2D,
        slot: Slot,
        cards: Array<Card>,
        count: Int,
        dec: Int,
        selected: IntArray?,
        backMode: Int,
    ) {
        if (count + dec <= 0) return
        val cw = if (slot.small) CARD_SM_W else CARD_W
        val ch = if (slot.small) CARD_SM_H else CARD_H
        val rc =
            run {
                val d = (count + dec - 1) * slot.handStep
                if (d > 0) {
                    Rect(slot.handX, slot.handY - handLift, cw + d, ch + handLift)
                } else {
                    Rect(slot.handX + d, slot.handY - handLift, cw - d, ch + handLift)
                }
            }

        val liftByIndex = IntArray(HAND_SIZE + KITTY_SIZE + 1)
        selected?.forEachIndexed { i, idx ->
            if (i < KITTY_SIZE && idx >= 0) liftByIndex[idx + 1] = handLift
        }

        restore(g, rc)
        for (j in 0 until count) {
            renderer.drawCard(
                g,
                slot.handX + j * slot.handStep,
                slot.handY - liftByIndex[j + 1],
                cards[j],
                slot.small,
                backMode,
                engine.gameNumber,
            )
        }
    }

    private fun drawKittyAt(
        g: Graphics2D,
        slot: KittySlot,
    ) {
        drawHandAtSlot(g, slot, engine.kitty.cards, kittyVisibleCount, KITTY_SIZE - kittyVisibleCount, null, slot.backMode)
    }

    private fun drawKitty(
        g: Graphics2D,
        slot: KittySlot,
    ) {
        for (j in 0 until kittyVisibleCount) {
            renderer.drawCard(
                g,
                slot.handX + j * slot.handStep,
                slot.handY,
                engine.kitty.cards[j],
                slot.small,
                slot.backMode,
                engine.gameNumber,
            )
        }
    }

    private fun eraseKittyAt(
        g: Graphics2D,
        slot: KittySlot,
    ) {
        val cw = if (slot.small) CARD_SM_W else CARD_W
        val ch = if (slot.small) CARD_SM_H else CARD_H
        val w = (KITTY_SIZE - 1) * slot.handStep + cw
        restore(g, Rect(slot.handX, slot.handY - handLift, w, ch + handLift))
    }

    private fun updateAdjutant(g: Graphics2D) {
        val slot = playerSlots[engine.adjutantId]
        val rc = Rect(slot.infoX + (balloonW - FS) / 2, slot.infoY + 2 + FS, FS, FS)
        restore(g, rc)
        drawInfoRect(g, rc)
        renderer.drawSuit(rc.x, rc.y, Suit.NONE)
        drawHonorEstimate(g)
    }

    private fun updateBalloon(
        g: Graphics2D,
        pid: Int,
        message: Balloon,
    ) {
        val slot = playerSlots[pid]
        val rc = Rect(slot.balloonX, slot.balloonY, balloonW, balloonH)
        restore(g, rc)
        if (message != Balloon.NONE) drawInfoRect(g, rc)

        g.color = TXT_COL
        val str: String? = balloonText(pid, message, rc, g)
        if (str != null) {
            g.color = TXT_COL
            for ((i, line) in str.split("\n").withIndex()) {
                glyph.drawString(g, rc.x + 2, rc.y + 2 + FS * i, line)
            }
        }
    }

    private fun balloonText(
        pid: Int,
        message: Balloon,
        rc: Rect,
        g: Graphics2D,
    ): String? =
        when (message) {
            Balloon.BID -> {
                val bid = engine.players[pid].bid
                if (bid == null) {
                    "パス"
                } else {
                    renderer.drawSuit(rc.x + 2, rc.y + 2, bid.suit)
                    "　${bid.target}枚"
                }
            }
            Balloon.REDEAL -> "配り直し"
            Balloon.APPOINT -> {
                val card = engine.adjutantCard!!
                if (!card.isPowerCard(engine.trump)) {
                    renderer.drawSuit(rc.x + 2, rc.y + 2 + FS, card.suit)
                }
                "副官は\n${adjutantName(card, engine.trump)}"
            }
            Balloon.DECLARE -> {
                val estimate = engine.estimateHonors(pid)
                when {
                    estimate.napoleonHonors == HONOR_COUNT -> "完全勝利！"
                    estimate.napoleonHonors >= engine.bidTarget -> "勝利！"
                    else -> "敗北…"
                }
            }
            Balloon.PLAY -> playBalloonText(pid, rc, g)
            Balloon.NONE -> null
        }

    private fun playBalloonText(
        pid: Int,
        rc: Rect,
        g: Graphics2D,
    ): String? {
        val handCount = engine.players[pid].handCount
        val card = engine.players[pid].playedCard
        val winning = engine.isTrickLeader(pid)
        return when {
            card.isJoker() -> {
                val declaredSuit = engine.jokerDeclaredSuit
                if (handCount > 0 && declaredSuit != null) {
                    renderer.drawSuit(rc.x + 2, rc.y + 2, declaredSuit)
                    "　請求"
                } else {
                    "ジョーカー"
                }
            }
            card.isMighty() -> "マイティ"
            card.isRightBower(engine.trump) -> "正ジャック"
            card.isLeftBower(engine.trump) -> "裏ジャック"
            card.isJokerCall() && !engine.jokerPlayed && engine.trickTurn == 1 && handCount > 0 -> "ジョーカー\n請求"
            card.isSlip() && engine.mightyInTrick -> "よろめき"
            card.suit == engine.trump && card.suit != engine.leadSuit && winning -> "チェック"
            card.isRankTwo() && winning && handCount != HAND_SIZE - 1 -> "セイム"
            else -> {
                restore(g, rc)
                null
            }
        }
    }

    private fun adjutantName(
        card: Card,
        trump: Suit,
    ): String =
        when {
            card.isJoker() -> "ジョーカー"
            card.isMighty() -> "マイティ"
            card.isRightBower(trump) -> "正ジャック"
            card.isLeftBower(trump) -> "裏ジャック"
            else -> "　${card.rank.shortName}"
        }

    private fun drawHonorEstimate(g: Graphics2D) {
        val numW = infoRect.w - (2 + FS * 7 / 2)
        val rc = Rect(infoRect.x + 2 + FS * 7 / 2, infoRect.y + 2 + FS * 4, numW, FS * 2)
        restore(g, rc)
        drawInfoRect(g, rc)

        val estimate = engine.estimateHonors(0)
        val nap = estimate.napoleonHonors
        val ally = estimate.allyHonors
        val diff = estimate.unknownHonors
        if (diff > 0) {
            drawText(g, infoRect.x, infoRect.y + FS * 4, infoRect.w, "%2d〜%2d枚".format(nap, nap + diff))
            drawText(g, infoRect.x, infoRect.y + FS * 5, infoRect.w, "%2d〜%2d枚".format(ally, ally + diff))
        } else {
            drawText(g, infoRect.x, infoRect.y + FS * 4, infoRect.w, "%d枚".format(nap))
            drawText(g, infoRect.x, infoRect.y + FS * 5, infoRect.w, "%d枚".format(ally))
        }
    }
}
