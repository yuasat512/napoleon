package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.support.Decisive
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Card
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// 連合軍のフォロー戦術。非切り札絵札は味方取り切りトリックに乗せて消化し、
// ジョーカー請求トリックでは絵札温存で献上回避。
class AllyFollowPlanner(
    context: AiContext,
    roleInference: RoleInference,
    evaluator: TrickEvaluator,
    logger: PlayDebugLogger,
) : PlayFollowPlanner(context, roleInference, evaluator, logger) {
    override fun chooseFollow(legal: List<Int>): Int {
        val me = context.curPlayer
        // 捨て序列の温存補正に使う「温存特殊札と同スートの集合」は1手番中不変なので候補ループの外で1度だけ算出する。
        val heldSuits = heldPreserveSuits()

        // 出した直後の暫定トップが敵かつ獲得絵札でナポ軍が勝利ラインに達する手は負け確定として除外。
        // 全手が負け確定なら legal のまま進む。
        val legal2 =
            legal
                .filter {
                    provisionalLeaderIsTeammate(it) ||
                        evaluator.classifyDecisiveOnTake(me.hand[it].rank.isHonor) != Decisive.WIN
                }.ifEmpty { legal }

        val leader = evaluator.determineCurrentLeader()
        val leaderStrength = context.strengthOf(leader.playedCard)
        val leaderIsTeammate = roleInference.isLikelyTeammate(leader.id)
        val trickHonors = evaluator.countTrickHonors()
        val remainingAfterMe = PLAYER_COUNT - context.trickTurn - 1

        // ナポ軍敗北が確定する保証勝ち札があれば最弱で奪取して即決着。
        val decisiveWins =
            legal2.filter {
                evaluator.isGuaranteedWinAfter(it) &&
                    evaluator.classifyDecisiveOnTake(me.hand[it].rank.isHonor) == Decisive.LOSS
            }
        if (decisiveWins.isNotEmpty()) {
            val idx = evaluator.weakestByPower(decisiveWins)
            logger.log(idx, legal2, PlayRoute.FOLLOW_DECISIVE_LOSS)
            return idx
        }

        // 味方取り切りトリックに絵札を積むだけで連合勝利確定なら最小絵札で加点。
        if (isLikelyTeamTakesTrick() &&
            evaluator.classifyDecisiveOnTake(true) == Decisive.LOSS
        ) {
            val clinchers =
                legal2.filter {
                    me.hand[it].rank.isHonor &&
                        provisionalLeaderIsTeammate(it)
                }
            if (clinchers.isNotEmpty()) {
                val idx = clinchers.minByOrNull { me.hand[it].rank.ordinal }!!
                logger.log(idx, legal2, PlayRoute.FOLLOW_CLINCH_HONOR, "候補=${logger.fmtIdxList(clinchers)}")
                return idx
            }
        }

        // 味方取り切り・リーダー無敵でないときだけ 2 投入を許可。
        if (!(isLikelyTeamTakesTrick() || leaderUnbeatable(leaderStrength))) {
            seimFollowIndex(legal2)?.let { idx ->
                logger.log(idx, legal2, PlayRoute.FOLLOW_SEIM_REVERSAL)
                return idx
            }
        }

        // ジョーカー請求で未出なら絵札温存。
        val preserveVsJokerCall = context.jokerCallActive && !context.jokerPlayed

        // 出した後も暫定トップが味方に残る非切り札絵札は小さい順に処分して獲得に変える。
        if (!preserveVsJokerCall) {
            val trump = context.trump
            val sideHonors =
                legal2.filter {
                    val c = me.hand[it]
                    c.rank.isHonor &&
                        c.suit != trump &&
                        !c.isPowerCard(trump) &&
                        provisionalLeaderIsTeammate(it)
                }
            if (sideHonors.isNotEmpty()) {
                val idx = sideHonors.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
                logger.log(idx, legal2, PlayRoute.FOLLOW_SIDE_HONOR_DUMP, "候補=${logger.fmtIdxList(sideHonors)}")
                return idx
            }
        }

        if (leaderIsTeammate) {
            if (isLikelyTeamTakesTrick()) {
                // ジョーカー請求以外で味方取り切り確定なら、暫定トップが崩れない死に絵札を上乗せ。
                val deadHonors =
                    (if (preserveVsJokerCall) emptyList() else legal2.filter { isDeadHonor(it) })
                        .filter { provisionalLeaderIsTeammate(it) }
                if (deadHonors.isNotEmpty()) {
                    val idx = deadHonors.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
                    logger.log(idx, legal2, PlayRoute.FOLLOW_TEAM_TAKES_DEAD_HONOR, "死に絵札=${logger.fmtIdxList(deadHonors)}")
                    return idx
                }
            } else if (trickHonors >= 1 &&
                !leaderUnbeatable(leaderStrength)
            ) {
                // 味方リードが超えられうるとき保証勝ち最弱で先取りして honor を確保。
                val certainWins = legal2.filter { evaluator.isGuaranteedWinAfter(it) }
                if (certainWins.isNotEmpty()) {
                    val idx = evaluator.weakestByPower(certainWins)
                    logger.log(idx, legal2, PlayRoute.FOLLOW_TEAM_LEAD_SECURE_HONOR, "勝ち候補=${logger.fmtIdxList(certainWins)}")
                    return idx
                }
            }
            return preserveOrDumpUnderTeammate(legal2, trickHonors, leaderStrength, heldSuits)
        }

        val certainWins = legal2.filter { evaluator.isGuaranteedWinAfter(it) }
        if (certainWins.isNotEmpty() && trickHonors >= 2) {
            val idx = evaluator.weakestByPower(certainWins)
            if (!me.hand[idx].isHighRoleCard(context.trump)) {
                logger.log(idx, legal2, PlayRoute.FOLLOW_ENEMY_CERTAIN_TAKE, "勝ち候補=${logger.fmtIdxList(certainWins)}")
                return idx
            }
        }

        val beaters = legal2.filter { evaluator.canBeatLeader(me.hand[it], leaderStrength) }
        if (beaters.isNotEmpty() && trickHonors >= 1 && !preserveVsJokerCall) {
            // 高位役札 (マイティ・ジョーカー・正/裏ジャック・切り札A) しか beater がないとき、
            // 絵札1枚かつ非切り札絵札リーダーなら温存する。役札を1絵札に消費するのは「もったい使い」で、
            // 絵札2枚以上 or リーダーが切り札絵札 (より大きな honor 奪取機会) のときだけコミットする。
            val nonRole = beaters.filter { !me.hand[it].isHighRoleCard(context.trump) }
            val leaderIsTrumpHonor =
                leader.playedCard.suit == context.trump && leader.playedCard.rank.isHonor
            val pool =
                when {
                    nonRole.isNotEmpty() -> nonRole
                    trickHonors >= 2 || leaderIsTrumpHonor -> beaters
                    else -> null
                }
            if (pool != null) {
                val idx = evaluator.weakestByPower(pool)
                logger.log(idx, legal2, PlayRoute.FOLLOW_ENEMY_CHASE, "超え候補=${logger.fmtIdxList(beaters)}")
                return idx
            }
        }

        if (!preserveVsJokerCall &&
            beaters.isEmpty() &&
            remainingAfterMe >= 2 &&
            isLikelyTeamTakesTrick() &&
            !leader.playedCard.isHighRoleCard(context.trump) &&
            anyNonPowerBeaterAlive(leaderStrength)
        ) {
            val deadHonors = legal2.filter { isDeadHonor(it) }
            if (deadHonors.isNotEmpty()) {
                val idx = deadHonors.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
                logger.log(idx, legal2, PlayRoute.FOLLOW_ENEMY_BACK_TEAM_DEAD_HONOR, "死に絵札=${logger.fmtIdxList(deadHonors)}")
                return idx
            }
        }

        val idx = legal2.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
        when {
            preserveVsJokerCall -> logger.log(idx, legal2, PlayRoute.FOLLOW_DUMP_JOKER_CALL)
            certainWins.isNotEmpty() ->
                logger.log(idx, legal2, PlayRoute.FOLLOW_DUMP_HOLD_CERTAIN, "勝ち候補=${logger.fmtIdxList(certainWins)}")
            beaters.isEmpty() -> logger.log(idx, legal2, PlayRoute.FOLLOW_DUMP_NO_BEATER)
            else -> logger.log(idx, legal2, PlayRoute.FOLLOW_DUMP_NOT_WORTH)
        }
        return idx
    }

    // 味方リードのトリックで取りに行かない場面。暫定トップを味方に保てる手と保てない手で分岐する。
    private fun preserveOrDumpUnderTeammate(
        legal: List<Int>,
        trickHonors: Int,
        leaderStrength: Int,
        heldSuits: Set<Suit>,
    ): Int {
        val me = context.curPlayer
        val holds = legal.filter { provisionalLeaderIsTeammate(it) }
        val beaters =
            legal
                .filter { evaluator.canBeatLeader(me.hand[it], leaderStrength) }
                .filter { !me.hand[it].rank.isHonor }

        if (holds.size == legal.size) {
            val idx =
                if (beaters.isNotEmpty()) {
                    evaluator.weakestByPower(beaters)
                } else {
                    legal.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
                }
            logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_LEAD_PRESERVE)
            return idx
        }
        if (holds.isNotEmpty() && trickHonors >= 1 && isLikelyTeamTakesTrick()) {
            val idx = evaluator.weakestByPower(holds)
            logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_SEIM_RECLAIM)
            return idx
        }
        val idx = legal.minByOrNull { allyDumpKey(me.hand[it], heldSuits) }!!
        logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_DUMP_SAFE)
        return idx
    }

    // 連合フォローの捨て序列。基準は evaluator.dumpKey (小さいほど先に捨てる) に、連合に固有の温存補正を足す。
    // よろめき (♥Q) はマイティ狩り、ジョーカー請求札 (♣3) はジョーカー請求に使いたいので安易に捨てない。
    // 温存したい特殊札と同スートの札は、そのスートを切らさないため同条件なら後回しにする。
    private fun allyDumpKey(
        card: Card,
        heldSuits: Set<Suit>,
    ): Int = evaluator.dumpKey(card) + preserveBonus(card, heldSuits)

    private fun preserveBonus(
        card: Card,
        heldSuits: Set<Suit>,
    ): Int {
        var bonus = 0
        // dumpKey の 1000 刻みカテゴリはまたがず、同カテゴリ内で最も捨てにくい位置へ引き上げる幅。
        if (card.isSlip() || card.isJokerCall()) bonus += SPECIAL_PRESERVE
        if (card.suit in heldSuits) bonus += SUIT_PRESERVE
        return bonus
    }

    // 温存したい特殊札 (裏J・マイティ・よろめき・ジョーカー請求札) を今手札に持っているスートの集合。
    private fun heldPreserveSuits(): Set<Suit> {
        val me = context.curPlayer
        val trump = context.trump
        val suits = HashSet<Suit>()
        for (i in 0 until me.handCount) {
            val c = me.hand[i]
            if (c.isLeftBower(trump) || c.isMighty() || c.isSlip() || c.isJokerCall()) suits += c.suit
        }
        return suits
    }

    // 役札・切り札以外の絵札で、同スートにまだ上位ランクが残っているもの。
    override fun isDeadHonor(handIdx: Int): Boolean {
        val me = context.curPlayer
        val c = me.hand[handIdx]
        if (!c.rank.isHonor) return false
        val trump = context.trump
        if (c.suit == trump) return false
        if (c.isPowerCard(trump)) return false
        val gone = HashSet<Card>()
        for (p in context.publicPlayers) {
            if (p.handCount < me.handCount) gone += p.playedCard
        }
        for (rec in context.trickHistory) for (play in rec.plays) gone += play.card
        for (h in context.kittyHonorCards) gone += h
        for (r in Rank.entries) {
            if (r <= c.rank) continue
            if (Card.of(c.suit, r) !in gone) return true
        }
        return false
    }

    companion object {
        // 特殊札 (よろめき・ジョーカー請求札) の温存補正。dumpKey の同カテゴリ内で最も捨てにくくする幅。
        private const val SPECIAL_PRESERVE = 500

        // 温存特殊札と同スートのカードに効くタイブレーク補正 (同条件なら後回し)。
        private const val SUIT_PRESERVE = 200
    }
}
