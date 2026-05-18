package napoleon.core

data class Bid(
    val suit: Suit,
    val target: Int,
) : Comparable<Bid> {
    init {
        // strength の shl 2 が 4 値 (0..3) を前提にしているため NONE は弾く。
        require(suit != Suit.NONE) { "bid suit must not be NONE" }
    }

    // 比較用パック値。target が優先され、同 target ならスート順位で決まる。
    val strength: Int = suit.ordinal + (target shl 2)

    override fun compareTo(other: Bid): Int = strength.compareTo(other.strength)

    companion object {
        const val MIN_TARGET = 12
        const val MAX_TARGET = GameRules.HONOR_COUNT
    }
}
