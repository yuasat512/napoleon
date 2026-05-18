package napoleon.record

import napoleon.core.Card
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.Suit
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class GameRecordWriter {
    fun format(record: GameRecord): String =
        buildString {
            val colWidth = 6
            val trump = record.bid.suit

            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            appendLine("=== $ts")
            val bidColWidth = 4
            appendLine(
                (0 until PLAYER_COUNT)
                    .joinToString("") { pid -> " ${PLAYER_NAMES[pid]}  " }
                    .trimEnd(),
            )
            val bidCells =
                buildList {
                    repeat(record.bidFirstPlayerId) { add(null) }
                    for (b in record.bidSequence) add(if (b == null) " -" else "${suitStr(b.suit)}${b.target}")
                }
            var rowStart = 0
            while (rowStart < bidCells.size) {
                val row =
                    (rowStart until minOf(rowStart + PLAYER_COUNT, bidCells.size))
                        .joinToString("") { i ->
                            (bidCells[i] ?: "").padEnd(bidColWidth)
                        }
                appendLine(row.trimEnd())
                rowStart += PLAYER_COUNT
            }
            appendLine("---")
            appendLine("${suitStr(trump)}${record.bid.target} ${cardStr(record.adjutantCard, trump)}")
            appendLine(
                "${record.drawnCards.joinToString(" ") { cardStr(it, trump) }} -> ${
                    record.discardedCards.joinToString(" ") { cardStr(it, trump) }
                }",
            )
            appendLine("---")

            val rolesStr =
                (0 until PLAYER_COUNT)
                    .joinToString("") { i ->
                        val role =
                            when (i) {
                                record.napoleonId -> "NP"
                                record.adjutantId -> "AD"
                                else -> "AL"
                            }
                        "${PLAYER_NAMES[i]}:$role".padEnd(colWidth)
                    }.dropLast(1)
            appendLine("$rolesStr| $rolesStr")

            val cumulativeHonors = IntArray(PLAYER_COUNT)
            for (trick in record.tricks) {
                val left =
                    (0 until PLAYER_COUNT)
                        .joinToString("") { pid ->
                            val card = trick.cards[pid]!!
                            val isLead = pid == trick.leadId
                            val cs = cardStr(card, trump, isLead, trick.mightyInTrick, trick.jokerDeclaredSuit)
                            val cell = if (isLead) "[$cs]" else " $cs "
                            cell.padEnd(colWidth)
                        }.dropLast(1)

                cumulativeHonors[trick.winnerId] += trick.honorsGained

                val right =
                    (0 until PLAYER_COUNT)
                        .joinToString("") { pid ->
                            val num = "%2d".format(cumulativeHonors[pid])
                            val cell = if (pid == trick.winnerId) "$num(${trick.honorsGained})" else num
                            cell.padEnd(colWidth)
                        }.trimEnd()

                appendLine("$left| $right")
            }

            if (record.remainingCount > 0) {
                appendLine("---")
                for (r in 0 until record.remainingCount) {
                    val row =
                        (0 until PLAYER_COUNT).joinToString("") { pid ->
                            val hands = record.remainingHands[pid]
                            val cell = if (r < hands.size) " ${cardStr(hands[r], trump)} " else ""
                            cell.padEnd(colWidth)
                        }
                    appendLine(row.trimEnd())
                }
            }

            appendLine("---")
            val deltas = IntArray(PLAYER_COUNT) { record.pointsAfter[it] - record.pointsBefore[it] }
            val deltaWidth = 5
            append(
                (0 until PLAYER_COUNT)
                    .joinToString(" ") { i -> " %+d".format(deltas[i]).padEnd(deltaWidth) }
                    .trimEnd(),
            )
        }

    fun appendToFile(
        path: Path,
        record: GameRecord,
    ) {
        val text = format(record)
        path.parent?.createDirectories()
        path.writeText(text + "\n", options = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND))
    }

    private fun cardStr(
        card: Card,
        trump: Suit,
        isLead: Boolean = false,
        mightyInTrick: Boolean = false,
        jokerDeclaredSuit: Suit? = null,
    ): String =
        when {
            card.isJoker() -> {
                if (isLead && jokerDeclaredSuit != null) "${suitStr(jokerDeclaredSuit)}*" else "**"
            }
            card.isMighty() -> "@A"
            card.isRightBower(trump) -> "+J"
            card.isLeftBower(trump) -> "-J"
            card.isSlip() && mightyInTrick -> "@Q"
            else -> "${card.suit.shortName}${card.rank.shortName}"
        }

    private fun suitStr(suit: Suit): String = suit.shortName
}
