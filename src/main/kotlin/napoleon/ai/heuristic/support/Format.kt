package napoleon.ai.heuristic.support

import napoleon.core.Card

fun fmtCard(card: Card): String =
    when {
        card.isJoker() -> "**"
        else -> "${card.suit.shortName}${card.rank.shortName}"
    }
