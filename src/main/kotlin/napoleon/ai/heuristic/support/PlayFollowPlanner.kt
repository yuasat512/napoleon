package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.core.CardStrength
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Rank
import napoleon.engine.view.AiContext

// フォロー時の手選び。セイム成立 → 決着確定札の奪取 → 味方リード時の温存/上乗せ → 敵リード時の確実勝ち奪取
// → 死に絵札の上乗せ → 追撃 → 数札出し捨て、の優先順で評価する。
class PlayFollowPlanner(
    private val context: AiContext,
    private val roleInference: RoleInference,
    private val evaluator: TrickEvaluator,
    private val logger: PlayDebugLogger,
) {
    fun chooseFollow(legal: List<Int>): Int {
        val me = context.curPlayer
        val role = roleInference.myRole()
        val leader = evaluator.determineCurrentLeader()
        val leaderStrength = context.strengthOf(leader.playedCard)
        val leaderIsTeammate = roleInference.isLikelyTeammate(leader.id)
        val trickHonors = evaluator.countTrickHonors()
        val remainingAfterMe = PLAYER_COUNT - context.trickTurn - 1

        seimFollowIndex(legal)?.let { idx ->
            logger.log(idx, legal, "フォロー: セイム成立条件が揃ったので2を投入して逆転狙い")
            return idx
        }

        if (!leaderIsTeammate) {
            val beaters = legal.filter { evaluator.canBeatLeader(me.hand[it], leaderStrength) }
            if (beaters.isNotEmpty()) {
                val ordered = beaters.sortedByDescending { context.strengthOf(me.hand[it]) }
                for (idx in ordered) {
                    val c = me.hand[idx]
                    val dec = evaluator.classifyDecisiveOnTake(c.rank.isHonor)
                    val commit =
                        when (role) {
                            Role.NAPOLEON, Role.ADJUTANT -> dec == Decisive.WIN
                            Role.ALLY -> dec == Decisive.LOSS
                        }
                    if (commit) {
                        val outcome = if (dec == Decisive.WIN) "ナポ軍勝利" else "ナポ軍敗北"
                        logger.log(idx, legal, "フォロー: 取り切れば${outcome}が確定するため最強で奪取 (役=$role)")
                        return idx
                    }
                }
            }
        }

        if (leaderIsTeammate) {
            if (isLikelyTeamTakesTrick()) {
                val deadHonors = legal.filter { isDeadHonor(it) }
                if (deadHonors.isNotEmpty()) {
                    val idx = deadHonors.minByOrNull { me.hand[it].rank.ordinal }!!
                    logger.log(
                        idx,
                        legal,
                        "フォロー: 味方が取り切る公算が高いので、もう勝てない死に絵札を上乗せして加点 " +
                            "(死に絵札=${logger.fmtIdxList(deadHonors)})",
                    )
                    return idx
                }
            } else if (!leader.playedCard.isHighRoleCard(context.trump) && trickHonors >= 1) {
                val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
                if (certainWins.isNotEmpty()) {
                    val idx = evaluator.weakestByPower(certainWins)
                    logger.log(
                        idx,
                        legal,
                        "フォロー: 味方リードだが現リーダー札が役札級でなく敵候補が後続のため、" +
                            "確実勝ち最弱で奪取して honor を team に確保 " +
                            "(勝ち候補=${logger.fmtIdxList(certainWins)})",
                    )
                    return idx
                }
            }
            val preserved = filterOutAdjutantIfPossible(legal, role, trickHonors)
            val idx = evaluator.weakestByPower(preserved)
            logger.log(idx, legal, "フォロー: 味方リードのため最小パワー札で温存")
            return idx
        }

        val certainWins = legal.filter { evaluator.isCertainWinAfter(it) }
        if (certainWins.isNotEmpty() && (trickHonors >= 1 || remainingAfterMe >= 1)) {
            val preserved = filterOutAdjutantIfPossible(certainWins, role, trickHonors)
            val pool = preserved.ifEmpty { certainWins }
            val idx = evaluator.weakestByPower(pool)
            if (!(me.hand[idx].isHighRoleCard(context.trump) && trickHonors < 2)) {
                logger.log(
                    idx,
                    legal,
                    "フォロー: 敵リードのトリックを最弱の確実勝ち札で取り切り (勝ち候補=${logger.fmtIdxList(certainWins)})",
                )
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
                logger.log(
                    idx,
                    legal,
                    "フォロー: 敵リードだが自分の後ろは全員味方候補のため、死に絵札を上乗せして team 取り切り狙い " +
                        "(死に絵札=${logger.fmtIdxList(deadHonors)})",
                )
                return idx
            }
        }

        val shouldChase = trickHonors >= 1 || (trickHonors == 0 && remainingAfterMe >= 1)
        if (shouldChase && beaters.isNotEmpty()) {
            val preserved = filterOutAdjutantIfPossible(beaters, role, trickHonors)
            val pool = preserved.ifEmpty { beaters }
            val idx = evaluator.weakestByPower(pool)
            logger.log(
                idx,
                legal,
                "フォロー: 確実勝ちはないが後続逆転リスクを承知で最小コストでリーダーを超えて追撃 " +
                    "(超え候補=${logger.fmtIdxList(beaters)})",
            )
            return idx
        }

        val idx = legal.minByOrNull { evaluator.dumpKey(me.hand[it]) }!!
        val why =
            when {
                certainWins.isNotEmpty() ->
                    "確実勝ち札はあるが場の絵札ゼロ&自分最終手番のため温存 (勝ち候補=${logger.fmtIdxList(certainWins)})"
                beaters.isEmpty() -> "リーダーを超えられる札がない"
                else -> "リーダー超え可だが追う価値が薄い (場の絵札ゼロ&自分最終手番)"
            }
        logger.log(idx, legal, "フォロー: 数札優先で出し捨て ($why)")
        return idx
    }

    private fun seimFollowIndex(legal: List<Int>): Int? {
        val lead = context.leadSuit ?: return null
        if (lead == context.trump) return null
        if (context.mightyInTrick) return null
        if (!context.sameCandidate) return null
        if (evaluator.suitAlreadyLed(lead)) return null
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (p.handCount < me.handCount) {
                if (p.playedCard.isPowerCard(context.trump)) return null
            }
        }
        return legal.firstOrNull {
            val c = me.hand[it]
            c.suit == lead && c.rank == Rank.RANK_2
        }
    }

    private fun anyNonPowerBeaterAlive(leaderStrength: Int): Boolean {
        val trump = context.trump
        val leadSuit = context.leadSuit
        val mightyInTrick = context.mightyInTrick
        val excluded = evaluator.buildExcludedSet()
        val leadVoidPossible = evaluator.isLeadSuitVoidPossible(leadSuit)
        for (threat in Card.ALL) {
            if (threat in excluded) continue
            if (threat.isPowerCard(trump)) continue
            if (!evaluator.canThreatPlayInTrick(threat, leadSuit, leadVoidPossible)) continue
            val threatMighty = mightyInTrick || threat.isMighty()
            val threatStrength = CardStrength.evaluate(threat, trump, leadSuit, threatMighty, false)
            if (threatStrength > leaderStrength) return true
        }
        return false
    }

    private fun isLikelyTeamTakesTrick(): Boolean {
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (p.handCount < me.handCount) continue
            if (!roleInference.isLikelyTeammate(p.id)) return false
        }
        return true
    }

    private fun isDeadHonor(handIdx: Int): Boolean {
        val me = context.curPlayer
        val c = me.hand[handIdx]
        if (!c.rank.isHonor) return false
        val trump = context.trump
        if (c.rank == Rank.RANK_A && c.suit == trump) return false
        if (c.isMighty() || c.isJoker() || c.isRightBower(trump) || c.isLeftBower(trump)) return false
        if (evaluator.isCertainWinAfter(handIdx)) return false
        val excluded = evaluator.buildExcludedSet()
        val lead = c.suit
        val mightyInT = context.mightyInTrick
        var count = 0
        for (threat in Card.ALL) {
            if (threat in excluded) continue
            if (threat === c) continue
            val threatMighty = mightyInT || threat.isMighty()
            val myStr = CardStrength.evaluate(c, trump, lead, threatMighty, false)
            val threatStr = CardStrength.evaluate(threat, trump, lead, threatMighty, false)
            if (threatStr > myStr) {
                count++
                if (count >= 2) return true
            }
        }
        return false
    }

    private fun filterOutAdjutantIfPossible(
        candidates: List<Int>,
        role: Role,
        trickHonors: Int,
    ): List<Int> {
        if (role != Role.ADJUTANT) return candidates
        if (context.adjutantRevealed) return candidates
        if (trickHonors > 0) return candidates
        val adj = context.adjutantCard ?: return candidates
        if (!adj.isHighRoleCard(context.trump)) return candidates
        val me = context.curPlayer
        val adjIdx = (0 until me.handCount).firstOrNull { me.hand[it] == adj } ?: return candidates
        val filtered = candidates.filter { it != adjIdx }
        return filtered.ifEmpty { candidates }
    }
}
