package napoleon.session

import napoleon.config.Config
import napoleon.config.RecordStore
import napoleon.core.GameResult
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.ScoreTable
import napoleon.engine.Player

class ScoreKeeper(
    private val players: List<Player>,
    private val config: Config,
    private val recordStore: RecordStore?,
) {
    val sessionRecords = Array(PLAYER_COUNT) { IntArray(ScoreTable.ENTRY_COUNT) }

    fun resetSession() {
        for (rec in sessionRecords) rec.fill(0)
    }

    fun applyScores(result: GameResult) {
        addScore(result, result.napoleonId, ScoreTable.Role.NAPOLEON)
        if (!result.solo) addScore(result, result.adjutantId, ScoreTable.Role.ADJUTANT)
        for (i in 0 until PLAYER_COUNT) {
            if (i != result.napoleonId && i != result.adjutantId) {
                addScore(result, i, ScoreTable.Role.ALLIES)
            }
        }
    }

    private fun addScore(
        result: GameResult,
        pid: Int,
        role: ScoreTable.Role,
    ) {
        val entryIdx = ScoreTable.entryIndex(result.verdict, result.solo, role)
        players[pid].points += ScoreTable[entryIdx]
        sessionRecords[pid][entryIdx]++
        // auto モードではユーザ戦績ファイルを汚さないよう永続記録への加算を抑制する。
        if (!config.auto && recordStore != null) recordStore.records[pid][entryIdx]++
    }
}
