package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.support.Decisive
import napoleon.ai.heuristic.support.Role
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.view.AiContext

// ナポレオン軍 (NAPOLEON / ADJUTANT) のフォロー戦術。決着確定は「勝利確定 (WIN)」で奪取する。
// 副官は honor のかからない局面で副官カードの正体を隠す (filterOutAdjutantIfPossible)。
class NapoleonFollowPlanner(
    context: AiContext,
    roleInference: RoleInference,
    evaluator: TrickEvaluator,
    logger: PlayDebugLogger,
) : PlayFollowPlanner(context, roleInference, evaluator, logger) {
    override fun chooseFollow(legal: List<Int>): Int {
        val me = context.curPlayer
        val role = roleInference.myRole()
        val leader = evaluator.determineCurrentLeader()
        val leaderStrength = context.strengthOf(leader.playedCard)
        val leaderIsTeammate = roleInference.isLikelyTeammate(leader.id)
        val trickHonors = evaluator.countTrickHonors()
        val remainingAfterMe = PLAYER_COUNT - context.trickTurn - 1

        // 味方が取り切るトリック (絵札ゼロ・後続全員味方・他家に超えられない味方リーダー) で 2 を出して
        // 味方を抜くのは無意味なので避ける。敵リード、または絵札がかかっていて敵が後続にいるときだけ奪う。
        val teamWillTakeAnyway =
            leaderIsTeammate &&
                (trickHonors == 0 || isLikelyTeamTakesTrick() || leaderUnbeatable(leaderStrength))
        if (!teamWillTakeAnyway) {
            seimFollowIndex(legal)?.let { idx ->
                logger.log(idx, legal, PlayRoute.FOLLOW_SEIM_REVERSAL)
                return idx
            }
        }

        // 敵リードで、取り切ればナポ軍勝利が確定する札があれば最強で奪取する。
        if (!leaderIsTeammate) {
            val beaters = legal.filter { evaluator.canBeatLeader(me.hand[it], leaderStrength) }
            if (beaters.isNotEmpty()) {
                val ordered = beaters.sortedByDescending { context.strengthOf(me.hand[it]) }
                for (idx in ordered) {
                    val c = me.hand[idx]
                    if (evaluator.classifyDecisiveOnTake(c.rank.isHonor) == Decisive.WIN) {
                        logger.log(idx, legal, PlayRoute.FOLLOW_DECISIVE_WIN)
                        return idx
                    }
                }
            }
        }

        if (leaderIsTeammate) {
            if (isLikelyTeamTakesTrick()) {
                // 出した直後の暫定トップが味方でなくなる札は除外する。台札スート不所持で絵札を上乗せすると
                // セイムが崩れ (§7-5)、最強だった味方の 2 が既出の敵台札札に抜かれて絵札を敵に献上するため。
                val deadHonors =
                    legal
                        .filter { isDeadHonor(it) }
                        .filter { provisionalLeaderIsTeammate(it) }
                if (deadHonors.isNotEmpty()) {
                    val idx = deadHonors.minByOrNull { me.hand[it].rank.ordinal }!!
                    logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_TAKES_DEAD_HONOR, "死に絵札=${logger.fmtIdxList(deadHonors)}")
                    return idx
                }
            } else if (!leader.playedCard.isHighRoleCard(context.trump) &&
                trickHonors >= 1 &&
                !leaderUnbeatable(leaderStrength)
            ) {
                // 味方リーダーが残存札で超えられうるときだけ、確実勝ち最弱で先取りして honor を team に確保する。
                val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
                if (certainWins.isNotEmpty()) {
                    val idx = evaluator.weakestByPower(certainWins)
                    logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_LEAD_SECURE_HONOR, "勝ち候補=${logger.fmtIdxList(certainWins)}")
                    return idx
                }
            }
            return preserveOrDumpUnderTeammate(legal, role, trickHonors)
        }

        val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
        if (certainWins.isNotEmpty() && (trickHonors >= 1 || remainingAfterMe >= 1)) {
            val preserved = filterOutAdjutantIfPossible(certainWins, role, trickHonors)
            val pool = preserved.ifEmpty { certainWins }
            val idx = evaluator.weakestByPower(pool)
            if (!(me.hand[idx].isHighRoleCard(context.trump) && trickHonors < 2)) {
                logger.log(idx, legal, PlayRoute.FOLLOW_ENEMY_CERTAIN_TAKE, "勝ち候補=${logger.fmtIdxList(certainWins)}")
                return idx
            }
        }

        val beaters = legal.filter { evaluator.canBeatLeader(me.hand[it], leaderStrength) }

        if (beaters.isEmpty() &&
            remainingAfterMe >= 1 &&
            isLikelyTeamTakesTrick() &&
            !leader.playedCard.isHighRoleCard(context.trump) &&
            anyNonPowerBeaterAlive(leaderStrength)
        ) {
            val deadHonors = legal.filter { isDeadHonor(it) }
            if (deadHonors.isNotEmpty()) {
                val idx = deadHonors.minByOrNull { me.hand[it].rank.ordinal }!!
                logger.log(idx, legal, PlayRoute.FOLLOW_ENEMY_BACK_TEAM_DEAD_HONOR, "死に絵札=${logger.fmtIdxList(deadHonors)}")
                return idx
            }
        }

        val shouldChase = trickHonors >= 1 || (trickHonors == 0 && remainingAfterMe >= 1)
        if (shouldChase && beaters.isNotEmpty()) {
            val preserved = filterOutAdjutantIfPossible(beaters, role, trickHonors)
            val pool = preserved.ifEmpty { beaters }
            val idx = evaluator.weakestByPower(pool)
            logger.log(idx, legal, PlayRoute.FOLLOW_ENEMY_CHASE, "超え候補=${logger.fmtIdxList(beaters)}")
            return idx
        }

        val idx = legal.minByOrNull { evaluator.dumpKey(me.hand[it]) }!!
        when {
            certainWins.isNotEmpty() ->
                logger.log(idx, legal, PlayRoute.FOLLOW_DUMP_HOLD_CERTAIN, "勝ち候補=${logger.fmtIdxList(certainWins)}")
            beaters.isEmpty() -> logger.log(idx, legal, PlayRoute.FOLLOW_DUMP_NO_BEATER)
            else -> logger.log(idx, legal, PlayRoute.FOLLOW_DUMP_NOT_WORTH)
        }
        return idx
    }

    // 味方リードのトリックで取りに行かず低い札を出す場面。出した後も暫定トップが味方に残る手 (holds) と
    // 敵に移る手を切り分け、全て味方なら最小パワー札で温存、一部だけ残り絵札がかかるなら暫定トップを味方に
    // 残す最小札で取り返し、それ以外は絵札を避け献上コスト最小で投げ捨てる。
    private fun preserveOrDumpUnderTeammate(
        legal: List<Int>,
        role: Role,
        trickHonors: Int,
    ): Int {
        val me = context.curPlayer
        val preserved = filterOutAdjutantIfPossible(legal, role, trickHonors)
        val holds = preserved.filter { provisionalLeaderIsTeammate(it) }
        if (holds.size == preserved.size) {
            val idx = evaluator.weakestByPower(preserved)
            logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_LEAD_PRESERVE)
            return idx
        }
        if (holds.isNotEmpty() && trickHonors >= 1 && isLikelyTeamTakesTrick()) {
            val idx = evaluator.weakestByPower(holds)
            logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_SEIM_RECLAIM)
            return idx
        }
        val idx = preserved.minByOrNull { evaluator.dumpKey(me.hand[it]) }!!
        logger.log(idx, legal, PlayRoute.FOLLOW_TEAM_DUMP_SAFE)
        return idx
    }

    // 副官未公開かつ honor のかからない局面では、副官カード (高位ロール札) を候補から外して正体を隠す。
    private fun filterOutAdjutantIfPossible(
        candidates: List<Int>,
        role: Role,
        trickHonors: Int,
    ): List<Int> {
        if (role != Role.ADJUTANT) return candidates
        if (context.adjutantRevealed) return candidates
        if (trickHonors > 0) return candidates
        val adj = context.adjutantCard
        if (!adj.isHighRoleCard(context.trump)) return candidates
        val me = context.curPlayer
        val adjIdx = (0 until me.handCount).firstOrNull { me.hand[it] == adj } ?: return candidates
        val filtered = candidates.filter { it != adjIdx }
        return filtered.ifEmpty { candidates }
    }
}
