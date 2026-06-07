package napoleon.ai.heuristic

import napoleon.ai.heuristic.log.BidDebugLogger
import napoleon.ai.heuristic.support.HandEvaluator
import napoleon.core.Bid
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 各切り札候補について、推定獲得絵札数と現在の最高宣言を上回る最小宣言値を計算し、
// (推定値 − 宣言値) のマージンが最大かつ非負のものを選ぶ。マージン非負とは、宣言が達成可能と
// 期待できることを意味する。同点はスート順位で決定する (決定的な順序付け)。
// ただし現在の宣言スートが自分の推定獲得絵札数で第1/第2候補に入る場合は、絵札が競合していて
// 上回りにくいとみてパスする。
class BidPlanner(
    private val context: AiContext,
    private val handEvaluator: HandEvaluator,
) {
    private val logger = BidDebugLogger(context)

    fun chooseBid(): Bid? {
        val current = context.bid
        val estimates = Suit.realEntries.associateWith { handEvaluator.estimateTeamHonors(it) }

        if (current != null && isTopTwoCandidate(current.suit, estimates)) {
            logger.log(current, estimates, null, null)
            return null
        }

        val best =
            Suit.realEntries
                .mapNotNull { trump ->
                    val nMin = minTarget(trump, current) ?: return@mapNotNull null
                    Candidate(trump, nMin, estimates.getValue(trump) - nMin).takeIf { it.margin >= 0.0 }
                }.maxWithOrNull(compareBy({ it.margin }, { it.trump.ordinal }))
        val result = best?.let { Bid(it.trump, it.target) }
        logger.log(current, estimates, result, best?.margin)
        return result
    }

    // 推定獲得絵札数の降順 (同点は trump.ordinal 降順、宣言選択の同点処理と一致) で
    // 上位2スートに suit が含まれるか。
    private fun isTopTwoCandidate(
        suit: Suit,
        estimates: Map<Suit, Double>,
    ): Boolean =
        estimates.entries
            .sortedWith(compareByDescending<Map.Entry<Suit, Double>> { it.value }.thenByDescending { it.key.ordinal })
            .take(2)
            .any { it.key == suit }

    private data class Candidate(
        val trump: Suit,
        val target: Int,
        val margin: Double,
    )

    // current を厳密に上回る最小 target。スートが強ければ同 target でも勝てるが、
    // 同等以下のスートでは target を 1 増やす必要がある。最小昇格でも上限超過なら null。
    private fun minTarget(
        trump: Suit,
        current: Bid?,
    ): Int? {
        val n =
            when {
                current == null -> Bid.MIN_TARGET
                trump.ordinal > current.suit.ordinal -> current.target
                else -> current.target + 1
            }
        return if (n > Bid.MAX_TARGET) null else n
    }
}
