package napoleon.ai.heuristic

import napoleon.core.Card
import napoleon.core.Rank
import napoleon.engine.view.AiContext

// 自分が持っていない最強カードを副官指定する。JOKER と MIGHTY を最上位に置くことで、
// チーム (ナポレオン軍) が必ずどちらか一方を保有する不変条件を作る。KittyPlanner と
// PlayPlanner の 1 トリック目ロジックはこの不変条件を前提にしている。
class AdjutantPlanner(
    private val context: AiContext,
) {
    fun chooseAdjutant(): Card {
        val trump = context.trump
        val me = context.curPlayer
        val have = HashSet<Card>()
        for (i in 0 until me.handCount) have += me.hand[i]
        val preferences =
            listOf(
                Card.JOKER,
                Card.MIGHTY,
                Card.rightBower(trump),
                Card.leftBower(trump),
                Card.of(trump, Rank.RANK_A),
                Card.of(trump, Rank.RANK_K),
                Card.of(trump, Rank.RANK_Q),
                Card.of(trump, Rank.RANK_T),
                Card.of(trump, Rank.RANK_9),
                Card.of(trump, Rank.RANK_8),
                Card.of(trump, Rank.RANK_7),
                Card.of(trump, Rank.RANK_6),
                Card.of(trump, Rank.RANK_5),
                Card.of(trump, Rank.RANK_4),
                Card.of(trump, Rank.RANK_3),
                Card.of(trump, Rank.RANK_2),
            )
        val pick =
            preferences.firstOrNull { it !in have }
                ?: error("All preference cards are in own hand — physically impossible")
        return pick
    }
}
