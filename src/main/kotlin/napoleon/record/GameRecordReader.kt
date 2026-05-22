package napoleon.record

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.GameRules.HAND_SIZE
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.PLAYER_NAMES
import java.nio.file.Path
import kotlin.io.path.readText

data class ReplayEntry(
    val deck: Array<Card>,
    val record: GameRecord,
    val label: String,
)

object GameRecordReader {
    fun parse(path: Path): List<ReplayEntry> {
        val text = path.readText().replace("\r", "")
        val labels =
            Regex("^=== (.*)", RegexOption.MULTILINE)
                .findAll(text)
                .map { it.groupValues[1].trim() }
                .toList()
        return text
            .split(Regex("^===.*\n", RegexOption.MULTILINE))
            .drop(1)
            .mapIndexed { idx, block ->
                val label = labels.getOrElse(idx) { "" }
                try {
                    parseGame(idx + 1, block, label)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to parse game ${idx + 1}: ${e.message}", e)
                }
            }
    }

    private fun parseGame(
        gameIndex: Int,
        block: String,
        label: String = "",
    ): ReplayEntry {
        val sections = block.split("\n---\n")
        val firstLine = sections[0].lines().firstOrNull { it.isNotEmpty() } ?: ""
        val hasBid = !firstLine.matches(Regex("[SCDH]\\d+.*"))
        val declIdx = if (hasBid) 1 else 0
        val tricksIdx = if (hasBid) 2 else 1
        val remainingIdx = if (hasBid) 3 else 2

        val bidFirstPlayerId: Int
        val bidSequence: List<Bid?>

        if (hasBid) {
            val bidLines = sections[0].lines()
            val bidColWidth = 4
            val allCells = mutableListOf<String>()
            for (lineIdx in 1 until bidLines.size) {
                val line = bidLines[lineIdx]
                for (j in 0 until PLAYER_COUNT) {
                    val start = j * bidColWidth
                    val chunk =
                        if (start < line.length) {
                            line.substring(start, minOf(start + bidColWidth, line.length)).trim()
                        } else {
                            ""
                        }
                    allCells.add(chunk)
                }
            }
            val firstNonEmpty = allCells.indexOfFirst { it.isNotEmpty() }
            bidFirstPlayerId = if (firstNonEmpty < 0) 0 else firstNonEmpty % PLAYER_COUNT
            bidSequence =
                allCells
                    .drop(maxOf(0, firstNonEmpty))
                    .filter { it.isNotEmpty() }
                    .map { if (it == "-") null else Bid(CardNotation.parseSuit(it[0]), it.substring(1).toInt()) }
        } else {
            bidFirstPlayerId = 0
            bidSequence = emptyList()
        }

        val declLines = sections[declIdx].lines()
        val declParts = declLines[0].trim().split(" ")
        val bidSuit = CardNotation.parseSuit(declParts[0][0])
        val bidTarget = declParts[0].substring(1).toInt()
        val bid = Bid(bidSuit, bidTarget)
        val adjutantCard = CardNotation.parseCard(declParts[1], bidSuit)

        val kittyLine = declLines[1]
        val arrowIdx = kittyLine.indexOf("->")
        val drawnCards =
            kittyLine
                .substring(0, arrowIdx)
                .trim()
                .split(" ")
                .map { CardNotation.parseCard(it, bidSuit) }
                .toTypedArray()
        val discardedCards =
            kittyLine
                .substring(arrowIdx + 2)
                .trim()
                .split(" ")
                .map { CardNotation.parseCard(it, bidSuit) }
                .toTypedArray()

        val trickLines = sections[tricksIdx].lines()
        val rolesPart = trickLines[0].substringBefore("|")
        var napoleonId = 0
        var adjutantId = -1
        for (token in rolesPart.trim().split("\\s+".toRegex())) {
            val colon = token.indexOf(':')
            if (colon < 0) continue
            val pid = token[0] - 'a'
            when (token.substring(colon + 1)) {
                "NP" -> napoleonId = pid
                "AD" -> adjutantId = pid
            }
        }
        if (adjutantId < 0) adjutantId = napoleonId

        val colWidth = 6
        val tricks = mutableListOf<GameRecord.TrickRecord>()
        for (lineIdx in 1 until trickLines.size) {
            val line = trickLines[lineIdx]
            if (line.isBlank()) continue
            val leftPart = if (line.contains('|')) line.substringBefore('|') else line
            val trick = GameRecord.TrickRecord()
            var mightyInTrick = false

            for (j in 0 until PLAYER_COUNT) {
                val start = j * colWidth
                if (start >= leftPart.length) break
                val rawCell = leftPart.substring(start, minOf(start + colWidth, leftPart.length))
                val isLead = rawCell.contains('[')
                val cardStr = rawCell.replace("[", "").replace("]", "").trim()

                // 最終トリックでは請求スートは意味を持たないため無視する。
                val jokerDeclaredSuit =
                    if (isLead &&
                        cardStr.length == 2 &&
                        cardStr[1] == '*' &&
                        cardStr[0] != '*' &&
                        tricks.size < HAND_SIZE - 1
                    ) {
                        CardNotation.parseSuit(cardStr[0])
                    } else {
                        null
                    }

                val card = CardNotation.parseCard(cardStr, bidSuit)
                trick.cards[j] = card
                if (isLead) {
                    trick.leadId = j
                    trick.jokerDeclaredSuit = jokerDeclaredSuit
                }
                if (card.isMighty()) mightyInTrick = true
            }

            trick.mightyInTrick = mightyInTrick
            tricks.add(trick)
        }

        val remaining = Array(PLAYER_COUNT) { mutableListOf<Card>() }
        val remainingSection = sections.getOrNull(remainingIdx)
        if (remainingSection != null && !isScoreDeltaSection(remainingSection)) {
            for (line in remainingSection.lines()) {
                if (line.isBlank()) continue
                for (j in 0 until PLAYER_COUNT) {
                    val start = j * colWidth
                    if (start >= line.length) break
                    val cardStr = line.substring(start, minOf(start + colWidth, line.length)).trim()
                    if (cardStr.isNotEmpty()) remaining[j].add(CardNotation.parseCard(cardStr, bidSuit))
                }
            }
        }

        // 配り直しを再現するためログから初期手札を復元する。非ナポレオンは played+remaining で済むが、
        // ナポレオンは途中でキティを 3 枚受け取って 3 枚戻しているため、戻した分を足し、引いた分を除く。
        val deck = Array(PLAYER_COUNT * HAND_SIZE + KITTY_SIZE) { Card.JOKER }
        for (pid in 0 until PLAYER_COUNT) {
            val played = tricks.map { it.cards[pid]!! }
            val hand =
                if (pid == napoleonId) {
                    (played + remaining[pid] + discardedCards.toList() - drawnCards.toList().toSet()).toTypedArray()
                } else {
                    (played + remaining[pid]).toTypedArray()
                }
            hand.copyInto(deck, pid * HAND_SIZE)
        }
        drawnCards.copyInto(deck, PLAYER_COUNT * HAND_SIZE)

        val record =
            GameRecord().apply {
                this.bidFirstPlayerId = bidFirstPlayerId
                this.bidSequence.addAll(bidSequence)
                this.bid = bid
                this.adjutantCard = adjutantCard
                this.napoleonId = napoleonId
                this.adjutantId = adjutantId
                this.drawnCards = drawnCards
                this.discardedCards = discardedCards
                this.tricks.addAll(tricks)
            }

        val entryLabel =
            if (label.isNotEmpty()) {
                "$label  ${PLAYER_NAMES[napoleonId]}: ${bidSuit.displayName} $bidTarget"
            } else {
                "#$gameIndex  ${PLAYER_NAMES[napoleonId]}: ${bidSuit.displayName} $bidTarget"
            }
        return ReplayEntry(deck, record, entryLabel)
    }

    private fun isScoreDeltaSection(section: String): Boolean {
        val firstLine = section.lines().firstOrNull { it.isNotBlank() } ?: return false
        val firstToken = firstLine.trim().split(Regex("\\s+")).firstOrNull() ?: return false
        return firstToken.matches(Regex("[+-]\\d+"))
    }
}
