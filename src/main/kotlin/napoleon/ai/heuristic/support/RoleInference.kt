package napoleon.ai.heuristic.support

import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.view.AiContext

enum class Role { NAPOLEON, ADJUTANT, ALLY }

// 視点プレイヤーから見た自分・他プレイヤーの役職 (NAPOLEON / ADJUTANT / ALLY) を、
// 副官カードの公開状況・既出札・絵札カウント等の公開情報のみから推定する。
// 副官未公開時は「ありうる副官候補」を集合として扱う。
class RoleInference(
    private val context: AiContext,
) {
    fun myRole(): Role {
        val me = context.curPlayer
        if (me.id == context.napoleonId) return Role.NAPOLEON
        val revealed = context.adjutantIdIfRevealed
        if (revealed != null && revealed == me.id) {
            return Role.ADJUTANT
        }
        val adj = context.adjutantCard
        for (i in 0 until me.handCount) {
            if (me.hand[i] == adj) return Role.ADJUTANT
        }
        return Role.ALLY
    }

    // 副官の候補プレイヤー集合。公開済みなら 1 名、自分が副官なら自分のみ。
    // ナポレオン視点では自分の手札やキティから副官カードの所在を引き当てられれば副官なし確定。
    // それ以外は仮説整合性チェックで絞り込む。連合軍視点では副官なしも仮説に含める。
    // 勝敗確定後も副官未公開のままプレイ続行する状況 (playThrough) では全仮説が「確定済み」
    // と矛盾して空になりうるため、空集合を許容し呼び出し側で未確定 (絞り込み不能) として扱う。
    fun adjutantCandidates(): List<Int> {
        val revealedAdj = context.adjutantIdIfRevealed
        if (revealedAdj != null) return listOf(revealedAdj)

        val viewer = context.curPlayer
        val role = myRole()
        if (role == Role.ADJUTANT) return listOf(viewer.id)

        val napId = context.napoleonId
        if (role == Role.NAPOLEON) {
            val adj = context.adjutantCard
            val adjInHand = (0 until viewer.handCount).any { viewer.hand[it] == adj }
            val adjInKitty = context.napoleonKittyDiscards!!.contains(adj)
            if (adjInHand || adjInKitty) return listOf(napId)
        }

        // 連合軍視点では napId (副官なし) も候補に残す。ナポレオン視点では napId == viewer.id の
        // ため自動的に除外され、副官なし確定は上で処理済み。
        val filtered = mutableListOf<Int>()
        for (pid in 0 until PLAYER_COUNT) {
            if (pid == viewer.id) continue
            if (hypothesisConsistent(pid)) filtered += pid
        }
        return filtered
    }

    fun isLikelyTeammate(pid: Int): Boolean {
        val me = context.curPlayer.id
        if (pid == me) return true
        val myRole = myRole()
        val inferred = inferRoleOf(pid)
        if (inferred != null) {
            return when (myRole) {
                Role.NAPOLEON -> inferred == Role.ADJUTANT
                Role.ADJUTANT -> inferred == Role.NAPOLEON
                Role.ALLY -> inferred == Role.ALLY
            }
        }
        // ここに到達するのは inferRoleOf が null を返したときのみ = 副官未公開かつ未特定。
        // 公開済みなら inferRoleOf が必ず非 null を返すため、revealedAdj は考慮不要。
        val napId = context.napoleonId
        return when (myRole) {
            Role.NAPOLEON -> false
            Role.ADJUTANT -> pid == napId
            Role.ALLY -> pid != napId
        }
    }

    // pid の役職を、副官候補集合 (adjutantCandidates) との関係から推定する。
    // ナポレオン・自分は確定情報。それ以外は候補が pid 単独なら副官確定、pid を含まなければ
    // 連合軍確定、複数候補に pid が残るなら未確定 (null)。候補が空 (playThrough の確定後) も
    // 未確定として扱う。
    private fun inferRoleOf(pid: Int): Role? {
        if (pid == context.napoleonId) return Role.NAPOLEON
        if (pid == context.curPlayer.id) return myRole()

        val cands = adjutantCandidates()
        return when {
            cands.isEmpty() -> null
            pid !in cands -> Role.ALLY
            cands.size == 1 -> Role.ADJUTANT
            else -> null
        }
    }

    // candAdjId が副官だと仮定したとき、現在までの観測 (副官カードのスート void、ジョーカー請求の不出など)
    // および絵札カウントから導かれるゲーム状態と矛盾しないかを判定する。
    // 矛盾していたら候補から除外する。
    private fun hypothesisConsistent(candAdjId: Int): Boolean {
        val napId = context.napoleonId
        val viewer = context.curPlayer

        if (candAdjId == viewer.id) return false

        if (viewer.id == napId && candAdjId == napId) return false

        // 副官カードのスート void / ジョーカー非保持から、その候補が副官カードを保持し得ないと確定すれば矛盾。
        // 副官なし (candAdjId == napId) では副官カードがキティに裏向きで戻されている可能性があるため原則スキップ。
        // ただし絵札の副官カードはキティ送りなら即公開されるので、未公開なら手札にあると確定でき副官なしにも適用する。
        // ジョーカー等の非絵札は裏向きで戻せて公開されない (副官なしの可能性が残る) ため、スキップを維持する。
        val adj = context.adjutantCard
        if (candAdjId != napId || adj.rank.isHonor) {
            if (adj.isJoker()) {
                if (context.knownNoJoker[candAdjId]) return false
            } else {
                if (adj.suit in context.knownVoids[candAdjId]) return false
            }
        }

        // 仮説下のナポ軍/連合軍の絵札合計が、まだ確定 (PERFECT/WIN/LOSS) を打っていないことを要する。
        // 既に確定するはずの状態と矛盾するなら、その仮説 (= candAdjId が副官) はあり得ない。
        val players = context.publicPlayers
        val total = players.sumOf { it.honorsTaken }
        val napHonors =
            if (candAdjId == napId) {
                players[napId].honorsTaken
            } else {
                players[napId].honorsTaken + players[candAdjId].honorsTaken
            }
        val allyHonors = total - napHonors
        val bidTarget = context.bid!!.target
        if (napHonors == HONOR_COUNT) return false
        if (allyHonors > HONOR_COUNT - bidTarget) return false
        if (allyHonors > 0 && napHonors >= bidTarget) return false
        return true
    }
}
