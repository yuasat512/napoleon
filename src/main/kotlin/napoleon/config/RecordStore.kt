package napoleon.config

import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.core.ScoreTable

class RecordStore {
    val records = Array(PLAYER_COUNT) { IntArray(ScoreTable.ENTRY_COUNT) }

    fun load(): RecordStore =
        apply {
            val props = loadProperties()
            records.forEachIndexed { pid, row ->
                for (idx in row.indices) {
                    row[idx] = props.getInt("player$pid.record$idx", 0)
                }
            }
        }

    fun save() {
        storeProperties {
            records.forEachIndexed { pid, row ->
                for (idx in row.indices) {
                    setProperty("player$pid.record$idx", "${row[idx]}")
                }
            }
        }
    }
}
