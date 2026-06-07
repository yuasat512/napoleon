package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 手札の各カードに重み付けし、合計でチームとしての推定獲得絵札数を返す。
class HandEvaluator(
    private val context: AiContext,
) {
    fun estimateTeamHonors(trump: Suit): Double {
        val me = context.curPlayer
        val self = me.hand.sumOf { weightOf(it, trump) }
        return self * CALIBRATION_SCALE
    }

    private fun weightOf(
        c: Card,
        trump: Suit,
    ): Double =
        when {
            c.isJoker() -> 9.78
            c.isMighty() -> 9.04
            c.isRightBower(trump) -> 8.51
            c.isLeftBower(trump) -> 8.53
            c.suit == trump ->
                when (c.rank) {
                    // ♠切り札では切り札Aがマイティと同一カードになるためここでは加算しない (重複防止)。
                    // 5.12 は ♠以外を切り札にした局だけで計上される % (3.77) を、その局割合 (1-0.264) で割り戻した 1枚当たり値。
                    Rank.RANK_A -> if (trump == Suit.SPADES) 0.0 else 5.12
                    Rank.RANK_K -> 3.89
                    Rank.RANK_Q -> 3.37
                    Rank.RANK_J -> error("trump-J should match isRightBower")
                    Rank.RANK_T -> 3.02
                    Rank.RANK_9 -> 3.08
                    Rank.RANK_8 -> 2.72
                    Rank.RANK_7 -> 2.53
                    Rank.RANK_6 -> 2.53
                    Rank.RANK_5 -> 2.41
                    Rank.RANK_4 -> 2.44
                    Rank.RANK_3 -> 2.46
                    Rank.RANK_2 -> 1.62
                }
            else ->
                // 非切り札の % は複数枚をまとめて計上した値なので 1枚当たりに割り戻す。
                // 通常ランクは 3スート分 (÷3)、J は正J/裏J が役札に抜けた残り 2枚 (÷2)、
                // A は ♠A が常にマイティに抜けるため平均 2+0.264 枚 (5.79% → 2.56) で割る。
                when (c.rank) {
                    Rank.RANK_A -> 2.56
                    Rank.RANK_K -> 1.48
                    Rank.RANK_Q -> 1.24
                    Rank.RANK_J -> 0.70
                    Rank.RANK_T -> 0.44
                    Rank.RANK_9 -> 0.47
                    Rank.RANK_8 -> 0.27
                    Rank.RANK_7 -> 0.15
                    Rank.RANK_6 -> 0.09
                    Rank.RANK_5 -> 0.04
                    Rank.RANK_4 -> 0.01
                    Rank.RANK_3 -> 0.01
                    Rank.RANK_2 -> 1.50 // 算出値は3.50だが過大のため一旦手修正。
                }
        }

    companion object {
        private const val CALIBRATION_SCALE = 0.55
    }
}
