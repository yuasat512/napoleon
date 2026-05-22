package napoleon.engine.view

import napoleon.core.Card
import napoleon.core.Suit

data class ResolvedTrick(
    val plays: List<TrickPlay>,
    val jokerDeclaredSuit: Suit?,
    val winnerId: Int,
) {
    // ジョーカーリード後のスート宣言を反映した実効的な台札スート。
    // 最終トリックのジョーカーリードでは宣言不要なので null。
    val effectiveLeadSuit: Suit?
        get() =
            when {
                jokerDeclaredSuit != null -> jokerDeclaredSuit
                plays[0].card.isJoker() -> null
                else -> plays[0].card.suit
            }
}

data class TrickPlay(
    val playerId: Int,
    val card: Card,
)
