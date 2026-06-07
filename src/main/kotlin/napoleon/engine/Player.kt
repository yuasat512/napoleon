package napoleon.engine

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.engine.view.SelfPlayerView

class Player(
    override val id: Int,
) : SelfPlayerView {
    // ナポレオンはキティ交換中だけ一時的に HAND_SIZE + KITTY_SIZE (13) 枚に膨らむ。
    override val hand = mutableListOf<Card>()
    override val handCount get() = hand.size
    override var honorsTaken = 0

    override var playedCard: Card = Card.JOKER

    var bid: Bid? = null
    var points = 0

    fun sortHand() {
        hand.sort()
    }
}
