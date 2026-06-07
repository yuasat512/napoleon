package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.ai.heuristic.support.seenCards
import napoleon.core.Card
import napoleon.core.CardStrength
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// フォロー時の手選びの基底。戦術 (どの順でどう取りに行くか) は役職ごとに NapoleonFollowPlanner /
// AllyFollowPlanner が個別実装する。ここには両者が使う盤面照会の「部品」だけを置く。
// 部品は事実を返すだけで戦術判断やログ出力を持たないため、片側の戦術を変えても影響しない。
abstract class PlayFollowPlanner(
    protected val context: AiContext,
    protected val roleInference: RoleInference,
    protected val evaluator: TrickEvaluator,
    protected val logger: PlayDebugLogger,
) {
    abstract fun chooseFollow(legal: List<Int>): Int

    // 「この handIdx を出した直後の暫定トップ (provisionalLeaderAfter) が味方候補か」。フォロー戦術が
    // 「出しても暫定トップが味方に残る手」を選ぶ判定で多用する共通述語。
    protected fun provisionalLeaderIsTeammate(handIdx: Int): Boolean =
        roleInference.isLikelyTeammate(evaluator.provisionalLeaderAfter(handIdx).id)

    // セイムで投入する台札スートの 2 の手札位置。台札スート・マイティ・セイム候補・既出スート・
    // 自分より先に出た強パワー札の有無といった成立前提を満たさなければ null。
    protected fun seimFollowIndex(legal: List<Int>): Int? {
        val lead = context.leadSuit
        if (lead == Suit.NONE) return null
        if (lead == context.trump) return null
        if (context.mightyInTrick) return null
        if (!context.sameCandidate) return null
        if (evaluator.suitAlreadyLed(lead)) return null
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (p.handCount < me.hand.size) {
                if (p.playedCard.isPowerCard(context.trump)) return null
            }
        }
        return legal.firstOrNull {
            val c = me.hand[it]
            c.suit == lead && c.rank == Rank.RANK_2
        }
    }

    // 現リーダー札を、場に残った札 (他家手札 + 裏向きキティ) のうち超えられる脅威が残るか。
    // includePower=false なら役札 (マイティ・ジョーカー・正/裏ジャック・切り札A) を脅威から除外する。
    private fun anyBeaterAlive(
        leaderStrength: Int,
        includePower: Boolean,
    ): Boolean {
        val trump = context.trump
        val leadSuit = context.leadSuit
        val mighty = context.mightyInTrick
        val excluded = context.seenCards()
        val leadVoidPossible = evaluator.isLeadSuitVoidPossible(leadSuit)
        for (threat in Card.ALL) {
            if (threat in excluded) continue
            if (!includePower && threat.isPowerCard(trump)) continue
            if (!evaluator.canThreatPlayInTrick(threat, leadSuit, leadVoidPossible)) continue
            val threatStrength = CardStrength.evaluate(threat, trump, leadSuit, mighty || threat.isMighty(), false)
            if (threatStrength > leaderStrength) return true
        }
        return false
    }

    // 現リーダー札を、場に残った札の非パワー札で超えられる脅威が残るか。
    protected fun anyNonPowerBeaterAlive(leaderStrength: Int): Boolean = anyBeaterAlive(leaderStrength, includePower = false)

    // 現リーダー札を、場に残った札のどれでも超えられないか。超えられないなら味方リーダーはそのまま取り切るので、
    // 被せて先取りする必要はない。
    protected fun leaderUnbeatable(leaderStrength: Int): Boolean = !anyBeaterAlive(leaderStrength, includePower = true)

    // 自分より後に出る他家が全員味方候補か (= このトリックは味方が取り切る公算が高いか)。
    protected fun isLikelyTeamTakesTrick(): Boolean {
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (p.handCount < me.hand.size) continue
            if (!roleInference.isLikelyTeammate(p.id)) return false
        }
        return true
    }

    // 手札 handIdx の絵札が、場に残った札で 2 枚以上に超えられる「もう勝てない死に絵札」か。
    protected open fun isDeadHonor(handIdx: Int): Boolean {
        val me = context.curPlayer
        val c = me.hand[handIdx]
        if (!c.rank.isHonor) return false
        val trump = context.trump
        if (c.rank == Rank.RANK_A && c.suit == trump) return false
        if (c.isMighty() || c.isJoker() || c.isRightBower(trump) || c.isLeftBower(trump)) return false
        if (evaluator.isCertainWinAfter(handIdx)) return false
        val excluded = context.seenCards()
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
}
