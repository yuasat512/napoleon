package napoleon.core

object CardStrength {
    // 役札の優先順位 (大きいほど強い)。よろめきはマイティと同一トリック時のみマイティを上回る。
    private const val SLIP_OVER_MIGHTY = 100
    private const val MIGHTY = 99
    private const val JOKER = 98
    private const val RIGHT_BOWER = 97
    private const val LEFT_BOWER = 96

    // 切り札ボーナス。任意の切り札が任意の非切り札数札に勝つよう、ランク値に上乗せする。
    private const val TRUMP_BOOST = 20

    // 「同一スートの2」が最強として振る舞うときの仮想ランク値。
    private const val SAME_RANK = 13

    // リードにも切り札にも追従していないカードはトリックを取れない。
    private const val NON_CONTRIBUTING = -1

    fun evaluate(
        card: Card,
        trump: Suit,
        leadSuit: Suit?,
        mightyInTrick: Boolean,
        sameActive: Boolean,
    ): Int {
        if (card.isSlip() && mightyInTrick) return SLIP_OVER_MIGHTY
        if (card.isMighty()) return MIGHTY
        if (card.isJoker()) return JOKER
        if (card.isRightBower(trump)) return RIGHT_BOWER
        if (card.isLeftBower(trump)) return LEFT_BOWER

        val rank = if (card.isRankTwo() && sameActive) SAME_RANK else card.rank.ordinal
        return when (card.suit) {
            leadSuit -> rank
            trump -> rank + TRUMP_BOOST
            else -> NON_CONTRIBUTING
        }
    }
}
