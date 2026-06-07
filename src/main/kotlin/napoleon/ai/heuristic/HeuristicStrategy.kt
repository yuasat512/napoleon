package napoleon.ai.heuristic

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.support.HandEvaluator
import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 公開情報のみで判断するヒューリスティック AI。AiContext 経由でしか盤面に触れないため、
// 他プレイヤーの手札やキティの非公開部分には型レベルで到達できず、RandomStrategy との
// 入れ替えも安全。各フェーズの判断はそれぞれの Planner に委譲し、本クラスは薄いファサードに留める。
class HeuristicStrategy(
    context: AiContext,
) : PlayerStrategy {
    private val handEvaluator = HandEvaluator(context)
    private val bidPlanner = BidPlanner(context, handEvaluator)
    private val adjutantPlanner = AdjutantPlanner(context)
    private val kittyPlanner = KittyPlanner(context)
    private val playPlanner = PlayPlanner(context)

    override fun chooseBid(): Bid? = bidPlanner.chooseBid()

    override fun chooseAdjutant(): Card = adjutantPlanner.chooseAdjutant()

    override fun chooseKittySwap(): List<Int> = kittyPlanner.chooseKittySwap()

    override fun choosePlay(): Pair<Int, Suit?> = playPlanner.choosePlay()
}
