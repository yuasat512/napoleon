package napoleon.ui

import napoleon.config.Config
import napoleon.core.Card
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.INVALID_INDEX
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.PLAYER_NAMES
import napoleon.core.Suit
import napoleon.engine.GameEngine
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

private const val FS = FONT_SIZE
private val TXT_COL = Color.WHITE
private val NUM_COL = Color(0x80, 0xFF, 0xFF)
private val NEG_COL = Color(0xFF, 0xB0, 0xB0)
private val INFO_RECT_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 96f / 255f)
private val INFO_RECT_COL = Color(0x40, 0x40, 0xFF)

private enum class Balloon { NONE, BID, REDEAL, APPOINT, PLAY, DECLARE }

private class BalloonView(
    val text: String,
    val suit: Suit? = null,
    val suitYOffset: Int = 0,
)

// ゲーム盤の描画ロジック。BufferedImage を 2 倍高さで確保し、上半分が表示画像・下半分が
// 初期背景 (フェルト) のスナップショット。restore() でこのスナップショットから矩形領域を
// 復元することで部分消去を実現している。
class GameView(
    private val engine: GameEngine,
    private val config: Config,
) {
    val image = BufferedImage(CANVAS_W, CANVAS_H * 2, BufferedImage.TYPE_INT_ARGB)
    private val renderer = CardRenderer(image, config) { engine.gameNumber }
    private val glyph = GlyphRenderer()

    private val layout = GameLayout()
    private val playerSlots = layout.playerSlots
    private val kittyCenterSlot = layout.kittyCenterSlot
    private val kittyPileSlot = layout.kittyPileSlot
    private val infoRect = layout.infoRect
    private val honorRect = layout.honorRect
    private val balloonW = layout.balloonW
    private val balloonH = layout.balloonH
    private val infoW = layout.infoW
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
                    )
                }
                drawPlayerInfo(g, pid)
            }

            drawKittyAt(g, kittyCenterSlot)

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
            val tx = infoRect.x + infoRect.w - glyph.textWidth(text) - BOX_PADDING
            val ty = infoRect.y + BOX_PADDING + FS
            g.color = TXT_COL
            renderer.drawSuit(g, tx, ty, engine.trump)
            glyph.drawString(g, tx, ty, text)

            drawPlayerInfo(g, engine.napoleonId)
        }
    }

    fun onAppoint() {
        withGraphics { g ->
            updateBalloon(g, engine.curPlayer.id, Balloon.APPOINT)

            val card = engine.adjutantCard!!
            val str = adjutantName(card, engine.trump)
            val tx = infoRect.x + infoRect.w - glyph.textWidth(str) - BOX_PADDING
            val ty = infoRect.y + BOX_PADDING + FS * 2
            g.color = TXT_COL
            if (!card.isPowerCard(engine.trump)) {
                renderer.drawSuit(g, tx, ty, card.suit)
            }
            glyph.drawString(g, tx, ty, str)
        }
    }

    fun onDraw() {
        kittyVisibleCount = 0
        withGraphics { g ->
            drawKittyAt(g, kittyCenterSlot) // kittyVisibleCount=0 のため中央キティを消去
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
            renderer.drawCard(g, slot.areaX, slot.areaY, card, false, 0)

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
                        renderer.drawCard(g, honorArea.x + j * honorRect.w, honorArea.y + i * honorRect.h, card, true, 0)
                    }
                }
            }

            val slot = playerSlots[engine.curPlayer.id]
            val honorMaxW = 2 * FS
            val rc3 = Rect(slot.infoX + infoW - honorMaxW - BOX_PADDING, slot.infoY + BOX_PADDING + FS, honorMaxW, FS)
            restore(g, rc3)
            drawInfoRect(g, rc3)
            val honorStr = "${engine.curPlayer.honorsTaken}枚"
            glyph.drawString(g, slot.infoX + infoW - glyph.textWidth(honorStr) - BOX_PADDING, rc3.y, honorStr)

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
            // 副官未公開のまま終局したケース (副官札が他チーム手札に残ったまま勝敗確定など) でも
            // 結果画面では副官マークを公開する。
            for (pid in 0 until PLAYER_COUNT) {
                drawPlayerInfo(g, pid, showAdjutant = true)
            }
        }
    }

    private fun drawPlayerInfo(
        g: Graphics2D,
        pid: Int,
        showAdjutant: Boolean = engine.adjutantRevealed,
    ) {
        val slot = playerSlots[pid]
        val pl = engine.players[pid]
        val rc = Rect(slot.infoX, slot.infoY, infoW, balloonH)
        restore(g, rc)
        drawInfoRect(g, rc)
        val name = PLAYER_NAMES[pid]
        val nameW = glyph.textWidth(name)
        val nameX = rc.x + (rc.w - nameW) / 2
        val nameY = rc.y + BOX_PADDING
        glyph.drawString(g, nameX, nameY, name)
        if (engine.bid != null && pid == engine.napoleonId) {
            renderer.drawSuit(g, nameX + nameW + FS / 2, nameY, engine.trump)
        }
        if (showAdjutant && pid == engine.adjutantId) {
            renderer.drawSuit(g, nameX - FS - FS / 2, nameY, Suit.NONE)
        }
        g.color = if (pl.points < 0) NEG_COL else NUM_COL
        drawText(g, rc.x, rc.y + FS, 0, "${pl.points}")
        g.color = TXT_COL
        drawText(g, rc.x, rc.y + FS, rc.w, "${pl.honorsTaken}枚")
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
        g.composite = INFO_RECT_COMPOSITE
        g.color = INFO_RECT_COL
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
        var y = ay + BOX_PADDING
        for (line in text.split("\n")) {
            val x =
                when {
                    aPos < 0 -> ax - (aPos + glyph.textWidth(line)) / 2
                    aPos > 0 -> ax + aPos - glyph.textWidth(line) - BOX_PADDING
                    else -> ax + BOX_PADDING
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
            )
        }
    }

    private fun drawKittyAt(
        g: Graphics2D,
        slot: KittySlot,
    ) {
        drawHandAtSlot(g, slot, engine.kitty.cards, kittyVisibleCount, KITTY_SIZE - kittyVisibleCount, null, slot.backMode)
    }

    private fun updateAdjutant(g: Graphics2D) {
        drawPlayerInfo(g, engine.adjutantId)
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
        val view = balloonView(pid, message) ?: return
        drawInfoRect(g, rc)
        view.suit?.let { renderer.drawSuit(g, rc.x + BOX_PADDING, rc.y + BOX_PADDING + view.suitYOffset, it) }
        g.color = TXT_COL
        for ((i, line) in view.text.split("\n").withIndex()) {
            glyph.drawString(g, rc.x + BOX_PADDING, rc.y + BOX_PADDING + FS * i, line)
        }
    }

    private fun balloonView(
        pid: Int,
        message: Balloon,
    ): BalloonView? =
        when (message) {
            Balloon.NONE -> null
            Balloon.BID -> {
                val bid = engine.players[pid].bid
                if (bid == null) BalloonView("パス") else BalloonView("　${bid.target}枚", bid.suit)
            }
            Balloon.REDEAL -> BalloonView("配り直し")
            Balloon.APPOINT -> {
                val card = engine.adjutantCard!!
                val suit = if (!card.isPowerCard(engine.trump)) card.suit else null
                BalloonView("副官は\n${adjutantName(card, engine.trump)}", suit, FS)
            }
            Balloon.DECLARE -> {
                val estimate = engine.estimateHonors(pid)
                val text =
                    when {
                        estimate.napoleonHonors == HONOR_COUNT -> "完全勝利！"
                        estimate.napoleonHonors >= engine.bidTarget -> "勝利！"
                        else -> "敗北…"
                    }
                BalloonView(text)
            }
            Balloon.PLAY -> playBalloonView(pid)
        }

    private fun playBalloonView(pid: Int): BalloonView? {
        val handCount = engine.players[pid].handCount
        val card = engine.players[pid].playedCard
        val winning = engine.isTrickLeader(pid)
        return when {
            card.isJoker() -> {
                val declaredSuit = engine.jokerDeclaredSuit
                if (handCount > 0 && declaredSuit != null) BalloonView("　請求", declaredSuit) else BalloonView("ジョーカー")
            }
            card.isMighty() -> BalloonView("マイティ")
            card.isRightBower(engine.trump) -> BalloonView("正ジャック")
            card.isLeftBower(engine.trump) -> BalloonView("裏ジャック")
            card.isJokerCall() && !engine.jokerPlayed && engine.trickTurn == 1 && handCount > 0 -> BalloonView("ジョーカー\n請求")
            card.isSlip() && engine.mightyInTrick -> BalloonView("よろめき")
            card.suit == engine.trump && card.suit != engine.leadSuit && winning -> BalloonView("チェック")
            card.isRankTwo() && winning && handCount != HAND_SIZE - 1 -> BalloonView("セイム")
            else -> null
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
        val numW = infoRect.w - (BOX_PADDING + FS * 7 / 2)
        val rc = Rect(infoRect.x + BOX_PADDING + FS * 7 / 2, infoRect.y + BOX_PADDING + FS * 4, numW, FS * 2)
        restore(g, rc)
        drawInfoRect(g, rc)

        // showAllCards (リプレイ・--debug) では全カード可視なので副官視点で厳密値を返させる。
        val estimate = engine.estimateHonors(if (config.showAllCards) engine.adjutantId else 0)
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
