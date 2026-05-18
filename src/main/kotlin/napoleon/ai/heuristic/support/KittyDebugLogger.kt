package napoleon.ai.heuristic.support

import napoleon.core.Suit
import napoleon.engine.view.AiContext

class KittyDebugLogger(
    private val context: AiContext,
) {
    fun log(
        discard: IntArray,
        firstTrickConfident: Boolean,
        voidedSuits: List<Suit>,
    ) {
        if (!context.debug) return
        val pid = context.curPlayer.id
        val me = context.curPlayer
        val discardStr = discard.joinToString(",") { "$it:${fmtCard(me.hand[it])}" }
        val voidedStr = if (voidedSuits.isEmpty()) "-" else voidedSuits.joinToString(",") { it.shortName }
        println(
            "[HeuristicAI P$pid] kitty discard=[$discardStr] firstTrickConfident=$firstTrickConfident voided=[$voidedStr]",
        )
    }
}
