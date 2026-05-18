package napoleon.record

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.GameRules.INVALID_INDEX
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit

class GameRecord {
    class TrickRecord {
        val cards = arrayOfNulls<Card>(PLAYER_COUNT)
        var leadId = INVALID_INDEX
        var winnerId = INVALID_INDEX
        var winnerHonors = 0
        var honorsGained = 0
        var mightyInTrick = false
        var jokerDeclaredSuit: Suit? = null
    }

    var bidFirstPlayerId = 0
    val bidSequence = mutableListOf<Bid?>()

    lateinit var bid: Bid
    lateinit var adjutantCard: Card
    var napoleonId = 0
    var adjutantId = 0
    var drawnCards = emptyArray<Card>()
    var discardedCards = emptyArray<Card>()
    val tricks = mutableListOf<TrickRecord>()
    var remainingCount = 0
    var remainingHands = Array(PLAYER_COUNT) { emptyArray<Card>() }
    var pointsBefore = IntArray(PLAYER_COUNT)
    var pointsAfter = IntArray(PLAYER_COUNT)
}
