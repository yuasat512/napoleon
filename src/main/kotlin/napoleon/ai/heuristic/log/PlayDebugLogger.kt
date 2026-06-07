package napoleon.ai.heuristic.log

import napoleon.ai.heuristic.support.RoleInference
import napoleon.ai.heuristic.support.TrickEvaluator
import napoleon.core.Card
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit
import napoleon.engine.view.AiContext
import napoleon.engine.view.SelfPlayerView

class PlayDebugLogger(
    private val context: AiContext,
    private val roleInference: RoleInference,
    private val evaluator: TrickEvaluator,
) {
    // route = 固定の戦術ルート (集計キー)、detail = 可変の補助情報 (候補札など)。reason フィールドは
    // この 2 つを DETAIL_SEP で連結して出力し、PlayBranchScan は DETAIL_SEP の前 (= route.label) だけで
    // 集計する。detail が空ならルートラベルだけを出す。
    fun log(
        idx: Int,
        legal: List<Int>,
        route: PlayRoute,
        detail: String = "",
    ) {
        if (!context.debug) return
        val reason = if (detail.isEmpty()) route.label else "${route.label}$DETAIL_SEP$detail"
        val me = context.curPlayer
        val card = me.hand[idx]
        val trickNo = context.trickHistory.size + 1
        val lead = if (context.leadSuit == Suit.NONE) "-" else context.leadSuit.shortName
        val hand = me.hand.joinToString(",") { "$it" }
        val legalStr = fmtIdxList(legal)

        val flags =
            buildString {
                if (context.mightyInTrick) append("mighty,")
                if (context.sameCandidate) append("same,")
                if (context.jokerCallActive) append("jokerCall,")
                if (context.jokerPlayed) append("jokerOut,")
            }.trimEnd(',')

        val parts = mutableListOf<String>()
        parts += "t=$trickNo.${context.trickTurn}"
        parts += "role=${roleInference.myRole()}"
        parts += "trump=${context.trump.shortName}"
        parts += "lead=$lead"
        if (flags.isNotEmpty()) parts += "flags=[$flags]"
        parts += "nap=P${context.napoleonId}"
        parts += "adj=${formatAdjutant()}"
        formatCurrentTrick(me)?.let { parts += it }
        parts += "honors=${evaluator.countTrickHonors()}"
        parts += "after=${PLAYER_COUNT - context.trickTurn - 1}"
        formatPublicHonors()?.let { parts += it }
        formatKitty()?.let { parts += it }
        formatKnownVoids(me)?.let { parts += it }
        formatKnownNoJoker(me)?.let { parts += it }
        parts += "hand=[$hand]"
        parts += "legal=[$legalStr]"
        parts += "pick=$idx:$card"
        parts += "reason=$reason"

        println("[HeuristicAI P${me.id}] ${parts.joinToString(" ")}")
    }

    fun fmtIdxList(indices: List<Int>): String {
        val me = context.curPlayer
        return indices.joinToString(",") { "$it:${me.hand[it]}" }
    }

    private fun formatAdjutant(): String {
        val adj = context.adjutantCard
        return if (context.adjutantRevealed) "$adj*" else "$adj"
    }

    private fun formatCurrentTrick(me: SelfPlayerView): String? {
        if (context.trickTurn == 0) return null
        val leaderId = evaluator.determineCurrentLeader().id
        val played =
            context.publicPlayers
                .filter { it.handCount < me.hand.size }
                .joinToString(",") { p ->
                    if (p.id == leaderId) {
                        val s = context.strengthOf(p.playedCard)
                        val tag = if (roleInference.isLikelyTeammate(p.id)) "A" else "E"
                        "P${p.id}:${p.playedCard}(L,$s,$tag)"
                    } else {
                        "P${p.id}:${p.playedCard}"
                    }
                }
        return "trick=[$played]"
    }

    private fun formatPublicHonors(): String? {
        val cards = mutableListOf<Card>()
        for (rec in context.trickHistory) {
            for (play in rec.plays) if (play.card.rank.isHonor) cards += play.card
        }
        if (context.trickHistory.isNotEmpty()) cards += context.kittyHonorCards
        if (cards.isEmpty()) return null
        cards.sortBy { it.honorIndex }
        return "pubHonors=[${cards.joinToString(",")}]"
    }

    private fun formatKitty(): String? {
        if (context.trickHistory.isNotEmpty()) return null
        val cards = context.kittyHonorCards
        if (cards.isEmpty()) return null
        return "kitty=[${cards.joinToString(",")}]"
    }

    private fun formatKnownVoids(me: SelfPlayerView): String? {
        val parts = mutableListOf<String>()
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            val voids = context.knownVoids[p.id]
            if (voids.isNotEmpty()) {
                parts += "P${p.id}:${voids.joinToString("") { it.shortName }}"
            }
        }
        if (parts.isEmpty()) return null
        return "voids=[${parts.joinToString(",")}]"
    }

    private fun formatKnownNoJoker(me: SelfPlayerView): String? {
        val ids = (0 until PLAYER_COUNT).filter { it != me.id && context.knownNoJoker[it] }
        if (ids.isEmpty()) return null
        return "noJoker=[${ids.joinToString(",") { "P$it" }}]"
    }

    companion object {
        // reason フィールド内で固定ルート (route.label) と可変 detail を区切るトークン。
        // PlayBranchScan はこのトークンの前だけを集計キーにする。ルートラベル・detail には含めない。
        const val DETAIL_SEP = " | "
    }
}
