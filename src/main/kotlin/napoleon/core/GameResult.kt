package napoleon.core

enum class BidOutcome { CONTINUE, ALL_PASSED, WON }

data class GameResult(
    val verdict: ScoreTable.Verdict,
    val solo: Boolean,
    val napoleonId: Int,
    val adjutantId: Int,
)
