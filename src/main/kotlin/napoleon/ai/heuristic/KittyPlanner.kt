package napoleon.ai.heuristic

import napoleon.ai.heuristic.support.KittyDebugLogger
import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext

// keep score (大きいほど残したい) の小さい順に 3 枚を選んで捨てる。基本スコアに加え:
//   - voidBonus: 切り札が多くサイドスートに A も無いとき、短いサイドスートを丸ごと捨てて
//                ボイドを作る (後でラフを生む)。
//   - dumpPenalty: 1 トリック目支配が見込めるとき、サイド絵札 (K/Q/J/T) をキティに送り込み、
//                  1 トリック目勝者として表向き絵札を回収する道筋を作る。
//
// dumpPenalty はサイドスート枚数で減衰する: 短いスート (≤2) は回収成功率が高いので強く効かせる。
// しきい値は KittyProfilerRunner の実測 (sideCount ≤ 2 の dump 経由で約 92%、サイド絵札放置で約 60% 回収)
// に基づき校正している。
class KittyPlanner(
    private val context: AiContext,
) {
    private val logger = KittyDebugLogger(context)

    companion object {
        // sideCount ≤ 2: 短いスート、強く dump
        private const val DUMP_HARD = 5_000

        // sideCount == 3
        private const val DUMP_SOFT = 3_000

        // sideCount ≥ 4: 平場勝負での絵札回収機会の方が大きいので弱めに dump
        private const val DUMP_VERY_SOFT = 1_500
        private const val VOID = 2_000
    }

    fun chooseKittySwap(): IntArray {
        val me = context.curPlayer
        val trump = context.trump
        val count = me.handCount
        val adjutant = context.adjutantCard

        val sideCount = countSideSuits(trump, count)
        val soloConfirmed = adjutant != null && (0 until count).any { me.hand[it] == adjutant }
        val trumpCount = countTrumps(trump, count)

        val scores =
            IntArray(count) { i ->
                baseScore(me.hand[i], trump, sideCount)
            }

        val voided = applyVoidBonus(scores, trump, trumpCount, adjutant, soloConfirmed)
        val confident = firstTrickConfident(soloConfirmed)
        if (confident) applyDumpPenalty(scores, trump, adjutant, sideCount)

        val sorted = (0 until count).sortedBy { scores[it] }
        val discard = sorted.take(3).toIntArray()
        discard.sort()
        logger.log(discard, confident, voided)
        return discard
    }

    private fun countTrumps(
        trump: Suit,
        count: Int,
    ): Int {
        val me = context.curPlayer
        var n = 0
        for (i in 0 until count) {
            val c = me.hand[i]
            if (c.suit == trump || c.isMighty()) n++
        }
        return n
    }

    private fun countSideSuits(
        trump: Suit,
        count: Int,
    ): IntArray {
        val me = context.curPlayer
        val result = IntArray(Suit.realEntries.size)
        for (i in 0 until count) {
            val c = me.hand[i]
            if (c.suit == Suit.NONE || c.suit == trump) continue
            result[c.suit.ordinal]++
        }
        return result
    }

    private fun baseScore(
        c: Card,
        trump: Suit,
        sideCount: IntArray,
    ): Int =
        when {
            c.isJoker() -> 100_000
            c.isMighty() -> 100_000
            c.isRightBower(trump) -> 90_000
            c.isLeftBower(trump) -> 90_000
            c.suit == trump && (c.rank == Rank.RANK_A || c.rank == Rank.RANK_K) -> 70_000 + c.rank.ordinal
            c.suit == trump -> 10_000 + c.rank.ordinal
            c.rank == Rank.RANK_A -> 5_000 + sideCount[c.suit.ordinal]
            c.rank == Rank.RANK_2 -> seimScore(sideCount[c.suit.ordinal])
            c.rank.isHonor -> 1_000 + c.rank.ordinal
            else -> c.rank.ordinal
        }

    private fun seimScore(n: Int): Int =
        when {
            n <= 3 -> 1_700
            n == 4 -> 1_500
            else -> 1_300
        }

    // ラフを支えられる構成のときだけ、サイドスート全体をボイド化する候補にする。
    // 条件: 切り札 ≥ 3、当該スートに A を含まない (A の方がラフより価値が高い)、
    //       全枚数が捨ててよい (役札・副官カードを含まない)。
    // ソロ確定時のみサイズ上限を 3→4 に緩める (単独戦ではラフ 1 トリックの価値が大きいため)。
    private fun applyVoidBonus(
        scores: IntArray,
        trump: Suit,
        trumpCount: Int,
        adjutant: Card?,
        soloConfirmed: Boolean,
    ): List<Suit> {
        if (trumpCount < 3) return emptyList()
        val maxSize = if (soloConfirmed) 4 else 3
        val voided = mutableListOf<Suit>()
        val me = context.curPlayer
        val count = me.handCount
        for (suit in Suit.realEntries) {
            if (suit == trump) continue
            val inSuit = (0 until count).filter { me.hand[it].suit == suit }
            if (inSuit.isEmpty() || inSuit.size > maxSize) continue
            if (inSuit.any { me.hand[it].rank == Rank.RANK_A }) continue
            val voidable =
                inSuit.all {
                    val c = me.hand[it]
                    c != adjutant && !c.isPowerCard(trump)
                }
            if (voidable) {
                for (i in inSuit) scores[i] -= VOID
                voided += suit
            }
        }
        return voided
    }

    private fun applyDumpPenalty(
        scores: IntArray,
        trump: Suit,
        adjutant: Card?,
        sideCount: IntArray,
    ) {
        val me = context.curPlayer
        val count = me.handCount
        for (i in 0 until count) {
            val c = me.hand[i]
            if (c == adjutant) continue
            if (c.suit == Suit.NONE || c.suit == trump) continue
            if (!c.rank.isHonor || c.rank == Rank.RANK_A) continue
            val penalty =
                when {
                    sideCount[c.suit.ordinal] <= 2 -> DUMP_HARD
                    sideCount[c.suit.ordinal] == 3 -> DUMP_SOFT
                    else -> DUMP_VERY_SOFT
                }
            scores[i] -= penalty
        }
    }

    // 1 トリック目の制圧は構造的に保証される:
    //   副官あり: AdjutantPlanner の優先順序がチームでの JOKER/MIGHTY 保有を保証する。
    //   ソロ: 副官カードがキティに来たケース。優先順序が高い JOKER/MIGHTY は既に自分の手札にあるはず。
    //         error() はその不変条件が崩れていないかを検出する。
    private fun firstTrickConfident(soloConfirmed: Boolean): Boolean {
        if (!soloConfirmed) return true
        val me = context.curPlayer
        for (i in 0 until me.handCount) {
            val c = me.hand[i]
            if (c.isJoker() || c.isMighty()) return true
        }
        error("solo confirmed but neither JOKER nor MIGHTY in hand (broken adjutant invariant)")
    }
}
