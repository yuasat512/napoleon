package napoleon.ai.random

import napoleon.ai.PlayerStrategy
import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext
import kotlin.random.Random

class RandomStrategy(
    private val context: AiContext,
) : PlayerStrategy {
    private val rng = Random(12345)

    override fun chooseBid(): Bid? {
        var bestScore = 0
        var bestSuit = Suit.CLUBS
        for (suit in Suit.realEntries) {
            val score =
                (0 until HAND_SIZE).sumOf { i ->
                    val card = context.curPlayer.hand[i]
                    if (card.isPowerCard(suit)) {
                        20
                    } else if (card.suit == suit) {
                        10
                    } else {
                        0
                    }
                }
            if (bestScore < score) {
                bestScore = score
                bestSuit = suit
            }
        }
        var candidate = Bid(bestSuit, bestScore / 10 + 5)

        val current =
            context.bid ?: return if (candidate >= Bid(Suit.CLUBS, Bid.MIN_TARGET)) {
                Bid(bestSuit, Bid.MIN_TARGET)
            } else {
                null
            }
        if (candidate > current) {
            while (candidate.strength > current.strength + 4) candidate = Bid(bestSuit, candidate.target - 1)
            return candidate
        }
        return null
    }

    override fun chooseAdjutant(): Card {
        val trump = context.trump
        val preferences =
            listOf(
                Card.JOKER,
                Card.MIGHTY,
                Card.rightBower(trump),
                Card.leftBower(trump),
                Card.of(trump, Rank.RANK_A),
                Card.of(trump, Rank.RANK_K),
                Card.of(trump, Rank.RANK_Q),
                Card.of(trump, Rank.RANK_8),
                Card.of(trump, Rank.RANK_7),
                Card.of(trump, Rank.RANK_6),
                Card.of(trump, Rank.RANK_5),
                Card.of(trump, Rank.RANK_4),
            )
        val hand = context.curPlayer.hand
        return preferences.firstOrNull { card ->
            (0 until HAND_SIZE).none { hand[it] == card }
        } ?: error("All adjutant candidates are in own hand")
    }

    override fun chooseKittySwap(): IntArray {
        val array = Array(HAND_SIZE + KITTY_SIZE) { it }
        array.shuffle(rng)
        return array.take(KITTY_SIZE).toIntArray()
    }

    override fun choosePlay(): Pair<Int, Suit?> {
        val playable = (0 until context.curPlayer.handCount).filter { context.canFollow(it) }
        val idx = playable[rng.nextInt(playable.size)]
        val suit = if (context.requiresJokerSuitChoice(idx)) Suit.realEntries[rng.nextInt(Suit.realEntries.size)] else null
        return idx to suit
    }
}
