package napoleon.engine

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.engine.view.SelfPlayerView

class Player(
    override val id: Int,
) : SelfPlayerView {
    // ナポレオンがキティ交換中に一時的に持つ 13 枚 (10 + KITTY_SIZE) を収容できるサイズ。
    override val hand = Array(HAND_SIZE + KITTY_SIZE) { Card.JOKER }
    override var handCount = 0
    override var honorsTaken = 0

    override var playedCard: Card = Card.JOKER

    var bid: Bid? = null
    var points = 0

    fun sortHand() {
        hand.sort(0, handCount)
    }
}
