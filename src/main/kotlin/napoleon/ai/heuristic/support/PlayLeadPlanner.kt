package napoleon.ai.heuristic.support

import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// リード時の手選び。優先度の高い特化ルール (ナポ第1トリック・決着確定・ジョーカー請求) から順に評価し、
// どれにも当てはまらなければ確実勝ちの最弱札 → 高位切り札の吸い出し → 低位の探り → 最弱札のフォールバックに進む。
class PlayLeadPlanner(
    private val context: AiContext,
    private val roleInference: RoleInference,
    private val evaluator: TrickEvaluator,
    private val logger: PlayDebugLogger,
) {
    fun chooseLead(legal: List<Int>): Int {
        val me = context.curPlayer
        val trump = context.trump
        val role = roleInference.myRole()

        if (role == Role.NAPOLEON && context.trickHistory.isEmpty()) {
            napoleonFirstTrickLead(legal)?.let { (idx, reason) ->
                logger.log(idx, legal, "リード: ナポ第1トリック特化、$reason")
                return idx
            }
        }

        decisiveLead(legal, role)?.let { idx ->
            val outcome = if (role == Role.ALLY) "ナポ軍敗北" else "自軍勝利"
            logger.log(idx, legal, "リード: このトリックを取れば${outcome}が確定する札を最強で切る (役=$role)")
            return idx
        }

        jokerCallLeadIndex(legal, role)?.let { idx ->
            logger.log(idx, legal, "リード: ♣3でジョーカー請求 (役=$role)")
            return idx
        }

        val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
        if (certainWins.isNotEmpty()) {
            val idx = certainWins.minByOrNull { evaluator.inherentPower(me.hand[it]) }!!
            if (!me.hand[idx].isPowerCard(trump)) {
                logger.log(
                    idx,
                    legal,
                    "リード: 確実勝ち札の最弱で抜けて強札を温存 (役=$role, 勝ち候補=${logger.fmtIdxList(certainWins)})",
                )
                return idx
            }
        }

        if (role != Role.ALLY) {
            val highTrumps =
                legal.filter {
                    val c = me.hand[it]
                    c.suit == trump && !c.isMighty() && !c.isRightBower(trump)
                }
            if (highTrumps.size >= 2) {
                val idx = highTrumps.maxByOrNull { me.hand[it].rank.ordinal }!!
                logger.log(
                    idx,
                    legal,
                    "リード: 高位切り札を切って敵の切り札を吸い出し (役=$role, 切り札候補=${logger.fmtIdxList(highTrumps)})",
                )
                return idx
            }
        }

        val enemyVoids = collectEnemyVoidSuits()
        if (enemyVoids.isNotEmpty()) {
            val voidProbe =
                legal.filter {
                    val c = me.hand[it]
                    c.suit != trump &&
                        c.suit != Suit.NONE &&
                        !c.rank.isHonor &&
                        c.rank != Rank.RANK_2 &&
                        c.suit in enemyVoids
                }
            if (voidProbe.isNotEmpty()) {
                val idx = voidProbe.minByOrNull { me.hand[it].rank.ordinal }!!
                logger.log(
                    idx,
                    legal,
                    "リード: 敵 void スート (${enemyVoids.joinToString { it.shortName }}) で trump 消費誘発 " +
                        "(役=$role, 候補=${logger.fmtIdxList(voidProbe)})",
                )
                return idx
            }
        }

        val probe =
            legal.filter {
                val c = me.hand[it]
                c.suit != trump && c.suit != Suit.NONE && !c.rank.isHonor && c.rank != Rank.RANK_2
            }
        if (probe.isNotEmpty()) {
            val idx = probe.minByOrNull { me.hand[it].rank.ordinal }!!
            logger.log(idx, legal, "リード: 低位非切り札で探り (候補=${logger.fmtIdxList(probe)})")
            return idx
        }

        val idx = legal.minByOrNull { evaluator.inherentPower(me.hand[it]) }!!
        logger.log(idx, legal, "リード: 該当戦略なし、最小パワー札でしのぐ")
        return idx
    }

    private fun napoleonFirstTrickLead(legal: List<Int>): Pair<Int, String>? {
        val me = context.curPlayer
        val trump = context.trump
        val mightyIdx = legal.firstOrNull { me.hand[it].isMighty() }
        val jokerIdx = legal.firstOrNull { me.hand[it].isJoker() }
        val rightIdx = legal.firstOrNull { me.hand[it].isRightBower(trump) }
        val kittyHonors = context.kittyHonorCards.size

        val justifiedByKitty = kittyHonors >= 1
        val justifiedBySpadeMighty = mightyIdx != null && trump == Suit.SPADES
        if (justifiedByKitty || justifiedBySpadeMighty) {
            val why = if (justifiedByKitty) "kitty=${kittyHonors}絵札回収" else "trump=♠でマイティ=切り札A兼任"
            if (mightyIdx != null) {
                if (!isMightyRiskyAsLead()) return mightyIdx to "$why のためマイティで先制"
                if (jokerIdx != null) return jokerIdx to "$why、SLIPリスクのためジョーカー先行"
                if (rightIdx != null) return rightIdx to "$why、SLIPリスクのため切り札Jで先行"
                return mightyIdx to "$why、代替なくマイティ"
            }
            if (jokerIdx != null) return jokerIdx to "$why のためジョーカーで先制"
        }

        legal
            .firstOrNull { me.hand[it].suit == trump && me.hand[it].rank == Rank.RANK_A }
            ?.let { return it to "切り札Aで吸い出し (kitty絵札なし、役札温存)" }

        val trumpCount = (0 until me.handCount).count { me.hand[it].suit == trump }
        if (trumpCount >= 5) {
            legal
                .filter {
                    val c = me.hand[it]
                    c.suit == trump && !c.isMighty() && !c.isRightBower(trump)
                }.maxByOrNull { me.hand[it].rank.ordinal }
                ?.let { return it to "切り札${trumpCount}枚保有で高位切り札ぶち抜き (役札温存)" }
        }

        legal
            .firstOrNull { me.hand[it].suit == trump && me.hand[it].rank == Rank.RANK_K }
            ?.let { return it to "切り札Kで吸い出し (役札温存)" }

        val adjCard = context.adjutantCard
        legal
            .filter {
                val c = me.hand[it]
                c.suit != trump && c.suit != Suit.NONE && c.rank == Rank.RANK_A && c != adjCard
            }.minByOrNull { idx ->
                val s = me.hand[idx].suit
                (0 until me.handCount).count { me.hand[it].suit == s }
            }?.let { return it to "サイドAで吸い出し (役札 burn の代替、第1トリックでセイム無効)" }

        if (mightyIdx != null) {
            if (!isMightyRiskyAsLead()) return mightyIdx to "代替手段なくマイティ"
            if (jokerIdx != null) return jokerIdx to "代替なくジョーカー (SLIP回避)"
            if (rightIdx != null) return rightIdx to "代替なく切り札J (SLIP回避)"
            return mightyIdx to "代替なくマイティ"
        }
        if (jokerIdx != null) return jokerIdx to "代替なくジョーカー"
        if (rightIdx != null) return rightIdx to "代替なく切り札J"

        val idx = legal.maxByOrNull { evaluator.inherentPower(me.hand[it]) } ?: return null
        return idx to "最強パワー札で凌ぐ (フォールバック)"
    }

    private fun isMightyRiskyAsLead(): Boolean {
        val me = context.curPlayer
        if ((0 until me.handCount).any { me.hand[it].isSlip() }) return false
        if (context.adjutantCard?.isSlip() == true) return false
        if (context.napoleonKittyDiscards?.any { it.isSlip() } == true) return false
        if (context.kittyHonorCards.any { it.isSlip() }) return false
        for (rec in context.trickHistory) for (p in rec.plays) if (p.card.isSlip()) return false
        return true
    }

    private fun jokerCallLeadIndex(
        legal: List<Int>,
        role: Role,
    ): Int? {
        if (context.jokerPlayed) return null
        if (context.trump == Suit.CLUBS) return null
        val me = context.curPlayer
        if ((0 until me.handCount).any { me.hand[it].isJoker() }) return null
        val idx = legal.firstOrNull { me.hand[it].isJokerCall() } ?: return null
        when (role) {
            Role.ADJUTANT -> return null
            Role.NAPOLEON -> {
                if (context.adjutantCard?.isJoker() == true) return null
            }
            Role.ALLY -> {
                if (context.adjutantCard?.isJoker() != true) {
                    val napId = context.napoleonId
                    val adjRevealed = context.adjutantIdIfRevealed
                    if (context.knownNoJoker[napId] && adjRevealed != null && context.knownNoJoker[adjRevealed]) {
                        return null
                    }
                }
            }
        }
        return idx
    }

    private fun decisiveLead(
        legal: List<Int>,
        role: Role,
    ): Int? {
        val me = context.curPlayer
        val winners = legal.filter { evaluator.isCertainWinAfter(it) }
        if (winners.isEmpty()) return null
        val target =
            when (role) {
                Role.NAPOLEON, Role.ADJUTANT -> Decisive.WIN
                Role.ALLY -> Decisive.LOSS
            }
        for (idx in winners.sortedByDescending { evaluator.inherentPower(me.hand[it]) }) {
            if (evaluator.classifyDecisiveOnTake(me.hand[idx].rank.isHonor) == target) return idx
        }
        return null
    }

    private fun collectEnemyVoidSuits(): Set<Suit> {
        val result = HashSet<Suit>()
        val me = context.curPlayer
        val trump = context.trump
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (roleInference.isLikelyTeammate(p.id)) continue
            for (s in context.knownVoids[p.id]) {
                if (s == trump || s == Suit.NONE) continue
                result.add(s)
            }
        }
        return result
    }
}
