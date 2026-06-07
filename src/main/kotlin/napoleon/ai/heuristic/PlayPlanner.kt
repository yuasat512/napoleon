package napoleon.ai.heuristic

import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.play.AllyFollowPlanner
import napoleon.ai.heuristic.play.AllyLeadPlanner
import napoleon.ai.heuristic.play.NapoleonFollowPlanner
import napoleon.ai.heuristic.play.NapoleonLeadPlanner
import napoleon.ai.heuristic.play.PlayFollowPlanner
import napoleon.ai.heuristic.play.PlayLeadPlanner
import napoleon.ai.heuristic.support.Role
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
    private val napoleonLeadPlanner = NapoleonLeadPlanner(context, roleInference, evaluator, logger)
    private val allyLeadPlanner = AllyLeadPlanner(context, roleInference, evaluator, logger)
    private val napoleonFollowPlanner = NapoleonFollowPlanner(context, roleInference, evaluator, logger)
    private val allyFollowPlanner = AllyFollowPlanner(context, roleInference, evaluator, logger)

    fun choosePlay(): Pair<Int, Suit?> {
        val me = context.curPlayer
        val legal = me.hand.indices.filter { context.canFollow(it) }
        val idx =
            when {
                legal.size == 1 -> legal[0].also { logger.log(it, legal, PlayRoute.FORCED_SINGLE) }
                context.trickTurn == 0 -> leadPlanner().chooseLead(legal)
                else -> followPlanner().chooseFollow(legal)
            }
        val suit = if (context.requiresJokerSuitChoice(idx)) evaluator.chooseJokerLeadSuit() else null
        return idx to suit
    }

    // 役職は副官公開等で局中に確定していくため、手番ごとに現在の推定役職で planner を選ぶ。
    private fun leadPlanner(): PlayLeadPlanner = if (roleInference.myRole() == Role.ALLY) allyLeadPlanner else napoleonLeadPlanner

    private fun followPlanner(): PlayFollowPlanner = if (roleInference.myRole() == Role.ALLY) allyFollowPlanner else napoleonFollowPlanner
}
