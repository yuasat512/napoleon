package napoleon.ai.heuristic.support

import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit
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
        val adj = context.adjutantCard
        if (adj != null) {
            for (i in 0 until me.handCount) {
                if (me.hand[i] == adj) return Role.ADJUTANT
            }
            val revealed = context.adjutantIdIfRevealed
            if (revealed != null && revealed == me.id && revealed != context.napoleonId) {
                return Role.ADJUTANT
            }
        }
        return Role.ALLY
    }

    // 副官の候補プレイヤー集合。公開済みなら 1 名、自分が副官なら自分のみ。
    // ナポレオン視点では自分の手札やキティから副官カードの所在を引き当てられればソロ確定。
    // それ以外は仮説整合性チェックで絞り込み、候補が空なら無条件の候補集合にフォールバックする。
    fun adjutantCandidates(): List<Int> {
        val napId = context.napoleonId
        val revealedAdj = context.adjutantIdIfRevealed
        if (revealedAdj != null) return listOf(revealedAdj)
        val viewer = context.curPlayer
        val role = myRole()
        if (role == Role.ADJUTANT) return listOf(viewer.id)
        if (role == Role.NAPOLEON) {
            val adj = context.adjutantCard
            if (adj != null) {
                val adjInHand = (0 until viewer.handCount).any { viewer.hand[it] == adj }
                val adjInKitty = context.napoleonKittyDiscards?.contains(adj) == true
                if (adjInHand || adjInKitty) return listOf(napId)
            }
        }
        val filtered = mutableListOf<Int>()
        for (pid in 0 until PLAYER_COUNT) {
            if (pid == napId) continue
            if (role == Role.ALLY && pid == viewer.id) continue
            if (hypothesisConsistent(pid)) filtered += pid
        }
        if (filtered.isNotEmpty()) return filtered
        val fallback = mutableListOf<Int>()
        for (pid in 0 until PLAYER_COUNT) {
            if (pid == napId) continue
            if (role == Role.ALLY && pid == viewer.id) continue
            fallback += pid
        }
        return fallback
    }

    fun isLikelyTeammate(pid: Int): Boolean {
        val me = context.curPlayer.id
        if (pid == me) return true
        val napId = context.napoleonId
        val myRole = myRole()
        val inferred = inferRoleOf(pid)
        if (inferred != null) {
            return when (myRole) {
                Role.NAPOLEON -> inferred == Role.ADJUTANT
                Role.ADJUTANT -> inferred == Role.NAPOLEON
                Role.ALLY -> inferred == Role.ALLY
            }
        }
        val revealedAdj = context.adjutantIdIfRevealed
        return when (myRole) {
            Role.NAPOLEON -> revealedAdj != null && pid == revealedAdj && revealedAdj != napId
            Role.ADJUTANT -> pid == napId
            Role.ALLY -> pid != napId && pid != revealedAdj
        }
    }

    private fun inferRoleOf(pid: Int): Role? {
        val napId = context.napoleonId
        if (pid == napId) return Role.NAPOLEON

        val viewer = context.curPlayer
        val revealedAdj = context.adjutantIdIfRevealed
        if (revealedAdj != null) {
            return if (pid == revealedAdj) Role.ADJUTANT else Role.ALLY
        }

        if (viewer.id != napId) {
            val adj = context.adjutantCard
            if (adj != null && (0 until viewer.handCount).any { viewer.hand[it] == adj }) {
                return if (pid == viewer.id) Role.ADJUTANT else Role.ALLY
            }
        } else {
            val adj = context.adjutantCard
            if (adj != null) {
                val adjInHand = (0 until viewer.handCount).any { viewer.hand[it] == adj }
                val adjInKitty = context.napoleonKittyDiscards?.contains(adj) == true
                if (adjInHand || adjInKitty) return Role.ALLY
            }
        }

        if (pid == viewer.id) return myRole()

        var sawAdj = false
        var sawAlly = false
        for (h in 0 until PLAYER_COUNT) {
            if (!hypothesisConsistent(h)) continue
            if (h == pid) {
                sawAdj = true
            } else if (pid != napId) {
                sawAlly = true
            }
        }
        return when {
            sawAdj && !sawAlly -> Role.ADJUTANT
            sawAlly && !sawAdj -> Role.ALLY
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

        val adj = context.adjutantCard
        if (adj != null) {
            if (adj.isJoker()) {
                if (context.knownNoJoker[candAdjId]) return false
            } else if (adj.suit != Suit.NONE) {
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
        val bidTarget = context.bid?.target ?: return true
        if (napHonors == HONOR_COUNT) return false
        if (allyHonors > HONOR_COUNT - bidTarget) return false
        if (allyHonors > 0 && napHonors >= bidTarget) return false
        return true
    }
}
