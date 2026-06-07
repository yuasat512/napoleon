package napoleon.ai.heuristic.play

import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.ai.heuristic.support.Decisive
import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// リード時の手選びの基底。戦術 (どの順でどの戦略を試すか) は役職ごとに NapoleonLeadPlanner /
// AllyLeadPlanner が個別実装する。ここには両者が使う盤面照会の「部品」だけを置く。
// 部品は事実を返すだけで戦術判断やログ出力を持たないため、片側の戦術を変えても影響しない。
abstract class PlayLeadPlanner(
    protected val context: AiContext,
    protected val roleInference: RoleInference,
    protected val evaluator: TrickEvaluator,
    protected val logger: PlayDebugLogger,
) {
    abstract fun chooseLead(legal: List<Int>): Int

    // このトリックを取ると指定の決着 (WIN=自軍勝利 / LOSS=ナポ軍敗北) が確定する札があれば、
    // 最強パワーから順に該当する手札位置を返す。
    protected fun decisiveLead(
        legal: List<Int>,
        target: Decisive,
    ): Int? {
        val me = context.curPlayer
        val winners = legal.filter { evaluator.isGuaranteedWinAfter(it) }
        if (winners.isEmpty()) return null
        for (idx in winners.sortedByDescending { evaluator.inherentPower(me.hand[it]) }) {
            if (evaluator.classifyDecisiveOnTake(me.hand[idx].rank.isHonor) == target) return idx
        }
        return null
    }

    // 自分以外の敵候補 (味方候補でないプレイヤー) で void と判明しているスート集合。
    protected fun collectEnemyVoidSuits(): Set<Suit> =
        buildSet {
            val me = context.curPlayer
            val trump = context.trump
            for (p in context.publicPlayers) {
                if (p.id == me.id) continue
                if (roleInference.isLikelyTeammate(p.id)) continue
                for (s in context.knownVoids[p.id]) {
                    if (s == trump || s == Suit.NONE) continue
                    add(s)
                }
            }
        }
}

// リード手の選択結果。idx=手札位置、route=戦術ルート (集計キー)、detail=可変補助情報 (候補札など)。
// ルートや detail が呼び出し側で一意に決まらない選択 (第1トリック・確実勝ち・探り) で、候補メソッドが
// 選んだ手と一緒にログ材料を返し、呼び出し側を logger.log→return の単一形に統一するために使う。
data class LeadPick(
    val idx: Int,
    val route: PlayRoute,
    val detail: String = "",
)
