package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.HeuristicVariant
import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.support.Decisive
import napoleon.ai.heuristic.support.FollowOdds
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 連合軍のリード戦術。決着確定 (ナポ軍敗北) → ジョーカー請求 → セイム狙いの非切り札2 →
// 確実勝ち最弱 → 低位探り → 最弱札のフォールバックに進む。切り札リード (能動的セイム・高位切り札
// 吸い出し) は主導権を渡すリスクが勝つため、敵 void へのラフ誘発リード (NapoleonLeadPlanner の同型) は
// 計測上ほぼ中立のため、いずれも連合では行わない (履歴は HeuristicVariant 参照)。
class AllyLeadPlanner(
    context: AiContext,
    roleInference: RoleInference,
    evaluator: TrickEvaluator,
    logger: PlayDebugLogger,
    variant: HeuristicVariant,
) : PlayLeadPlanner(context, roleInference, evaluator, logger, variant) {
    private val followOdds = FollowOdds(context)

    override fun chooseLead(legal: List<Int>): Int {
        decisiveLead(legal, Decisive.LOSS)?.let {
            logger.log(it, legal, PlayRoute.LEAD_DECISIVE_LOSS)
            return it
        }

        jokerCallLead(legal)?.let {
            logger.log(it, legal, PlayRoute.LEAD_JOKER_CALL)
            return it
        }

        seimTwoLead(legal)?.let {
            logger.log(it, legal, PlayRoute.LEAD_SEIM_TWO)
            return it
        }

        certainWinWeakestLead(legal)?.let { (idx, route, detail) ->
            logger.log(idx, legal, route, detail)
            return idx
        }

        probeLead(legal)?.let { (idx, route, detail) ->
            logger.log(idx, legal, route, detail)
            return idx
        }

        val idx = evaluator.weakestByPower(legal)
        logger.log(idx, legal, PlayRoute.LEAD_FALLBACK_WEAKEST)
        return idx
    }

    // ♣3 によるジョーカー請求リードの可否判定。
    private fun jokerCallLead(legal: List<Int>): Int? {
        if (context.jokerPlayed) return null
        if (context.trump == Suit.CLUBS) return null
        val me = context.curPlayer
        if ((0 until me.handCount).any { me.hand[it].isJoker() }) return null
        return legal.firstOrNull { me.hand[it].isJokerCall() }
    }

    // セイム (§7-5) 狙いの非切り札 2 リード。2 は他家が同じスートで追従し役札が出なければ最強になる。
    // 後続全員がそのスートを追従できる見込み (FollowOdds) が閾値以上の 2 を選ぶ。
    private fun seimTwoLead(legal: List<Int>): Int? {
        val me = context.curPlayer
        val trump = context.trump
        return legal.firstOrNull {
            val c = me.hand[it]
            c.isRankTwo() && c.suit != trump && followOdds.pAllLaterFollow(c.suit) >= SEIM_FOLLOW_THRESHOLD
        }
    }

    // 確実勝ちの最弱札リード。役札 (パワーカード) しか確実勝ちがないときは温存のため見送る (null)。
    private fun certainWinWeakestLead(legal: List<Int>): LeadPick? {
        val me = context.curPlayer
        val trump = context.trump
        val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
        if (certainWins.isEmpty()) return null
        val idx = evaluator.weakestByPower(certainWins)
        if (me.hand[idx].isPowerCard(context.trump) || me.hand[idx].suit == trump) return null
        return LeadPick(idx, PlayRoute.LEAD_CERTAIN_WIN_WEAKEST, "勝ち候補=${logger.fmtIdxList(certainWins)}")
    }

    // 非切り札の最弱札で探る。副官のスート (未判明時) を持っていればそちらを優先し、副官を炙り出す。
    private fun probeLead(legal: List<Int>): LeadPick? {
        val me = context.curPlayer
        val probe = legal.filter { me.hand[it].suit != context.trump }
        if (probe.isEmpty()) return null
        val adjutantSuit = adjutantProbeSuit()
        val preferred = if (adjutantSuit != null) probe.filter { me.hand[it].suit == adjutantSuit } else emptyList()
        val pool = preferred.ifEmpty { probe }
        val idx = pool.minByOrNull { me.hand[it].rank.ordinal }!!
        val route = if (preferred.isNotEmpty()) PlayRoute.LEAD_ADJUTANT_PROBE else PlayRoute.LEAD_LOW_PROBE
        return LeadPick(idx, route, "候補=${logger.fmtIdxList(pool)}")
    }

    // 探りで優先したい副官カードのスート。副官が未判明で、かつ副官カードが非切り札・非ジョーカーのときだけ
    // 返す。そのスートをリードすれば副官に追従を強いて炙り出しやすい。
    private fun adjutantProbeSuit(): Suit? {
        if (context.adjutantRevealed) return null
        val card = context.adjutantCard
        if (card.isJoker() || card.suit == context.trump) return null
        return card.suit
    }

    private companion object {
        // セイム狙いで非切り札の 2 を投げる下限。後続全員がそのスートを追従できる確率がこれ未満なら見送る。
        const val SEIM_FOLLOW_THRESHOLD = 0.5
    }
}
