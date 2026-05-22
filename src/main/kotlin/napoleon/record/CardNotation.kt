package napoleon.record

import napoleon.core.Card
import napoleon.core.Rank
import napoleon.core.Suit

// ログ上のカード記法の単一の真実源。GameRecordWriter (整形) と GameRecordReader (解析) が
// この対応表を双方向で共有する。役札は trump 文脈に依存するため、変換には trump を要する。
object CardNotation {
    private const val JOKER_GLYPH = "**"
    private const val MIGHTY_GLYPH = "@A"
    private const val RIGHT_BOWER_GLYPH = "+J"
    private const val LEFT_BOWER_GLYPH = "-J"
    private const val SLIP_GLYPH = "@Q"

    // card -> ログ表記。isLead/jokerDeclaredSuit はジョーカーリード時の請求スート表記 (例 "C*") に、
    // mightyInTrick はよろめきを "@Q" と書くかの判定に使う。
    fun format(
        card: Card,
        trump: Suit,
        isLead: Boolean = false,
        mightyInTrick: Boolean = false,
        jokerDeclaredSuit: Suit? = null,
    ): String =
        when {
            card.isJoker() -> {
                if (isLead && jokerDeclaredSuit != null) "${jokerDeclaredSuit.shortName}*" else JOKER_GLYPH
            }
            card.isMighty() -> MIGHTY_GLYPH
            card.isRightBower(trump) -> RIGHT_BOWER_GLYPH
            card.isLeftBower(trump) -> LEFT_BOWER_GLYPH
            card.isSlip() && mightyInTrick -> SLIP_GLYPH
            else -> card.toString()
        }

    // ログ表記 -> card。末尾 "*" 付き (リード請求スート表記) は通常のジョーカーへ畳む。
    fun parseCard(
        s: String,
        trump: Suit,
    ): Card =
        when (s) {
            JOKER_GLYPH -> Card.JOKER
            MIGHTY_GLYPH -> Card.MIGHTY
            RIGHT_BOWER_GLYPH -> Card.rightBower(trump)
            LEFT_BOWER_GLYPH -> Card.leftBower(trump)
            SLIP_GLYPH -> Card.SLIP
            else ->
                if (s.endsWith("*")) {
                    Card.JOKER
                } else {
                    Card.of(parseSuit(s[0]), parseRank(s.substring(1)))
                }
        }

    fun parseSuit(c: Char): Suit = Suit.realEntries.firstOrNull { it.shortName[0] == c } ?: error("Unknown suit: $c")

    private fun parseRank(s: String): Rank = Rank.entries.firstOrNull { it.shortName == s } ?: error("Unknown rank: $s")
}
