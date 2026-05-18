package napoleon.ai

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.Suit

interface PlayerStrategy {
    fun chooseBid(): Bid?

    fun chooseAdjutant(): Card

    fun chooseKittySwap(): IntArray

    fun choosePlay(): Pair<Int, Suit?>
}
