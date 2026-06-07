package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.core.GameRules
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 視点プレイヤーから見た未確定カード (他家の手札 + 裏向きキティ) の分布にもとづく確率推定。
// 盤面から (手札枚数, 当該スートの未確定枚数, 余り枚数) を求め、事前計算済みの FollowTable を引いて
// マストフォローの確度 (= 後続全員がそのスートを追従できる確率) を返す。
class FollowOdds(
    private val context: AiContext,
) {
    // 未確定札の総数と、そのうち指定スートの枚数。
    private fun counts(suit: Suit): Pair<Int, Int> {
        val seen = context.seenCards()
        val unseenTotal = Card.CARD_COUNT - seen.size
        val unseenSuit = RANKS_PER_SUIT - seen.count { it.suit == suit }
        return unseenTotal to unseenSuit
    }

    // まだ手番が来ていない (自分より後に出す) 相手全員が suit を追従できる確率。
    // = その相手の誰も切り札で割り込めない確率。高位札のリードやセイム成立の見込みに使う。
    // リード時専用: 誰もこのトリックに出していないので、後続は必ず自分以外の全員 (PLAYER_COUNT-1 人) で
    // 全員が同じ手札枚数。よって盤面値で FollowTable を引くだけで済む。
    fun pAllLaterFollow(suit: Suit): Double {
        if (suit == Suit.NONE) return 0.0
        val me = context.curPlayer
        val handCount = me.hand.size
        val later = context.publicPlayers.count { it.id != me.id && it.handCount >= handCount }
        if (later != GameRules.PLAYER_COUNT - 1) {
            error("pAllLaterFollow はリード時専用 (later=$later, handCount=$handCount)")
        }
        // 後続のいずれかが当該スート void と確定済みなら、その人は追従できないので全員追従は不可能。
        if (context.publicPlayers.any { it.id != me.id && suit in context.knownVoids[it.id] }) return 0.0
        val (unseenTotal, unseenSuit) = counts(suit)
        if (unseenTotal <= 0) return 0.0
        val slack = unseenTotal - later * handCount
        return FollowTable.pAllFollow(handCount, slack, unseenSuit)
    }

    private companion object {
        val RANKS_PER_SUIT = Rank.entries.size // 2..A の 13 ランク
    }
}
