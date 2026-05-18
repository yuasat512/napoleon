package napoleon.core

object ScoreTable {
    enum class Verdict { PERFECT_WIN, WIN, LOSS }

    enum class Role { NAPOLEON, ADJUTANT, ALLIES }

    // 結果 × 役職の得点増減表。各行は (NAPOLEON, ADJUTANT, ALLIES) の3要素。
    // 0..2行目は副官あり、3..5行目は副官なし (ソロ)。視認性のため ktlint を抑止する。
    @Suppress("ktlint")
    private val scores: IntArray = intArrayOf(
        12, 12, -8, // PERFECT_WIN
         6,  6, -4, // WIN
        -6, -6,  4, // LOSS
        24,  0, -6, // PERFECT_WIN (solo)
        12,  0, -3, // WIN (solo)
        -8,  0,  2, // LOSS (solo)
    )

    val ENTRY_COUNT = scores.size
    private val ROLE_COUNT = Role.entries.size
    private val GROUP_SIZE = Verdict.entries.size * ROLE_COUNT

    init {
        require(ENTRY_COUNT == GROUP_SIZE * 2)
    }

    fun entryIndex(
        verdict: Verdict,
        solo: Boolean,
        role: Role,
    ): Int = verdict.ordinal * ROLE_COUNT + (if (solo) GROUP_SIZE else 0) + role.ordinal

    operator fun get(i: Int): Int = scores[i]
}
