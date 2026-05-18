package napoleon.ai.heuristic.support

import napoleon.core.Bid
import napoleon.core.Suit
import napoleon.engine.view.AiContext

class BidDebugLogger(
    private val context: AiContext,
) {
    fun log(
        current: Bid?,
        estimates: Map<Suit, Double>,
        result: Bid?,
        bestMargin: Double?,
    ) {
        if (!context.debug) return
        val pid = context.curPlayer.id
        val cur = current?.let { fmtBid(it) } ?: "null"
        val estStr =
            Suit.realEntries.joinToString("/") { suit ->
                estimates[suit]?.let { "${suit.shortName}=${"%.1f".format(it)}" } ?: "${suit.shortName}=-"
            }
        val pick = result?.let { fmtBid(it) } ?: "PASS"
        val marginStr = bestMargin?.let { " margin=${"%.1f".format(it)}" } ?: ""
        println("[HeuristicAI P$pid] bid current=$cur E=[$estStr] pick=$pick$marginStr")
    }

    private fun fmtBid(bid: Bid) = "${bid.suit.shortName}${bid.target}"
}
