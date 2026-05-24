package napoleon.core

enum class BidOutcome { CONTINUE, ALL_PASSED, WON }

data class GameResult(
    val verdict: ScoreTable.Verdict,
    val napoleonId: Int,
    val adjutantId: Int,
) {
    val solo: Boolean get() = napoleonId == adjutantId
}
