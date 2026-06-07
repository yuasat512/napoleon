package napoleon.replay

import napoleon.ai.PlayerStrategy
import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.Suit
import napoleon.engine.view.AiContext
import napoleon.record.GameRecord

class ReplayStrategy(
    private val myId: Int,
    private val context: AiContext,
    private val record: GameRecord,
    private val myBids: List<Bid?>,
) : PlayerStrategy {
    private var bidIdx = 0
    private var trickIdx = 0

    override fun chooseBid(): Bid? {
        // 旧フォーマットのログは確定した宣言だけを保存していた。その場合は
        // 「ナポレオンが記録された宣言を 1 度だけ出し、他全員はパス」とみなして競りを再現する。
        if (myBids.isEmpty()) {
            return if (myId == record.napoleonId && bidIdx++ == 0) record.bid else null
        }
        return myBids[bidIdx++]
    }

    override fun chooseAdjutant(): Card = record.adjutantCard

    override fun chooseKittySwap(): List<Int> {
        val hand = context.curPlayer.hand
        return record.discardedCards.map { target -> hand.indexOf(target) }
    }

    override fun choosePlay(): Pair<Int, Suit?> {
        val trick = record.tricks[trickIdx++]
        val card = trick.cards[myId]!!
        val hand = context.curPlayer.hand
        val handIdx = hand.indexOf(card)
        val jokerSuit = if (card.isJoker() && trick.leadId == myId) trick.jokerDeclaredSuit else null
        return handIdx to jokerSuit
    }
}
