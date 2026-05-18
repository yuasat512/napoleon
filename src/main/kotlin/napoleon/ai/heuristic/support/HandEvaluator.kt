package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 手札の各カードに「絵札獲得への期待寄与」を重み付けし、合計でチームとしての推定獲得絵札数を返す。
// 係数は当面の暫定値。BidPlanner などの下流ヒューリスティックが安定したら、統計ランナーで
// 実測校正することを前提とした設計。
class HandEvaluator(
    private val context: AiContext,
) {
    fun estimateTeamHonors(trump: Suit): Double {
        val me = context.curPlayer
        var self = 0.0
        for (i in 0 until me.handCount) {
            self += weightOf(me.hand[i], trump)
        }
        return self * CALIBRATION_SCALE
    }

    private fun weightOf(
        c: Card,
        trump: Suit,
    ): Double =
        when {
            c.isJoker() -> 1.9
            c.isMighty() -> 2.7
            c.isRightBower(trump) -> 2.7
            c.isLeftBower(trump) -> 2.1
            c.suit == trump ->
                when (c.rank) {
                    // ♠切り札では切り札Aがマイティと同一カードになるためここでは加算しない (重複防止)。
                    Rank.RANK_A -> if (trump == Suit.SPADES) 0.0 else 2.8
                    Rank.RANK_K -> 2.1
                    Rank.RANK_Q -> 1.9
                    Rank.RANK_T, Rank.RANK_9 -> 1.7
                    Rank.RANK_8, Rank.RANK_7, Rank.RANK_6, Rank.RANK_5,
                    Rank.RANK_4, Rank.RANK_3, Rank.RANK_2,
                    -> 1.5
                    Rank.RANK_J -> error("trump-J should match isRightBower")
                }
            else ->
                when (c.rank) {
                    Rank.RANK_A -> 1.15
                    Rank.RANK_K -> 0.9
                    Rank.RANK_Q -> 0.7
                    Rank.RANK_J, Rank.RANK_T -> 0.6
                    Rank.RANK_9, Rank.RANK_8, Rank.RANK_7, Rank.RANK_6,
                    Rank.RANK_5, Rank.RANK_4, Rank.RANK_3, Rank.RANK_2,
                    -> 0.5
                }
        }

    companion object {
        private const val CALIBRATION_SCALE = 1.10
    }
}
