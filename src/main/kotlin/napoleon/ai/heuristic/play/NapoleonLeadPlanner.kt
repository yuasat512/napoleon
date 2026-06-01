package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.HeuristicVariant
import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.support.Decisive
import napoleon.ai.heuristic.support.FollowOdds
import napoleon.ai.heuristic.support.Role
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// ナポレオン軍 (NAPOLEON / ADJUTANT) のリード戦術。優先度の高い特化ルール (ナポ第1トリック・決着確定・
// ジョーカー請求) から順に評価し、続いて確実勝ち最弱 → 能動的セイムリード → 高位切り札の吸い出し →
// 敵 void 探り → 低位探り → 最弱札のフォールバックに進む。
class NapoleonLeadPlanner(
    context: AiContext,
    roleInference: RoleInference,
    evaluator: TrickEvaluator,
    logger: PlayDebugLogger,
    variant: HeuristicVariant,
) : PlayLeadPlanner(context, roleInference, evaluator, logger, variant) {
    private val followOdds = FollowOdds(context)

    override fun chooseLead(legal: List<Int>): Int {
        val me = context.curPlayer
        val trump = context.trump
        val role = roleInference.myRole()

        if (role == Role.NAPOLEON && context.trickHistory.isEmpty()) {
            napoleonFirstTrickLead(legal)?.let { (idx, route, detail) ->
                logger.log(idx, legal, route, detail)
                return idx
            }
        }

        decisiveLead(legal, Decisive.WIN)?.let { idx ->
            logger.log(idx, legal, PlayRoute.LEAD_DECISIVE_WIN)
            return idx
        }

        val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
        if (certainWins.isNotEmpty()) {
            val idx = evaluator.weakestByPower(certainWins)
            if (!me.hand[idx].isPowerCard(trump)) {
                logger.log(idx, legal, PlayRoute.LEAD_CERTAIN_WIN_WEAKEST, "勝ち候補=${logger.fmtIdxList(certainWins)}")
                return idx
            }
        }

        // 能動的セイムリード。確実勝ちではないが、後続全員がリードスートを追従できる見込みが高ければ非絵札の 2 を
        // リードしてセイム成立 (2 が最強化) を狙う。成立すれば取り切り、不成立でも捨てるのは非絵札の 2 のみで損失が
        // 小さい (絵札を晒さない)。切り札の 2 は切り札刈りも兼ねるため無条件。
        seimLeadIndex(legal)?.let { idx ->
            logger.log(idx, legal, PlayRoute.LEAD_SEIM_TWO)
            return idx
        }

        val highTrumps =
            legal.filter {
                val c = me.hand[it]
                c.suit == trump && !c.isMighty() && !c.isRightBower(trump)
            }
        if (highTrumps.size >= 2) {
            val idx = highTrumps.maxByOrNull { me.hand[it].rank.ordinal }!!
            logger.log(idx, legal, PlayRoute.LEAD_TRUMP_DRAW, "切り札候補=${logger.fmtIdxList(highTrumps)}")
            return idx
        }

        // 敵陣営の void スートへ低札をリードし、ラフを誘って相手の切り札を1枚消費させる (主導権は渡す)。
        val enemyVoids = collectEnemyVoidSuits()
        if (enemyVoids.isNotEmpty()) {
            val voidProbe =
                legal.filter {
                    val c = me.hand[it]
                    isSideProbeCard(c) && c.suit in enemyVoids
                }
            if (voidProbe.isNotEmpty()) {
                val idx = voidProbe.minByOrNull { me.hand[it].rank.ordinal }!!
                logger.log(
                    idx,
                    legal,
                    PlayRoute.LEAD_ENEMY_VOID_RUFF,
                    "void=${enemyVoids.joinToString("") { it.shortName }},候補=${logger.fmtIdxList(voidProbe)}",
                )
                return idx
            }
        }

        val probe = legal.filter { isSideProbeCard(me.hand[it]) }
        if (probe.isNotEmpty()) {
            val idx = probe.minByOrNull { me.hand[it].rank.ordinal }!!
            logger.log(idx, legal, PlayRoute.LEAD_LOW_PROBE, "候補=${logger.fmtIdxList(probe)}")
            return idx
        }

        val idx = evaluator.weakestByPower(legal)
        logger.log(idx, legal, PlayRoute.LEAD_FALLBACK_WEAKEST)
        return idx
    }

    private fun napoleonFirstTrickLead(legal: List<Int>): LeadPick? {
        val me = context.curPlayer
        val trump = context.trump
        val mightyIdx = legal.firstOrNull { me.hand[it].isMighty() }
        val jokerIdx = legal.firstOrNull { me.hand[it].isJoker() }
        val rightIdx = legal.firstOrNull { me.hand[it].isRightBower(trump) }
        val kittyHonors = context.kittyHonorCards.size

        val justifiedByKitty = kittyHonors >= 1
        val justifiedBySpadeMighty = mightyIdx != null && trump == Suit.SPADES
        if (justifiedByKitty || justifiedBySpadeMighty) {
            val why = if (justifiedByKitty) "kitty=${kittyHonors}絵札回収" else "trump=♠マイティは切り札A兼任"
            if (mightyIdx != null) {
                if (!isMightyRiskyAsLead()) return LeadPick(mightyIdx, PlayRoute.LEAD_NAP1_MIGHTY_PREEMPT, why)
                if (jokerIdx != null) return LeadPick(jokerIdx, PlayRoute.LEAD_NAP1_JOKER_SLIP_AVOID, why)
                if (rightIdx != null) return LeadPick(rightIdx, PlayRoute.LEAD_NAP1_RIGHT_SLIP_AVOID, why)
                return LeadPick(mightyIdx, PlayRoute.LEAD_NAP1_MIGHTY_NO_ALT, why)
            }
            if (jokerIdx != null) return LeadPick(jokerIdx, PlayRoute.LEAD_NAP1_JOKER_PREEMPT, why)
        }

        // 切り札がスペードでマイティ (♠A) を持たないとき、切り札リードは敵マイティに確実に奪われ、
        // 第1トリックでは表向き絵札ごと献上になる。切り札 (スペード) リードを避けてサイドで凌ぐ。
        val avoidTrumpLead = trump == Suit.SPADES && mightyIdx == null
        if (avoidTrumpLead) {
            // サイド絵札を投げて切り札でラフされるより、低位数札で安全に探る (取れない第1トリックは捨てる)。
            legal
                .filter { isSideProbeCard(me.hand[it]) }
                .minByOrNull { me.hand[it].rank.ordinal }
                ?.let { return LeadPick(it, PlayRoute.LEAD_NAP1_AVOID_TRUMP_PROBE) }
        }

        legal
            .firstOrNull { me.hand[it].suit == trump && me.hand[it].rank == Rank.RANK_A }
            ?.let { return LeadPick(it, PlayRoute.LEAD_NAP1_TRUMP_ACE_DRAW) }

        val trumpCount = (0 until me.handCount).count { me.hand[it].suit == trump }
        if (trumpCount >= 5 && !avoidTrumpLead) {
            legal
                .filter {
                    val c = me.hand[it]
                    c.suit == trump && !c.isMighty() && !c.isRightBower(trump)
                }.maxByOrNull { me.hand[it].rank.ordinal }
                ?.let { return LeadPick(it, PlayRoute.LEAD_NAP1_TRUMP_HIGH_SMASH, "切り札${trumpCount}枚") }
        }

        if (!avoidTrumpLead) {
            legal
                .firstOrNull { me.hand[it].suit == trump && me.hand[it].rank == Rank.RANK_K }
                ?.let { return LeadPick(it, PlayRoute.LEAD_NAP1_TRUMP_KING_DRAW) }
        }

        val adjCard = context.adjutantCard
        legal
            .filter {
                val c = me.hand[it]
                c.suit != trump && c.suit != Suit.NONE && c.rank == Rank.RANK_A && c != adjCard
            }.minByOrNull { idx ->
                val s = me.hand[idx].suit
                (0 until me.handCount).count { me.hand[it].suit == s }
            }?.let { return LeadPick(it, PlayRoute.LEAD_NAP1_SIDE_ACE_DRAW) }

        if (mightyIdx != null) {
            // risky なときだけ SLIP 回避でジョーカー/切り札Jを優先し、なければ (非risky含め) マイティに落とす。
            if (isMightyRiskyAsLead()) {
                if (jokerIdx != null) return LeadPick(jokerIdx, PlayRoute.LEAD_NAP1_JOKER_SLIP_AVOID)
                if (rightIdx != null) return LeadPick(rightIdx, PlayRoute.LEAD_NAP1_RIGHT_SLIP_AVOID)
            }
            return LeadPick(mightyIdx, PlayRoute.LEAD_NAP1_MIGHTY_NO_ALT)
        }
        if (jokerIdx != null) return LeadPick(jokerIdx, PlayRoute.LEAD_NAP1_JOKER_NO_ALT)
        if (rightIdx != null) return LeadPick(rightIdx, PlayRoute.LEAD_NAP1_RIGHT_NO_ALT)

        // avoidTrumpLead では切り札 (スペード) を除いた中から最強で凌ぐ。スペードしか残らない手札では全体から選ぶ。
        val fallbackPool = if (avoidTrumpLead) legal.filter { me.hand[it].suit != trump } else legal
        val idx = fallbackPool.ifEmpty { legal }.maxByOrNull { evaluator.inherentPower(me.hand[it]) } ?: return null
        return LeadPick(idx, PlayRoute.LEAD_NAP1_FALLBACK)
    }

    private fun isMightyRiskyAsLead(): Boolean {
        val me = context.curPlayer
        if ((0 until me.handCount).any { me.hand[it].isSlip() }) return false
        if (context.adjutantCard.isSlip()) return false
        if (context.napoleonKittyDiscards?.any { it.isSlip() } == true) return false
        if (context.kittyHonorCards.any { it.isSlip() }) return false
        for (rec in context.trickHistory) for (p in rec.plays) if (p.card.isSlip()) return false
        return true
    }

    // 切り札でもジョーカーでも絵札でも 2 でもないサイドスートの数札 (ラフ誘発・低位探りに使う捨て候補)。
    private fun isSideProbeCard(c: Card): Boolean =
        c.suit != context.trump && c.suit != Suit.NONE && !c.rank.isHonor && c.rank != Rank.RANK_2

    // CANDIDATE: 能動的セイムリードの候補。非絵札の 2 のうち、後続 (まだ手番の来ていない相手) 全員がその
    // スートを追従できる見込み (FollowOdds) が閾値以上のものを、最も成立しそうなものから選ぶ。第1トリックは
    // セイム不成立 (§7-1) なので対象外。切り札の 2 は同時に切り札刈り (敵切り札の吸い出し) も兼ねる。
    private fun seimLeadIndex(legal: List<Int>): Int? {
        if (context.trickHistory.isEmpty()) return null
        val me = context.curPlayer
        val trump = context.trump
        // 切り札の 2 は無条件で最優先 (閾値0相当)。セイム成立なら取り切り、不成立でも切り札刈り (敵切り札の
        // 吸い出し) が成立し、捨てるのは非絵札の 2 なので損失が小さい。各スートの 2 は高々1枚。
        legal
            .firstOrNull { me.hand[it].isRankTwo() && me.hand[it].suit == trump }
            ?.let { return it }
        // サイドの 2 は void→ラフで主導権を渡すリスクがあるので、後続フォロー見込みが閾値以上のときだけ投げる。
        return legal
            .filter { me.hand[it].isRankTwo() && me.hand[it].suit != trump }
            .map { it to followOdds.pAllLaterFollow(me.hand[it].suit) }
            .filter { it.second >= SEIM_LEAD_SIDE_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    private companion object {
        // サイドの 2 をセイム狙いで投げる下限。後続全員がそのスートを追従できる確率がこれ未満なら、void→ラフで
        // 主導権を渡すリスクが勝つとみて投げない。切り札の 2 は切り札刈り効果も兼ねるため閾値を設けず最優先。
        const val SEIM_LEAD_SIDE_THRESHOLD = 0.6
    }
}
