package napoleon.ai.heuristic

import napoleon.ai.heuristic.support.PlayDebugLogger
import napoleon.ai.heuristic.support.PlayFollowPlanner
import napoleon.ai.heuristic.support.PlayLeadPlanner
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Suit
import napoleon.engine.view.AiContext

class PlayPlanner(
    private val context: AiContext,
) {
    private val roleInference = RoleInference(context)
    private val evaluator = TrickEvaluator(context, roleInference)
    private val logger = PlayDebugLogger(context, roleInference, evaluator)
    private val leadPlanner = PlayLeadPlanner(context, roleInference, evaluator, logger)
    private val followPlanner = PlayFollowPlanner(context, roleInference, evaluator, logger)

    fun choosePlay(): Pair<Int, Suit?> {
        val me = context.curPlayer
        val legal = (0 until me.handCount).filter { context.canFollow(it) }
        val idx =
            when {
                legal.size == 1 -> legal[0].also { logger.log(it, legal, "合法手が1枚だけなので選択の余地なし") }
                context.trickTurn == 0 -> leadPlanner.chooseLead(legal)
                else -> followPlanner.chooseFollow(legal)
            }
        val suit = if (context.requiresJokerSuitChoice(idx)) evaluator.chooseJokerLeadSuit() else null
        return idx to suit
    }
}
